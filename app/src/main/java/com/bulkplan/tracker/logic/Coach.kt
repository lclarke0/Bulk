package com.bulkplan.tracker.logic

import com.bulkplan.tracker.data.AppState
import com.bulkplan.tracker.data.Change
import com.bulkplan.tracker.data.DayLog
import com.bulkplan.tracker.data.Plan
import com.bulkplan.tracker.data.Proposal
import com.bulkplan.tracker.data.Repo
import com.bulkplan.tracker.logic.PlanLogic.addD
import com.bulkplan.tracker.logic.PlanLogic.diffD
import com.bulkplan.tracker.logic.PlanLogic.fmtN
import com.bulkplan.tracker.logic.PlanLogic.today
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.Locale

object Coach {
    fun rules(p: Plan, skipTests: List<String> = emptyList()): String {
        val pr = p.profile; val t = p.targets
        fun lb(x: Double) = fmtN(x)
        val dir = if (pr.goalWeight >= pr.startWeight) "gain" else "loss"
        val early = String.format(Locale.US, "%+.1f", t.gainEarly * 2); val late = String.format(Locale.US, "%+.1f", t.gainLate * 2)
        val skipped = listOfNotNull("DEXA scans".takeIf { "dexa" in skipTests }, "bloodwork".takeIf { "blood" in skipTests })
        return """You are a strength and nutrition coach for one person, working through their ${p.weeks}-week plan (long-term goal: ${lb(pr.startWeight)} lb to ${lb(pr.goalWeight)} lb${if (pr.goalMuscleLb > 0) ", +${lb(pr.goalMuscleLb)} lb of muscle" else ""}; target at the end of this plan about ${lb(pr.sixMonthWeight)} lb). Their plan, recent logs, food diary totals, Garmin data and lift history are below.

How to coach:
- Be direct and specific. Short paragraphs, plain words.
- Ask follow-up questions when you need context (sleep, pain, schedule, appetite, missed sessions). Ask at most 3 at a time, then wait for answers before proposing changes.
- Base calorie changes on the rule: compare 2-week average weights; target $early lb per 2 weeks in weeks 1–${t.earlyWeeks} and $late lb per 2 weeks after (weight $dir). More than 0.5 lb under target: +200 kcal as carbs. More than 0.5 lb over: −150 kcal.
- Respect their food dislikes and equipment.${if (skipped.isNotEmpty()) "\n- They chose not to do ${skipped.joinToString(" or ")}. Don't schedule or push them." else ""}
- You are not their doctor. Send anything that sounds like injury, abnormal blood results or other health problems to a doctor.

When you propose plan changes, end your message with ONE fenced json block exactly like this:
```json
{"summary":"one line","changes":[{"op":"set","path":["targets","calories"],"value":3700,"why":"weight flat for 2 weeks"}]}
```
Rules for changes: op is "set", "add" or "remove". path is an array of keys into the PLAN object. Inside arrays, address items by their "id" string (for example ["workouts","upper","exercises","bench","sets"] or ["schedule","caf","time"]). "add" appends value to the array at path (new exercises need id, name, sets, reps, muscle, compound, note; new schedule items need id, time "HH:MM", kind, title, detail, days [0=Sun..6=Sat], and meals also protein and kcal). "remove" deletes the item or key at path. Only include changes the person has agreed to or that the data clearly supports. The app shows each change to the person to approve before applying it. Leave the json block out when you're only asking questions."""
    }

    val STARTERS = listOf(
        "Weekly check-in" to "Start my weekly check-in. Look at my numbers, tell me what stands out, and ask me what you need to know before suggesting any changes.",
        "Weight is stalling" to "My weight has stopped going up. Help me figure out why and what to change.",
        "A lift went backwards" to "One of my lifts went backwards. Ask me what you need to and help me adjust.",
        "Something hurts" to "Something hurts when I train. Ask me about it and help me adjust the program around it.",
        "Adjust my meals" to "I want to change some of my meals or snacks. Ask me what isn’t working.",
        "Change my schedule" to "My schedule changed. Ask me what’s different and move things around.",
    )

