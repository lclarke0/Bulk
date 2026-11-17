@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bulkplan.tracker.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bulkplan.tracker.data.DayLog
import com.bulkplan.tracker.data.DefaultPlan
import com.bulkplan.tracker.data.Exercise
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
    val ctx = LocalContext.current

    // Rest timer
    var restEnd by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(restEnd) {
        while (restEnd > 0) {
            now = System.currentTimeMillis()
            if (now >= restEnd) { restEnd = 0; alarm(ctx); break }
            delay(250)
        }
    }
    val left = if (restEnd > 0) ((restEnd - now) / 1000).coerceAtLeast(0) else 0

    Column {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            keys.forEach { k ->
                val wo = plan.workouts.getValue(k)
                FilterChip(selected = k == wk, onClick = { wk = k }, label = { Text("${PlanLogic.DAYS[wo.day]} · ${wo.name}") })
            }
        }
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${left / 60}:${(left % 60).toString().padStart(2, '0')}", fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(72.dp))
                listOf(90, 120, 180).forEach { s ->
                    OutlinedButton(onClick = { restEnd = System.currentTimeMillis() + s * 1000L; now = System.currentTimeMillis() }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                        Text("${s / 60}:${(s % 60).toString().padStart(2, '0')}")
                    }
                }
                TextButton(onClick = { restEnd = 0 }) { Text("Stop") }
            }
        }
        if (workout == null) { Muted("No workouts in the plan.", Modifier.padding(16.dp)); return@Column }
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                Section(workout.name, action = { Tag(if (w < 1) "Preview of week 1" else "Week $w" + (b?.let { " · ${it.name}" } ?: "")) }) {
                    Text("${b?.note ?: ""} Stop ${b?.rir ?: "0–2"} reps short of failure. Rest 2–3 min on compound lifts, 1.5–2 min on isolation.", fontSize = 15.sp)
                    if (lg.workout != null && lg.workout != wk) Muted("You already logged ${plan.workouts[lg.workout]?.name ?: "another workout"} today; sets here also save to today.")
                }
            }
            items(workout.exercises, key = { it.id }) { ex ->
                val n = PlanLogic.setsFor(plan, ex, b)
                val last = PlanLogic.lastSession(logs, ex.id, t)
                val cur = lg.sets[ex.id] ?: emptyList()
                val rows = max(n, cur.size)
                Section(ex.name, action = { TextButton(onClick = { editing = ex }) { Text("Edit") } }) {
                    Text("$n × ${ex.reps}  ·  ${ex.muscle}" + if (ex.note.isNotBlank()) "  ·  ${ex.note}" else "", fontSize = 14.sp)
                    last?.let { (d, s) -> Muted("Last time (${PlanLogic.fmtD(d)}): " + s.joinToString(", ") { "${fmtN(it.w)}×${fmtN(it.r)}" }) }
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                        Text(PlanLogic.suggestion(ex, b, wk, last), modifier = Modifier.padding(10.dp), fontSize = 15.sp)
                    }
                    for (i in 0 until rows) {
                        val v = cur.getOrNull(i) ?: SetEntry()
                        val ph = last?.second?.getOrNull(i)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${i + 1}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(22.dp))
                            NumField("lb", v.w, "w${ex.id}$i$t", Modifier.weight(1f), hint = ph?.w?.let { fmtN(it) }) { x -> setSet(t, wk, ex.id, i) { it.copy(w = x) } }
                            NumField("reps", v.r, "r${ex.id}$i$t", Modifier.weight(1f), hint = ph?.r?.let { fmtN(it) }) { x -> setSet(t, wk, ex.id, i) { it.copy(r = x) } }
                            Checkbox(checked = v.ok == true, onCheckedChange = { on ->
                                setSet(t, wk, ex.id, i) { it.copy(ok = on, w = it.w ?: ph?.w) }
                                if (on) { restEnd = System.currentTimeMillis() + (if (ex.compound) 150_000L else 105_000L); now = System.currentTimeMillis() }
                            })
                        }
                    }
                    TextButton(onClick = { setSet(t, wk, ex.id, rows) { it } }) { Text("Add a set") }
                }
            }
            item { OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("Add exercise") } }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    val target = editing
    if (target != null || adding) {
        ExerciseDialog(target, workoutName = workout?.name ?: "", onDismiss = { editing = null; adding = false },
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
private fun ExerciseDialog(ex: Exercise?, workoutName: String, onDismiss: () -> Unit, onSave: (Exercise) -> Unit, onRemove: (String) -> Unit) {
    var name by remember { mutableStateOf(ex?.name ?: "") }
    var sets by remember { mutableStateOf((ex?.sets ?: 3).toString()) }
    var reps by remember { mutableStateOf(ex?.reps ?: "8–12") }
    var muscle by remember { mutableStateOf(ex?.muscle ?: "chest") }
    var compound by remember { mutableStateOf(ex?.compound ?: false) }
    var note by remember { mutableStateOf(ex?.note ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (ex == null) "Add exercise" else "Edit exercise") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(sets, { sets = it }, label = { Text("Full sets") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(reps, { reps = it }, label = { Text("Reps") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Muted("Muscle")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DefaultPlan.MUSCLES.forEach { m -> FilterChip(selected = muscle == m, onClick = { muscle = m }, label = { Text(m) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = compound, onClick = { compound = true }, label = { Text("Compound") })
                    FilterChip(selected = !compound, onClick = { compound = false }, label = { Text("Isolation") })
                }
                OutlinedTextField(note, { note = it }, label = { Text("Note") })
                if (ex != null) TextButton(onClick = { onRemove(ex.id) }) { Text("Remove from $workoutName") }
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
