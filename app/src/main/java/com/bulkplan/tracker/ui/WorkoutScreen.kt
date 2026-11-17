@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bulkplan.tracker.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bulkplan.tracker.data.Block
import com.bulkplan.tracker.data.DayLog
import com.bulkplan.tracker.data.DefaultPlan
import com.bulkplan.tracker.data.Exercise
import com.bulkplan.tracker.data.Plan
import com.bulkplan.tracker.data.Repo
import com.bulkplan.tracker.data.SetEntry
import com.bulkplan.tracker.logic.PlanLogic
import com.bulkplan.tracker.logic.PlanLogic.fmtN
import kotlinx.coroutines.delay
import kotlin.math.max

private fun setSet(date: String, wk: String, exId: String, i: Int, f: (SetEntry) -> SetEntry) = Repo.updateLog(date) { l ->
    val list = (l.sets[exId] ?: emptyList()).toMutableList()
    while (list.size <= i) list.add(SetEntry())
    list[i] = f(list[i] ?: SetEntry())
    l.copy(sets = l.sets + (exId to list), workout = wk)
}

private fun alarm(ctx: Context) {
    runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80).startTone(ToneGenerator.TONE_PROP_BEEP2, 500) }
    runCatching { ctx.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)) }
}

private fun doneSets(l: DayLog, exId: String) = (l.sets[exId] ?: emptyList()).count { it?.ok == true || (it?.r != null && it.w != null) }

private fun muscleColor(m: String): Color = when (m) {
    "chest", "front delts", "side delts", "rear delts", "triceps" -> C.BlueSoft
    "back", "biceps", "traps" -> C.Purple
    "quads", "hamstrings", "calves" -> C.Yellow
    else -> C.Green
}