    fun context(s: AppState, logs: Map<String, DayLog>): String {
        val p = s.plan; val t = today(); val w = PlanLogic.weekOf(p, t); val b = PlanLogic.blockFor(p, w)
        val days = (20 downTo 0).mapNotNull { i ->
            val d = addD(t, -i.toLong()); val g = logs[d] ?: return@mapNotNull null
            val items = PlanLogic.itemsFor(p, d); val done = items.count { g.checks[it.id] == true }
            val ft = PlanLogic.foodTotals(g)
            val exLogged = g.sets.count { PlanLogic.validSets(it.value).isNotEmpty() }
            buildString {
                append("$d | wt ${fmtN(g.weight)}")
                g.garmin?.weight?.let { append(" (garmin ${fmtN(it)})") }
                append(" | kcal ${fmtN(PlanLogic.kcalOf(g))} | prot ${fmtN(PlanLogic.proteinOf(g))}")
                if (g.foods.isNotEmpty()) append(" | carbs ${fmtN(ft.carbs)} fat ${fmtN(ft.fat)} (${g.foods.size} foods logged)")
                append(" | sleep ${fmtN(PlanLogic.sleepOf(g))} | steps ${fmtN(PlanLogic.stepsOf(g))}")
                g.garmin?.restingHr?.let { append(" | resting HR ${fmtN(it)}") }
                g.garmin?.bodyBattery?.let { append(" | body battery ${fmtN(it)}") }
                append(" | energy ${g.energy ?: "-"} | sore ${g.sore ?: "-"} | appetite ${g.hunger ?: "-"} | checklist $done/${items.size}")
                if (exLogged > 0) append(" | $exLogged exercises logged")
                g.notes?.takeIf { it.isNotBlank() }?.let { append(" | note: ${it.replace(Regex("\\s+"), " ").take(240)}") }
                if (g.m.values.any { it != null && it != JsonNull }) append(" | measurements ${Repo.json.encodeToString(JsonObject.serializer(), JsonObject(g.m.mapValues { it.value ?: JsonNull }))}")
            }
        }
        val lifts = p.workouts.values.flatMap { it.exercises }.mapNotNull { ex ->
            val ds = logs.keys.filter { it <= t && PlanLogic.validSets(logs[it]?.sets?.get(ex.id)).isNotEmpty() }.sorted().takeLast(3)
            if (ds.isEmpty()) null else "${ex.name} (${ex.reps}): " + ds.joinToString(" ; ") { d ->
                d + " " + PlanLogic.validSets(logs[d]?.sets?.get(ex.id)).joinToString(",") { "${fmtN(it.w)}x${fmtN(it.r)}" }
            }
        }
        val cr = PlanLogic.calorieRule(p, logs)
        val todayLog = logs[t]
        val status = if (w < 1) "Plan has not started yet (${diffD(t, p.startDate)} days to go)."
        else "Week $w of ${p.weeks}" + (b?.let { ", block \"${it.name}\": ${it.note} Effort ${it.rir} reps short of failure." } ?: "")
        return """TODAY: $t (${PlanLogic.DAYN[PlanLogic.dow(t)]}). Plan start ${p.startDate}. $status
CURRENT 7-DAY AVERAGE WEIGHT: ${String.format(Locale.US, "%.1f", PlanLogic.curWeight(p, logs))} lb. Protein target ${PlanLogic.proteinTarget(p, logs)} g. Calorie target ${p.targets.calories}.
APP'S CALORIE RULE CHECK: ${cr.text}
PRE-START CHECKLIST: ${PlanLogic.prepFor(p, s.settings).joinToString("; ") { (if (s.prepDone[it.id] == true) "[done] " else "[ ] ") + it.text }}
${about(s)}
TODAY'S SCHEDULE (now ${PlanLogic.fmtT("%02d:%02d".format(PlanLogic.nowMins() / 60, PlanLogic.nowMins() % 60))}):
${todayLog.let { g -> PlanLogic.itemsFor(p, t).joinToString("\n") { i -> "- ${PlanLogic.fmtT(i.time)} ${i.title}" + (if (g?.checks?.get(i.id) == true) " [done]" else "") } }}

TODAY'S FOOD LOG:
${todayLog?.foods?.joinToString("\n") { f -> "- ${f.meal}: ${f.name} (${fmtN(f.kcal)} kcal, ${fmtN(f.protein)} g protein, ${fmtN(f.carbs)} g carbs, ${fmtN(f.fat)} g fat)" }?.takeIf { it.isNotBlank() } ?: "(nothing logged yet today)"}

DAILY LOG, LAST 21 DAYS:
${days.joinToString("\n").ifEmpty { "(nothing logged yet)" }}

LIFTS, LAST 3 SESSIONS EACH (weight x reps):
${lifts.joinToString("\n").ifEmpty { "(no workouts logged yet)" }}

RECENT PLAN CHANGES: ${s.changelog.takeLast(5).joinToString("; ") { it.at.take(10) + ": " + it.summary }.ifEmpty { "none" }}

PLAN (JSON):
${Repo.json.encodeToString(Plan.serializer(), p)}"""
    }

