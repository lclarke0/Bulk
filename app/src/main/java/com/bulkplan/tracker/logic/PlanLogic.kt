package com.bulkplan.tracker.logic

import com.bulkplan.tracker.data.Block
import com.bulkplan.tracker.data.DayLog
import com.bulkplan.tracker.data.Exercise
import com.bulkplan.tracker.data.Plan
import com.bulkplan.tracker.data.ScheduleItem
import com.bulkplan.tracker.data.SetEntry
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

data class Macros(val kcal: Double = 0.0, val protein: Double = 0.0, val carbs: Double = 0.0, val fat: Double = 0.0) {
    operator fun plus(o: Macros) = Macros(kcal + o.kcal, protein + o.protein, carbs + o.carbs, fat + o.fat)
    operator fun times(k: Double) = Macros(kcal * k, protein * k, carbs * k, fat * k)
}

data class CalorieCall(val state: String, val text: String, val change: Int? = null)

object PlanLogic {
    val DAYS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val DAYN = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    fun today(): String = LocalDate.now().toString()
    fun date(s: String): LocalDate = LocalDate.parse(s)
    fun addD(s: String, n: Long): String = date(s).plusDays(n).toString()
    fun diffD(a: String, b: String): Long = ChronoUnit.DAYS.between(date(a), date(b))
    /** 0 = Sunday … 6 = Saturday, like the web tracker. */
    fun dow(s: String): Int = date(s).dayOfWeek.value % 7

