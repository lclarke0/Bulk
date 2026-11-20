package com.bulkplan.tracker.logic

import com.bulkplan.tracker.data.Block
import com.bulkplan.tracker.data.DefaultPlan
import com.bulkplan.tracker.data.Plan
import com.bulkplan.tracker.data.Repo
import com.bulkplan.tracker.data.Workout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs

/** Reads the plan file an AI writes from the intake document, checks it and tidies it up. */
object PlanImport {
    data class Result(val plan: Plan, val summary: String, val warnings: List<String>)

    class BadPlan(msg: String) : Exception(msg)

    private val KINDS = setOf("meal", "train", "supp", "skin", "task", "coach", "sleep")
    private val KIND_ALIASES = mapOf(
        "supplement" to "supp", "supplements" to "supp", "caffeine" to "supp", "workout" to "train", "training" to "train",
        "gym" to "train", "skincare" to "skin", "shower" to "skin", "snack" to "meal", "food" to "meal", "bed" to "sleep",
        "bedtime" to "sleep", "checkin" to "coach", "check-in" to "coach", "review" to "coach",
    )

    /** Finds the JSON in an AI reply: a ```bulkplan block, then a ```json block, then the outermost braces. */
    fun extractJson(text: String): String {
        val fenced = Regex("```\\s*(?:${Intake.FENCE}|json|JSON)?\\s*\\n([\\s\\S]*?)```").findAll(text).map { it.groupValues[1] }.toList()
        val tagged = Regex("```\\s*${Intake.FENCE}\\s*\\n([\\s\\S]*?)(```|$)").find(text)?.groupValues?.get(1)
        val pick = tagged ?: fenced.filter { it.contains("\"workouts\"") || it.contains("\"schedule\"") }.maxByOrNull { it.length }
        val raw = pick ?: text
        val i = raw.indexOf('{'); val j = raw.lastIndexOf('}')
        if (i < 0 || j <= i) throw BadPlan(
            if (i >= 0 && tagged != null) "The plan looks cut off. Ask the AI to “resend the whole code block”, then paste that."
            else "Couldn’t find a plan in that text. Paste the AI’s whole reply, including the code block."
        )
        return raw.substring(i, j + 1)
    }

    /** Light clean-up of common AI slips: // comments and trailing commas. */
    private fun relax(s: String): String {
        val noComments = s.lines().joinToString("\n") { line ->
            // Drop // comments that aren't inside a string (good enough for plan files).
            var inStr = false; var esc = false; var cut = -1
            for (k in line.indices) {
                val ch = line[k]
                if (esc) { esc = false; continue }
                if (ch == '\\') { esc = true; continue }
                if (ch == '"') inStr = !inStr
                if (!inStr && ch == '/' && k + 1 < line.length && line[k + 1] == '/') { cut = k; break }
            }
            if (cut >= 0) line.substring(0, cut) else line
        }
        return noComments.replace(Regex(",\\s*([}\\]])"), "$1")
    }