    /** Intake answers worth knowing every time (age, height, schedule, food limits). */
    private fun about(s: AppState): String {
        val keys = listOf("age", "sex", "height", "experience", "injuries", "work", "workFood", "diet", "allergies", "dislikes", "favorites", "appetite", "budget", "other")
        val parts = keys.mapNotNull { k ->
            val v = s.intake[k]?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            (Intake.ALL.firstOrNull { it.id == k }?.label ?: k) + ": " + v.replace(Regex("\\s+"), " ")
        }
        return if (parts.isEmpty()) "" else "ABOUT THEM (intake answers): " + parts.joinToString("; ") + "\n"
    }

    /** Rules plus everything the app knows, sent fresh with every coach message. */
    fun systemPrompt(s: AppState, logs: Map<String, DayLog>) =
        rules(s.plan, s.settings.skipTests) + "\n\nThe app attaches the person's current data below on every message, so it is always up to date. " +
            "Use it directly: never say you can't see their data or ask them to paste numbers that are already here.\n\n" + context(s, logs)

    fun fullPrompt(s: AppState, logs: Map<String, DayLog>, ask: String) = rules(s.plan, s.settings.skipTests) + "\n\n" + context(s, logs) + "\n\n---\n" + ask

    // ---------- proposals ----------
    fun parseProposal(text: String): Proposal? {
        val blocks = Regex("```json\\s*([\\s\\S]*?)```").findAll(text).toList()
        var raw = blocks.lastOrNull()?.groupValues?.get(1)
        if (raw == null) {
            val i = text.indexOf("{\"summary\""); val j = text.lastIndexOf('}')
            if (i >= 0 && j > i) raw = text.substring(i, j + 1)
        }
        raw ?: return null
        return try {
            val o = Repo.json.parseToJsonElement(raw).jsonObject
            val changes = o["changes"]?.jsonArray?.map { Repo.json.decodeFromJsonElement<Change>(it) }.orEmpty()
            if (changes.isEmpty()) null
            else Proposal((o["summary"] as? JsonPrimitive)?.content ?: "Plan update", changes.take(40))
        } catch (e: Exception) {
            Proposal(error = "The AI’s change list couldn’t be read: ${e.message}")
        }
    }

    fun stripJson(text: String) = text.replace(Regex("```json[\\s\\S]*?(```|$)"), "").trim()

    // ---------- applying changes to the plan as a JSON tree ----------
    private fun toMut(e: JsonElement?): Any? = when (e) {
        null, JsonNull -> null
        is JsonObject -> LinkedHashMap<String, Any?>().apply { e.forEach { (k, v) -> put(k, toMut(v)) } }
        is JsonArray -> e.map { toMut(it) }.toMutableList()
        is JsonPrimitive -> e
    }

    @Suppress("UNCHECKED_CAST")
    private fun toJson(a: Any?): JsonElement = when (a) {
        null -> JsonNull
        is JsonElement -> a
        is Map<*, *> -> JsonObject((a as Map<String, Any?>).mapValues { toJson(it.value) })
        is List<*> -> JsonArray(a.map { toJson(it) })
        else -> JsonPrimitive(a.toString())
    }

    private fun segStr(e: JsonElement): String = (e as? JsonPrimitive)?.content ?: e.toString()

    private fun indexIn(list: List<Any?>, seg: String): Int {
        seg.toIntOrNull()?.let { return if (it in list.indices) it else -1 }
        return list.indexOfFirst { (it as? Map<*, *>)?.get("id")?.let { id -> (id as? JsonPrimitive)?.content == seg } == true }
    }

    @Suppress("UNCHECKED_CAST")
    private fun applyOne(root: MutableMap<String, Any?>, ch: Change): Pair<Any?, Any?> {
        if (ch.op !in listOf("set", "add", "remove")) error("Unknown operation")
        if (ch.path.isEmpty()) error("Missing path")
        val path = ch.path.map { segStr(it) }
        var cur: Any? = root
        for (seg in path.dropLast(1)) {
            cur = when (cur) {
                is MutableList<*> -> indexIn(cur, seg).let { i -> if (i < 0) null else cur[i] }
                is MutableMap<*, *> -> cur[seg]
                else -> null
            } ?: error("Not found: " + path.take(path.indexOf(seg) + 1).joinToString(" › "))
        }
        val last = path.last()
        val value = toMut(ch.value)
        when (val par = cur) {
            is MutableList<*> -> {
                val list = par as MutableList<Any?>
                val i = indexIn(list, last)
                return when (ch.op) {
                    "set" -> { if (i < 0) error("Item not found"); val b = list[i]; list[i] = value; b to value }
                    "remove" -> { if (i < 0) error("Item not found"); list.removeAt(i) to null }
                    else -> {
                        val target = if (i >= 0) list[i] else null
                        if (target is MutableList<*>) { (target as MutableList<Any?>).add(value); null to value } else error("Can only add to a list")
                    }
                }
            }
            is MutableMap<*, *> -> {
                val map = par as MutableMap<String, Any?>
                val before = map[last]
                return when (ch.op) {
                    "set" -> { map[last] = value; before to value }
                    "remove" -> { if (!map.containsKey(last)) error("Not found"); map.remove(last); before to null }
                    else -> when (before) {
                        is MutableList<*> -> {
                            if (value is MutableMap<*, *>) {
                                val v = value as MutableMap<String, Any?>
                                if (v["id"] == null) v["id"] = JsonPrimitive("c" + System.currentTimeMillis().toString(36))
                                val id = (v["id"] as? JsonPrimitive)?.content
                                if ((before as List<Any?>).any { (it as? Map<*, *>)?.get("id")?.let { x -> (x as? JsonPrimitive)?.content == id } == true }) error("An item with id \"$id\" already exists")
                            }
                            (before as MutableList<Any?>).add(value); null to value
                        }
                        null -> { map[last] = value; null to value }
                        else -> error("Can only add to a list")
                    }
                }
            }
            else -> error("Not found")
        }
    }