@Composable
fun WorkoutScreen() {
    val state by Repo.state.collectAsStateWithLifecycle()
    val logs by Repo.logs.collectAsStateWithLifecycle()
    val plan = state.plan
    val t = PlanLogic.today()
    val keys = plan.workouts.keys.sortedBy { (plan.workouts.getValue(it).day + 6) % 7 }
    var wk by rememberSaveable {
        mutableStateOf(PlanLogic.workoutForDay(plan, PlanLogic.dow(t))
            ?: keys.firstOrNull { (plan.workouts.getValue(it).day - PlanLogic.dow(t) + 7) % 7 > 0 } ?: keys.firstOrNull() ?: "")
    }
    val workout = plan.workouts[wk]
    val w = PlanLogic.weekOf(plan, t)
    val b = PlanLogic.blockFor(plan, max(1, w))
    val lg = logs[t] ?: DayLog(t)
    var editing by remember { mutableStateOf<Exercise?>(null) }
    var adding by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current

    var restEnd by remember { mutableLongStateOf(0L) }
    var restTotal by remember { mutableLongStateOf(1L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(restEnd) {
        while (restEnd > 0) {
            now = System.currentTimeMillis()
            if (now >= restEnd) { restEnd = 0; alarm(ctx); break }
            delay(250)
        }
    }
    fun startRest(ms: Long) { restTotal = ms; restEnd = System.currentTimeMillis() + ms; now = System.currentTimeMillis() }
    val left = if (restEnd > 0) ((restEnd - now) / 1000).coerceAtLeast(0) else 0

    if (workout == null) { Muted("No workouts in the plan.", Modifier.padding(16.dp)); return }
    val total = workout.exercises.sumOf { PlanLogic.setsFor(plan, it, b) }
    val done = workout.exercises.sumOf { minOf(doneSets(lg, it.id), PlanLogic.setsFor(plan, it, b)) }
    val volume = workout.exercises.sumOf { ex -> (lg.sets[ex.id] ?: emptyList()).sumOf { s -> (s?.w ?: 0.0) * (s?.r ?: 0.0) } }
    val exDone = workout.exercises.count { doneSets(lg, it.id) >= PlanLogic.setsFor(plan, it, b) }
    val open = expanded ?: workout.exercises.firstOrNull { doneSets(lg, it.id) < PlanLogic.setsFor(plan, it, b) }?.id

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                keys.forEach { k ->
                    val wo = plan.workouts.getValue(k)
                    Chip("${PlanLogic.DAYS[wo.day]} · ${wo.name}", k == wk) { wk = k; expanded = null }
                }
            }
        }
        item {
            Section {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ring(if (total == 0) 0f else done / total.toFloat(), 132.dp, color = C.Blue, stroke = 12.dp) {
                        Text("$done", color = C.Text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Text("/ $total", color = C.Text, fontSize = 15.sp)
                        Text("sets", color = C.BlueSoft, fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Box(Modifier.width(1.dp).height(120.dp).background(C.Line))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(workout.name, style = H2)
                        Tag(if (w < 1) "Week 1 preview" else "Week $w" + (b?.let { " · ${it.name}" } ?: ""))
                        Stat2("Exercises", "$exDone / ${workout.exercises.size}")
                        Stat2("Volume", "${fmtN(Math.rint(volume))} lb")
                        Stat2("Effort", "${b?.rir ?: "0–2"} reps left")
                    }
                }
                Muted(b?.note ?: "")
            }
        }
        item {
            Section {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ico.Timer, null, tint = C.BlueSoft, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Rest", style = H3, modifier = Modifier.weight(1f))
                    Text("${left / 60}:${(left % 60).toString().padStart(2, '0')}", fontSize = 34.sp, fontWeight = FontWeight.Bold,
                        color = if (restEnd > 0) C.Text else C.Muted)
                }
                if (restEnd > 0) Meter(1f - (restEnd - now).coerceAtLeast(0) / restTotal.toFloat(), color = C.Blue)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(90, 120, 180).forEach { s -> PillButton("${s / 60}:${(s % 60).toString().padStart(2, '0')}") { startRest(s * 1000L) } }
                    if (restEnd > 0) PillButton("Stop") { restEnd = 0 }
                }
            }
        }
        items(workout.exercises, key = { it.id }) { ex ->
            ExerciseCard(plan, ex, b, wk, lg, t, logs, isOpen = open == ex.id,
                onToggle = { expanded = if (open == ex.id) "" else ex.id },
                onEdit = { editing = ex },
                onSetDone = { compound -> startRest(if (compound) 150_000L else 105_000L) })
        }
        item { AddButton("Add Exercise", Modifier.padding(bottom = 24.dp)) { adding = true } }
    }

    val target = editing
    if (target != null || adding) {
        ExerciseDialog(target, workoutName = workout.name, onDismiss = { editing = null; adding = false },
            onSave = { ex ->
                Repo.changePlan((if (target == null) "Added " else "Edited ") + ex.name) { p ->
                    val wo = p.workouts[wk] ?: return@changePlan p
                    val list = if (target == null) wo.exercises + ex else wo.exercises.map { if (it.id == ex.id) ex else it }
                    p.copy(workouts = p.workouts + (wk to wo.copy(exercises = list)))
                }
                editing = null; adding = false
            },
            onRemove = { id ->
                Repo.changePlan("Removed an exercise") { p ->
                    p.copy(workouts = p.workouts.mapValues { (_, wo) -> wo.copy(exercises = wo.exercises.filterNot { it.id == id }) })
                }
                editing = null
            })
    }
}

