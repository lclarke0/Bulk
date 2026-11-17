@file:OptIn(ExperimentalMaterial3Api::class)

package com.bulkplan.tracker.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bulkplan.tracker.LocalToast
import com.bulkplan.tracker.data.DayLog
import com.bulkplan.tracker.data.Plan
import com.bulkplan.tracker.data.Repo
import com.bulkplan.tracker.logic.PlanLogic
import com.bulkplan.tracker.logic.PlanLogic.addD
import com.bulkplan.tracker.logic.PlanLogic.fmtN
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun ProgressScreen() {
    val state by Repo.state.collectAsStateWithLifecycle()
    val logs by Repo.logs.collectAsStateWithLifecycle()
    val plan = state.plan
    val t = PlanLogic.today()
    val toast = LocalToast.current
    var editDay by remember { mutableStateOf<String?>(null) }
    val a7 = PlanLogic.avgW(logs, addD(t, -6), t)
    val p7 = PlanLogic.avgW(logs, addD(t, -13), addD(t, -7))
    val last7 = (0..6).mapNotNull { logs[addD(t, -it.toLong())] }
    fun avg(f: (DayLog) -> Double?) = last7.mapNotNull(f).takeIf { it.isNotEmpty() }?.average()
    val cr = PlanLogic.calorieRule(plan, logs)

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("7-day avg weight", a7?.let { String.format(Locale.US, "%.1f", it) } ?: "—", "lb",
                    if (a7 != null && p7 != null) String.format(Locale.US, "%+.1f lb vs last week", a7 - p7) else "Log daily to see the trend", Modifier.weight(1f))
                Stat("Avg calories", fmtN(avg { PlanLogic.kcalOf(it) }?.let { Math.rint(it) }), "", "Target ${fmtN(plan.targets.calories.toDouble())}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Avg protein", fmtN(avg { PlanLogic.proteinOf(it) }?.let { Math.rint(it) }), "g", "Target ${PlanLogic.proteinTarget(plan, logs)} g", Modifier.weight(1f))
                Stat("Avg sleep", fmtN(avg { PlanLogic.sleepOf(it) }?.let { (it * 10).roundToInt() / 10.0 }), "h",
                    "Steps ${fmtN(avg { PlanLogic.stepsOf(it) }?.let { Math.rint(it) })}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }
        item {
            Section("Weight vs target pace") {
                Muted("Dots: daily · blue line: 7-day average · dashes: target pace")
                WeightChart(plan, logs)
            }
        }
        item {
            Section("Calorie call") {
                Text(cr.text)
                cr.change?.let { c ->
                    Button(onClick = {
                        Repo.changePlan("Calories ${plan.targets.calories} → ${plan.targets.calories + c} (2-week rule)") { it.copy(targets = it.targets.copy(calories = it.targets.calories + c)) }
                        toast("Calorie target updated")
                    }) { Text("${if (c > 0) "Add" else "Cut"} ${kotlin.math.abs(c)} kcal (${plan.targets.calories} → ${plan.targets.calories + c})") }
                }
            }
        }
        item {
            Section("Main lifts") {
                Muted("Estimated 1-rep max from your best set, first session vs latest.")
                val rows = plan.workouts.values.flatMap { it.exercises }.filter { it.compound }.mapNotNull { ex ->
                    val ds = logs.keys.filter { PlanLogic.validSets(logs[it]?.sets?.get(ex.id)).any { s -> s.w != null } }.sorted()
                    if (ds.isEmpty()) return@mapNotNull null
                    fun best(d: String) = PlanLogic.validSets(logs[d]?.sets?.get(ex.id)).filter { it.w != null }.maxOf { PlanLogic.e1rm(it.w!!, it.r!!) }
                    Triple(ex.name, best(ds.first()), best(ds.last()))
                }
                if (rows.isEmpty()) Muted("Log a few workouts to see your lifts here.")
                rows.forEach { (n, f, l) ->
                    Row {
                        Text(n, modifier = Modifier.weight(1f), fontSize = 15.sp)
                        Text("${f.roundToInt()} → ${l.roundToInt()}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(String.format(Locale.US, "  %+d%%", ((l / f - 1) * 100).roundToInt()), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Section("Last 14 days") {
                Muted("Tap a day to fix its numbers.")
                Row {
                    listOf("Day" to 1.4f, "Weight" to 1f, "Calories" to 1f, "Protein" to 1f, "Sleep" to .8f).forEach { (h, wt) ->
                        Text(h, modifier = Modifier.weight(wt), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                (0..13).map { addD(t, -it.toLong()) }.forEach { d ->
                    val g = logs[d]
                    Row(Modifier.fillMaxWidth().clickable { editDay = d }.padding(vertical = 6.dp)) {
                        Text(PlanLogic.fmtD(d), modifier = Modifier.weight(1.4f), fontSize = 14.sp)
                        Text(fmtN(PlanLogic.weightOf(g)), modifier = Modifier.weight(1f), fontSize = 14.sp)
                        Text(fmtN(PlanLogic.kcalOf(g)?.let { Math.rint(it) }), modifier = Modifier.weight(1f), fontSize = 14.sp)
                        Text(fmtN(PlanLogic.proteinOf(g)?.let { Math.rint(it) }) + " g", modifier = Modifier.weight(1f), fontSize = 14.sp)
                        Text(fmtN(PlanLogic.sleepOf(g)) + " h", modifier = Modifier.weight(0.8f), fontSize = 14.sp)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }
        item {
            Section("Milestones") {
                plan.milestones.forEach { m ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text(PlanLogic.fmtD(addD(plan.startDate, (m.week - 1) * 7L)), modifier = Modifier.weight(0.9f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Week ${m.week}: ${m.text}", modifier = Modifier.weight(2f), fontSize = 14.sp)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    editDay?.let { d -> DayEditor(d) { editDay = null } }
}

@Composable
private fun Stat(label: String, value: String, unit: String, sub: String, modifier: Modifier) {
    Section(modifier = modifier.padding(bottom = 0.dp)) {
        Muted(label)
        Row { Text(value, style = Big); if (unit.isNotEmpty()) Text(" $unit", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp)) }
        Muted(sub)
    }
}

@Composable
private fun WeightChart(plan: Plan, logs: Map<String, DayLog>) {
    val t = PlanLogic.today(); val st = plan.startDate
    val end = addD(st, plan.weeks * 7L)
    val from = if (st < t) addD(st, -7) else addD(t, -21)
    val to = if (st < t) minOf(addD(t, 7), end) else addD(st, 28)
    val pts = PlanLogic.weights(logs, from, t)
    val avg = pts.map { it.first to (PlanLogic.avgW(logs, addD(it.first, -6), it.first) ?: it.second) }
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outline
    val accent = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(240.dp)) {
        val ys = pts.map { it.second } + PlanLogic.targetWeightOn(plan, from) + PlanLogic.targetWeightOn(plan, to)
        val lo = floor(ys.min() - 2); val hi = ceil(ys.max() + 2)
        val left = 44.dp.toPx(); val right = 8.dp.toPx(); val top = 8.dp.toPx(); val bottom = 24.dp.toPx()
        val n = PlanLogic.diffD(from, to).coerceAtLeast(1).toFloat()
        fun x(d: String) = left + PlanLogic.diffD(from, d) / n * (size.width - left - right)
        fun y(v: Double) = (top + (hi - v) / (hi - lo) * (size.height - top - bottom)).toFloat()
        val paint = Paint().apply { color = ink.toArgb(); textSize = 11.sp.toPx(); isAntiAlias = true }
        val step = ((hi - lo) / 4).roundToInt().coerceAtLeast(1)
        var v = lo
        while (v <= hi) {
            drawLine(grid, Offset(left, y(v)), Offset(size.width - right, y(v)), 1f)
            paint.textAlign = Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText(v.roundToInt().toString(), left - 6.dp.toPx(), y(v) + 4.dp.toPx(), paint)
            v += step
        }
        val ticks = 4
        for (i in 0..ticks) {
            val d = addD(from, (n * i / ticks).roundToInt().toLong())
            paint.textAlign = when (i) { 0 -> Paint.Align.LEFT; ticks -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
            val ld = PlanLogic.date(d)
            drawContext.canvas.nativeCanvas.drawText("${ld.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${ld.dayOfMonth}", x(d), size.height - 4.dp.toPx(), paint)
        }
        // Target pace
        val tp = Path().apply {
            moveTo(x(from), y(PlanLogic.targetWeightOn(plan, from)))
            val mid = if (st > from) st else from
            lineTo(x(mid), y(PlanLogic.targetWeightOn(plan, mid)))
            lineTo(x(to), y(PlanLogic.targetWeightOn(plan, to)))
        }
        drawPath(tp, ink, style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
        if (t in from..to) drawLine(grid, Offset(x(t), top), Offset(x(t), size.height - bottom), 2.dp.toPx())
        pts.forEach { (d, w) -> drawCircle(ink.copy(alpha = .6f), 3.dp.toPx(), Offset(x(d), y(w))) }
        if (avg.size > 1) {
            val p = Path()
            avg.forEachIndexed { i, (d, w) -> if (i == 0) p.moveTo(x(d), y(w)) else p.lineTo(x(d), y(w)) }
            drawPath(p, accent, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (pts.isEmpty()) {
            paint.textAlign = Paint.Align.CENTER; paint.textSize = 14.sp.toPx()
            drawContext.canvas.nativeCanvas.drawText("Log your morning weight to start the line", (size.width + left) / 2, size.height / 2, paint)
        }
    }
}

@Composable
private fun DayEditor(d: String, onClose: () -> Unit) {
    val logs by Repo.logs.collectAsStateWithLifecycle()
    val lg = logs[d] ?: DayLog(d)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(PlanLogic.fmtD(d)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Morning weight (lb)", lg.weight, "ew$d", Modifier.fillMaxWidth()) { v -> Repo.updateLog(d) { it.copy(weight = v) } }
                NumField("Sleep (h)", lg.sleep, "es$d", Modifier.fillMaxWidth()) { v -> Repo.updateLog(d) { it.copy(sleep = v) } }
                NumField("Steps", lg.steps, "est$d", Modifier.fillMaxWidth()) { v -> Repo.updateLog(d) { it.copy(steps = v) } }
                if (lg.foods.isEmpty()) {
                    NumField("Calories (no food diary that day)", lg.cal, "ec$d", Modifier.fillMaxWidth()) { v -> Repo.updateLog(d) { it.copy(cal = v) } }
                    NumField("Protein (g)", lg.protein, "ep$d", Modifier.fillMaxWidth()) { v -> Repo.updateLog(d) { it.copy(protein = v) } }
                } else Muted("Calories and protein come from that day’s food diary (Food tab).")
                TextIn("Notes", lg.notes ?: "", "en$d", Modifier.fillMaxWidth(), singleLine = false, minLines = 2) { v -> Repo.updateLog(d) { it.copy(notes = v.ifBlank { null }) } }
                MeasurementsCard(d, lg)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
    )
}

@Suppress("unused")
private val unusedColor = Color.Unspecified