    fun mins(t: String): Int = runCatching { val p = t.split(":"); p[0].toInt() * 60 + p[1].toInt() }.getOrDefault(0)
    fun nowMins(): Int = LocalTime.now().let { it.hour * 60 + it.minute }
    fun timeParts(t: String): Pair<String, String> {
        val m = mins(t); val h = m / 60; val mm = m % 60
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "$h12:${mm.toString().padStart(2, '0')}" to (if (h >= 12) "pm" else "am")
    }
    fun fmtT(t: String) = timeParts(t).let { "${it.first} ${it.second}" }
    fun fmtD(s: String): String = date(s).format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US))
    fun fmtN(n: Double?): String = when {
        n == null -> "—"
        n == n.roundToInt().toDouble() -> String.format(Locale.US, "%,d", n.roundToInt())
        else -> String.format(Locale.US, "%,.1f", n)
    }
    fun durText(m: Int) = if (m >= 60) "${m / 60}h ${(m % 60).toString().padStart(2, '0')}m" else "$m min"

    fun weekOf(plan: Plan, d: String): Int = Math.floorDiv(diffD(plan.startDate, d), 7L).toInt() + 1
    fun blockFor(plan: Plan, w: Int): Block? = plan.blocks.firstOrNull { w >= it.from && w <= it.to }

    fun setsFor(plan: Plan, ex: Exercise, b: Block?): Int = when (b?.mode) {
        null -> ex.sets
        "two" -> 2
        "c3" -> if (ex.compound) 3 else 2
        "half" -> max(1, (ex.sets + 1) / 2)
        "spec" -> ex.sets + if (plan.weakMuscles.contains(ex.muscle)) 1 else 0
        else -> ex.sets
    }

    fun workoutForDay(plan: Plan, dw: Int): String? = plan.workouts.entries.firstOrNull { it.value.day == dw }?.key

    fun itemsFor(plan: Plan, d: String): List<ScheduleItem> {
        val dw = dow(d)
        val list = plan.schedule.filter { it.days.contains(dw) }.toMutableList()
        if (dw == 0 && date(d).dayOfMonth <= 7) list.add(
            ScheduleItem("monthly", "17:15", "task", "Monthly check",
                "Measure your waist and body, take front, side and back photos, and check blood pressure and resting heart rate. Log them under Today › Measurements.")
        )
        val wk = workoutForDay(plan, dw)
        val b = blockFor(plan, max(1, weekOf(plan, d)))
        return list.map { i ->
            if (i.kind == "train" && wk != null) {
                val w = plan.workouts.getValue(wk)
                i.copy(title = "Train: ${w.name}", detail = "${w.exercises.size} exercises. ${b?.note ?: ""} Stop ${b?.rir ?: "0–2"} reps short of failure.")
            } else i
        }.sortedBy { mins(it.time) }
    }

    // ---- daily numbers (typed values win over Garmin's) ----
    fun weightOf(l: DayLog?): Double? = l?.weight ?: l?.garmin?.weight
    fun stepsOf(l: DayLog?): Double? = l?.steps ?: l?.garmin?.steps
    fun sleepOf(l: DayLog?): Double? = l?.sleep ?: l?.garmin?.sleepH
    fun foodTotals(l: DayLog?): Macros = l?.foods?.fold(Macros()) { a, f -> a + Macros(f.kcal, f.protein, f.carbs, f.fat) } ?: Macros()
    fun kcalOf(l: DayLog?): Double? = if (l == null) null else if (l.foods.isNotEmpty()) foodTotals(l).kcal else l.cal
    fun proteinOf(l: DayLog?): Double? = if (l == null) null else if (l.foods.isNotEmpty()) foodTotals(l).protein else l.protein

    fun weights(logs: Map<String, DayLog>, from: String, to: String): List<Pair<String, Double>> {
        val out = mutableListOf<Pair<String, Double>>()
        var d = from
        while (d <= to) { weightOf(logs[d])?.let { out.add(d to it) }; d = addD(d, 1) }
        return out
    }
    fun avgW(logs: Map<String, DayLog>, from: String, to: String): Double? =
        weights(logs, from, to).map { it.second }.takeIf { it.isNotEmpty() }?.average()

    fun curWeight(plan: Plan, logs: Map<String, DayLog>): Double {
        val t = today()
        return avgW(logs, addD(t, -6), t) ?: weights(logs, addD(t, -60), t).lastOrNull()?.second ?: plan.profile.startWeight
    }
    fun proteinTarget(plan: Plan, logs: Map<String, DayLog>): Int = (curWeight(plan, logs) * plan.targets.proteinPerLb).roundToInt()

    fun targetWeightOn(plan: Plan, d: String): Double {
        val wk = diffD(plan.startDate, d) / 7.0
        if (wk <= 0) return plan.profile.startWeight
        val t = plan.targets
        val e = minOf(wk, t.earlyWeeks.toDouble())
        return plan.profile.startWeight + e * t.gainEarly + max(0.0, wk - t.earlyWeeks) * t.gainLate
    }

    fun calorieRule(plan: Plan, logs: Map<String, DayLog>): CalorieCall {
        val t = today(); val w = weekOf(plan, t); val T = plan.targets
        val target = 2 * (if (w <= T.earlyWeeks) T.gainEarly else T.gainLate)
        if (w < 1) return CalorieCall("pre", "The calorie rule starts once you’re 4 weeks in.")
        if (w <= 2) return CalorieCall("early", "Weeks 1–2: ignore the scale. A 2–4 lb jump from water, glycogen and creatine is expected.")
        val a = weights(logs, addD(t, -13), t); val b = weights(logs, addD(t, -27), addD(t, -14))
        if (a.size < 4 || b.size < 4) return CalorieCall("need", "Log your morning weight at least 4 days in each of the last two 2-week windows to get a calorie call.")
        val delta = a.map { it.second }.average() - b.map { it.second }.average()
        val d = String.format(Locale.US, "%+.1f", delta); val tg = String.format(Locale.US, "%+.1f", target)
        return when {
            delta < target - 0.5 -> CalorieCall("up", "2-week change is $d lb against a $tg lb target. Add 200 calories, as carbs.", 200)
            delta > target + 0.5 -> CalorieCall("down", "2-week change is $d lb against a $tg lb target. Cut 150 calories, from fat or carbs.", -150)
            else -> CalorieCall("hold", "2-week change is $d lb against a $tg lb target. On track: change nothing.")
        }
    }

    fun validSets(l: List<SetEntry?>?): List<SetEntry> = l.orEmpty().filterNotNull().filter { it.r != null }

    fun lastSession(logs: Map<String, DayLog>, exId: String, before: String): Pair<String, List<SetEntry>>? =
        logs.keys.filter { it < before && validSets(logs[it]?.sets?.get(exId)).any { s -> s.w != null } }
            .maxOrNull()?.let { it to validSets(logs[it]?.sets?.get(exId)) }

    fun topRep(reps: String): Int? = Regex("\\d+").findAll(reps).map { it.value.toInt() }.maxOrNull()

    fun suggestion(ex: Exercise, b: Block?, wkKey: String, last: Pair<String, List<SetEntry>>?): String {
        val rir = b?.rir ?: "0–2"
        if (last == null) return "First session: pick a weight that lands in ${ex.reps} reps with $rir reps left in the tank."
        val w = last.second.maxOf { it.w ?: 0.0 }
        val top = topRep(ex.reps)
        val allTop = top != null && last.second.isNotEmpty() && last.second.all { (it.r ?: 0.0) >= top }
        if (b?.mode == "half") return "Deload: use ${fmtN(w)} lb and stop $rir reps short."
        if (allTop) {
            val lower = wkKey == "lower" || wkKey == "legs"
            return if (ex.compound) {
                val jump = if (lower) 10 else 5
                "You hit the top of the range. Add $jump lb: ${fmtN(w + jump)} lb, back to the bottom of the range."
            } else "You hit the top of the range. Use the smallest weight jump available above ${fmtN(w)} lb."
        }
        return "Stay at ${fmtN(w)} lb and beat last time by at least one rep."
    }

    fun e1rm(w: Double, r: Double) = w * (1 + r / 30.0)
}