    data class Preview(val ok: Boolean, val label: String, val info: String, val why: String?)

    /** Applies changes in order; returns the new plan (null if none applied) and a line per change. */
    @Suppress("UNCHECKED_CAST")
    fun apply(plan: Plan, changes: List<Change>): Pair<Plan?, List<Preview>> {
        val root = toMut(Repo.json.encodeToJsonElement(Plan.serializer(), plan)) as MutableMap<String, Any?>
        val out = mutableListOf<Preview>()
        var applied = 0
        for (c in changes) {
            val label = pathLabel(plan, c.path.map { segStr(it) })
            val snapshot = toJson(root)
            try {
                val (b, a) = applyOne(root, c)
                // Make sure the plan still reads as a valid plan after this change.
                Repo.json.decodeFromJsonElement<Plan>(toJson(root))
                out += Preview(true, label, when (c.op) {
                    "add" -> "Add: ${short(a)}"
                    "remove" -> "Remove: ${short(b)}"
                    else -> "${short(b)} → ${short(a)}"
                }, c.why)
                applied++
            } catch (e: Exception) {
                // Roll this one back.
                val restored = toMut(snapshot) as MutableMap<String, Any?>
                root.clear(); root.putAll(restored)
                out += Preview(false, label, e.message ?: "Can’t apply this one", c.why)
            }
        }
        val newPlan = if (applied > 0) runCatching { Repo.json.decodeFromJsonElement<Plan>(toJson(root)) }.getOrNull() else null
        return newPlan to out
    }

    private fun short(v: Any?): String = when (v) {
        null -> "—"
        is Map<*, *> -> ((v["name"] ?: v["title"] ?: v["text"]) as? JsonPrimitive)?.content ?: toJson(v).toString().take(90)
        is List<*> -> toJson(v).toString().take(90)
        is JsonPrimitive -> v.content
        else -> v.toString()
    }

    private val NAMES = mapOf("targets" to "Targets", "workouts" to "Workouts", "exercises" to "exercises", "schedule" to "Schedule",
        "blocks" to "Blocks", "weakMuscles" to "Weak muscles", "proteinPerLb" to "protein per lb", "calories" to "calories")

    fun pathLabel(plan: Plan, path: List<String>): String {
        val tree = Repo.json.encodeToJsonElement(Plan.serializer(), plan)
        var cur: JsonElement? = tree
        val out = mutableListOf<String>()
        for (seg in path) {
            var lab = NAMES[seg] ?: seg
            cur = when (val c = cur) {
                is JsonArray -> {
                    val i = seg.toIntOrNull()?.takeIf { it in c.indices } ?: c.indexOfFirst { (it as? JsonObject)?.get("id")?.jsonPrimitive?.content == seg }
                    val it = if (i >= 0) c[i] as? JsonObject else null
                    if (it != null) {
                        val nm = it["name"]?.jsonPrimitive?.content ?: it["title"]?.jsonPrimitive?.content?.let { t -> t + (it["time"]?.jsonPrimitive?.content?.let { tm -> " " + PlanLogic.fmtT(tm) } ?: "") } ?: it["text"]?.jsonPrimitive?.content
                        if (nm != null) lab = nm
                    }
                    it
                }
                is JsonObject -> { val n = c[seg]; (n as? JsonObject)?.get("name")?.jsonPrimitive?.content?.let { lab = it }; n }
                else -> null
            }
            out += lab
        }
        return out.joinToString(" › ")
    }

    @Suppress("unused")
    private fun prim(e: JsonPrimitive): Any = e.booleanOrNull ?: e.longOrNull ?: e.doubleOrNull ?: e.content
}