@Composable
private fun Stat2(label: String, value: String) {
    Row { Muted(label, Modifier.weight(1f)); Text(value, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun ExerciseCard(
    plan: Plan, ex: Exercise, b: Block?, wk: String, lg: DayLog, t: String, logs: Map<String, DayLog>,
    isOpen: Boolean, onToggle: () -> Unit, onEdit: () -> Unit, onSetDone: (Boolean) -> Unit,
) {
    val n = PlanLogic.setsFor(plan, ex, b)
    val last = PlanLogic.lastSession(logs, ex.id, t)
    val cur = lg.sets[ex.id] ?: emptyList()
    val rows = max(n, cur.size)
    val doneN = doneSets(lg, ex.id)
    val complete = doneN >= n
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(shape).background(C.Card)
        .border(if (isOpen && !complete) 1.5.dp else 1.dp, if (isOpen && !complete) C.Blue else C.Line, shape)) {
        Row(Modifier.fillMaxWidth().background(C.CardHi).clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(muscleColor(ex.muscle).copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                Icon(Ico.Dumbbell, null, tint = muscleColor(ex.muscle), modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ex.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (complete) C.Muted else C.Text, maxLines = 2)
                Text("$n × ${ex.reps} · ${ex.muscle}", fontSize = 14.sp, color = C.Muted)
            }
            Text("$doneN / $n", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (complete) C.Green else C.Text)
            Spacer(Modifier.width(8.dp))
            Icon(if (isOpen) Ico.Up else Ico.Down, null, tint = C.BlueSoft, modifier = Modifier.size(22.dp))
        }
        if (isOpen) Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (ex.note.isNotBlank()) Row(verticalAlignment = Alignment.Top) {
                Icon(Ico.Note, null, tint = C.Muted, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Muted(ex.note)
            }
            last?.let { (d, s) -> Muted("Last time (${PlanLogic.fmtD(d)}): " + s.joinToString(", ") { "${fmtN(it.w)}×${fmtN(it.r)}" }) }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.BlueTint).padding(12.dp), verticalAlignment = Alignment.Top) {
                Icon(Ico.Sparkle, null, tint = C.BlueSoft, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(PlanLogic.suggestion(ex, b, wk, last), color = C.Text, fontSize = 15.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SET", color = C.Muted, fontSize = 12.sp, modifier = Modifier.width(36.dp))
                Text("LB", color = C.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("REPS", color = C.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.width(44.dp))
            }
            for (i in 0 until rows) {
                val v = cur.getOrNull(i) ?: SetEntry()
                val ph = last?.second?.getOrNull(i)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${i + 1}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.Muted, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
                    NumField("lb", v.w, "w${ex.id}$i$t", Modifier.weight(1f), hint = ph?.w?.let { fmtN(it) }) { x -> setSet(t, wk, ex.id, i) { it.copy(w = x) } }
                    NumField("reps", v.r, "r${ex.id}$i$t", Modifier.weight(1f), hint = ph?.r?.let { fmtN(it) }) { x -> setSet(t, wk, ex.id, i) { it.copy(r = x) } }
                    CheckCircle(v.ok == true, 36.dp) {
                        val on = v.ok != true
                        setSet(t, wk, ex.id, i) { it.copy(ok = on, w = it.w ?: ph?.w, r = it.r ?: ph?.r) }
                        if (on) onSetDone(ex.compound)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AddButton("Add Set", Modifier.weight(1f)) { setSet(t, wk, ex.id, rows) { it } }
                TextButton(onClick = onEdit) { Text("Edit", color = C.Muted) }
            }
        }
    }
}

@Composable
private fun ExerciseDialog(ex: Exercise?, workoutName: String, onDismiss: () -> Unit, onSave: (Exercise) -> Unit, onRemove: (String) -> Unit) {
    var name by remember { mutableStateOf(ex?.name ?: "") }
    var sets by remember { mutableStateOf((ex?.sets ?: 3).toString()) }
    var reps by remember { mutableStateOf(ex?.reps ?: "8–12") }
    var muscle by remember { mutableStateOf(ex?.muscle ?: "chest") }
    var compound by remember { mutableStateOf(ex?.compound ?: false) }
    var note by remember { mutableStateOf(ex?.note ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.Card,
        title = { Text(if (ex == null) "Add exercise" else "Edit exercise", color = C.Text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(name, { name = it }, "Name")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(sets, { sets = it }, "Full sets", Modifier.weight(1f), number = true)
                    Field(reps, { reps = it }, "Reps", Modifier.weight(1f))
                }
                Muted("Muscle")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DefaultPlan.MUSCLES.forEach { m -> Chip(m, muscle == m) { muscle = m } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Compound", compound) { compound = true }
                    Chip("Isolation", !compound) { compound = false }
                }
                Field(note, { note = it }, "Note")
                if (ex != null) TextButton(onClick = { onRemove(ex.id) }) { Text("Remove from $workoutName", color = C.Red) }
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank(), onClick = {
                onSave(Exercise(ex?.id ?: ("ex" + System.currentTimeMillis().toString(36)), name.trim(), (sets.toIntOrNull() ?: 3).coerceAtLeast(1),
                    reps.ifBlank { "8–12" }, muscle, compound, note.trim()))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