    fun parse(text: String, today: String = PlanLogic.today()): Result {
        val raw = extractJson(text)
        val root = runCatching { Repo.json.parseToJsonElement(raw) }
            .recoverCatching { Repo.json.parseToJsonElement(relax(raw)) }
            .getOrElse {
                val cut = !text.trimEnd().endsWith("```") && text.contains("```${Intake.FENCE}")
                throw BadPlan(if (cut) "The plan looks cut off. Ask the AI to “resend the whole code block”, then paste that." else "The plan isn’t valid JSON (${it.message?.take(120)}). Ask the AI to resend the code block as plain JSON with no comments.")
            } as? JsonObject ?: throw BadPlan("The plan should be a JSON object.")
        val planEl: JsonElement = root["plan"] ?: root
        val summary = (root["summary"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val obj = planEl as? JsonObject ?: throw BadPlan("The “plan” part should be a JSON object.")
        val withDate = if (obj["startDate"] == null) JsonObject(obj + ("startDate" to JsonPrimitive(""))) else obj
        val decoded = try { Repo.json.decodeFromJsonElement<Plan>(withDate) }
        catch (e: Exception) { throw BadPlan("Some fields are the wrong type: ${e.message?.lines()?.firstOrNull()?.take(160)}") }
        val (plan, warnings) = normalize(decoded, today)
        return Result(plan, summary, warnings)
    }

    private fun slug(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "item" }

    private fun uniq(ids: MutableSet<String>, want: String): String {
        var id = slug(want); var n = 2
        while (!ids.add(id)) id = slug(want) + "-" + n++
        return id
    }

    fun normTime(t: String): String? {
        val m = Regex("^\\s*(\\d{1,2})(?::(\\d{2}))?\\s*([aApP][mM])?\\s*$").find(t) ?: return null
        var h = m.groupValues[1].toInt(); val mm = m.groupValues[2].ifBlank { "0" }.toInt()
        val ap = m.groupValues[3].lowercase()
        if (ap == "pm" && h < 12) h += 12
        if (ap == "am" && h == 12) h = 0
        if (h !in 0..23 || mm !in 0..59) return null
        return "%02d:%02d".format(h, mm)
    }

    fun normalize(p0: Plan, today: String): Pair<Plan, List<String>> {
        val w = mutableListOf<String>()
        // Start date: a valid date, not in the past; Monday preferred.
        val start = runCatching { LocalDate.parse(p0.startDate.trim()) }.getOrNull()
        val t = LocalDate.parse(today)
        val startDate = when {
            start == null -> { w += "No start date, so it starts next Monday. Change it in Settings."; nextMonday(t) }
            start.isBefore(t.minusDays(7)) -> { w += "The start date was in the past, so it now starts next Monday."; nextMonday(t) }
            else -> start
        }
        val weeks = p0.weeks.coerceIn(1, 104).also { if (it != p0.weeks) w += "Plan length set to $it weeks." }

        // Workouts: valid days, unique exercise ids, sensible sets.
        val exIds = mutableSetOf<String>()
        val usedDays = mutableSetOf<Int>()
        val workouts = LinkedHashMap<String, Workout>()
        val wkIds = mutableSetOf<String>()
        p0.workouts.forEach { (k, wk) ->
            var day = wk.day
            if (day !in 0..6) { w += "${wk.name} had an invalid day; check Edit schedule › Workout days."; day = (0..6).first { it !in usedDays } }
            if (!usedDays.add(day)) w += "Two workouts were on ${PlanLogic.DAYN[day]}; only the first shows. Fix it in Edit schedule › Workout days."
            val exs = wk.exercises.map { e ->
                val m = e.muscle.trim().lowercase()
                e.copy(id = uniq(exIds, e.id.ifBlank { e.name }), name = e.name.ifBlank { "Exercise" }, sets = e.sets.coerceIn(1, 10),
                    reps = e.reps.ifBlank { "8–12" }, muscle = m)
            }
            if (exs.isEmpty()) w += "${wk.name} has no exercises."
            workouts[uniq(wkIds, k.ifBlank { wk.name })] = wk.copy(name = wk.name.ifBlank { k }, day = day, exercises = exs)
        }

        // Schedule: valid times, known kinds, valid days, unique ids.
        val sIds = mutableSetOf<String>()
        var dropped = 0
        val schedule = p0.schedule.mapNotNull { i ->
            val time = normTime(i.time) ?: run { dropped++; return@mapNotNull null }
            val kindRaw = i.kind.trim().lowercase()
            val kind = if (kindRaw in KINDS) kindRaw else KIND_ALIASES[kindRaw] ?: "task"
            val days = i.days.filter { it in 0..6 }.distinct().sorted().ifEmpty { (0..6).toList() }
            i.copy(id = uniq(sIds, i.id.ifBlank { i.title }), time = time, kind = kind, title = i.title.ifBlank { kind.replaceFirstChar { it.uppercase() } },
                days = days, dur = if (kind == "train") (i.dur ?: 75).coerceIn(10, 300) else i.dur,
                kcal = if (kind == "meal") i.kcal else null, protein = if (kind == "meal") i.protein else null)
        }
        if (dropped > 0) w += "$dropped schedule item${if (dropped == 1) "" else "s"} had an unreadable time and were left out."
        if (workouts.isEmpty() && schedule.isEmpty()) throw BadPlan("The plan has no workouts and no schedule. Ask the AI to send the full plan file.")

        // Training days without a training time.
        val trainDays = schedule.filter { it.kind == "train" }.flatMap { it.days }.toSet()
        val missingTrain = workouts.values.map { it.day }.filter { it !in trainDays }
        if (missingTrain.isNotEmpty()) w += "No training time on ${missingTrain.joinToString(", ") { PlanLogic.DAYN[it] }}. Add one in Edit schedule."

        // Blocks cover the plan.
        var blocks = p0.blocks.filter { it.to >= it.from }.sortedBy { it.from }
            .map { it.copy(mode = it.mode.takeIf { m -> m in setOf("two", "c3", "full", "half", "spec") } ?: "full") }
        if (blocks.isEmpty()) blocks = listOf(Block(1, weeks, "Training", "full", "0–2", "Sets as written. Add weight or reps each week."))
        val covered = (1..weeks).all { wk -> blocks.any { wk in it.from..it.to } }
        if (!covered) w += "The training phases don’t cover every week; uncovered weeks use the sets as written."

        // Calories vs meals.
        val targets = p0.targets.copy(calories = p0.targets.calories.coerceIn(1000, 8000))
        if (targets.calories != p0.targets.calories) w += "Calories were out of range and were set to ${targets.calories}."
        (0..6).map { d -> schedule.filter { it.kind == "meal" && d in it.days }.sumOf { it.kcal ?: 0 } }
            .filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()?.let { avg ->
                if (abs(avg - targets.calories) > targets.calories * 0.12)
                    w += "Meals add up to about ${avg.toInt()} kcal a day but the target is ${targets.calories}. The coach can fix this later."
            }

        val muscles = DefaultPlan.MUSCLES.toSet()
        val plan = p0.copy(
            startDate = startDate.toString(), weeks = weeks, targets = targets, blocks = blocks,
            workouts = workouts, schedule = schedule,
            weakMuscles = p0.weakMuscles.map { it.trim().lowercase() }.filter { it in muscles },
            prep = p0.prep.mapIndexed { n, it -> it.copy(id = it.id.ifBlank { "prep$n" }, tag = PlanLogic.testTag(it.tag, it.id)) },
            milestones = p0.milestones.filter { it.week in 1..weeks },
        )
        return plan to w
    }

    private fun nextMonday(t: LocalDate): LocalDate {
        var d = t.plusDays(1)
        while (d.dayOfWeek != DayOfWeek.MONDAY) d = d.plusDays(1)
        return d
    }

    /** Short description of a plan for the preview card. */
    fun overview(p: Plan): List<String> = listOf(
        "${p.weeks} weeks from ${PlanLogic.fmtD(p.startDate)}",
        "${p.targets.calories} kcal a day · protein ${PlanLogic.fmtN(p.targets.proteinPerLb)} g per lb",
        "${PlanLogic.fmtN(p.profile.startWeight)} → ${PlanLogic.fmtN(p.profile.sixMonthWeight)} lb by the end (goal ${PlanLogic.fmtN(p.profile.goalWeight)} lb)",
        p.workouts.values.sortedBy { (it.day + 6) % 7 }.joinToString(" · ") { "${PlanLogic.DAYS[it.day]} ${it.name}" }.ifBlank { "No workouts" },
        "${p.schedule.size} things on the weekly schedule, ${p.blocks.size} training phase${if (p.blocks.size == 1) "" else "s"}",
    )
}
