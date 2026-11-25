@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bulkplan.tracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bulkplan.tracker.LocalToast
import com.bulkplan.tracker.data.DayLog
import com.bulkplan.tracker.data.Exercise
import com.bulkplan.tracker.data.Plan
import com.bulkplan.tracker.data.Repo
import com.bulkplan.tracker.data.SetEntry
import com.bulkplan.tracker.logic.PlanLogic
import com.bulkplan.tracker.logic.PlanLogic.fmtN

/** Every exercise in the plan by id, so logged sets can show a name. */
private fun exerciseIndex(plan: Plan): Map<String, Exercise> = plan.workouts.values.flatMap { it.exercises }.associateBy { it.id }

private fun exName(plan: Plan, id: String) = exerciseIndex(plan)[id]?.name ?: id.replace('-', ' ').replaceFirstChar { it.uppercase() }

/** Days with any logged set, newest first. */
fun sessionDays(logs: Map<String, DayLog>): List<String> =
    logs.values.filter { l -> l.sets.values.any { list -> list.any { it?.w != null || it?.r != null } } }.map { it.date }.sortedDescending()

private fun workoutLabel(plan: Plan, l: DayLog): String =
    l.workout?.let { plan.workouts[it]?.name } ?: PlanLogic.workoutForDay(plan, PlanLogic.dow(l.date))?.let { plan.workouts[it]?.name } ?: "Workout"

/** Past workouts. Tap one to fix weights and reps, delete sets, or delete the whole session. */
@Composable
fun WorkoutHistoryScreen(onClose: () -> Unit) {
    val state by Repo.state.collectAsStateWithLifecycle()
    val logs by Repo.logs.collectAsStateWithLifecycle()
    val plan = state.plan
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<String?>(null) }
    BackHandler { if (open != null) open = null else onClose() }

    val day = open
    if (day != null) { SessionEditor(day, onBack = { open = null }); return }

    val days = sessionDays(logs)
    Scaffold(
        containerColor = C.Bg,
        topBar = {
            TopAppBar(
                title = { Text("Workout history") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = C.Bg),
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp)) {
            item {
                Muted("Tap a session to fix a weight or rep count, delete a set, or delete the whole workout.", Modifier.padding(bottom = 12.dp))
                AddButton("Log a past workout", Modifier.padding(bottom = 16.dp)) { picking = true }
            }
            if (days.isEmpty()) item { Muted("No workouts logged yet. Sets you log on the Workout tab show up here.") }
            items(days, key = { it }) { d ->
                val l = logs[d] ?: return@items
                val logged = l.sets.filterValues { list -> list.any { it?.w != null || it?.r != null } }
                val sets = logged.values.sumOf { list -> list.count { it?.w != null || it?.r != null } }
                val volume = logged.values.flatten().sumOf { (it?.w ?: 0.0) * (it?.r ?: 0.0) }
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(16.dp)).background(C.Card)
                        .border(1.dp, C.Line, RoundedCornerShape(16.dp)).clickable { open = d }.padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ico.Barbell, null, tint = androidx.compose.ui.graphics.Color.Unspecified, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(workoutLabel(plan, l), style = H3)
                            Muted(PlanLogic.fmtD(d) + (if (d == PlanLogic.today()) " · today" else ""))
                        }
                        Icon(Ico.Edit, "Edit", tint = C.Muted, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("${logged.size} exercises · $sets sets · ${fmtN(Math.rint(volume))} lb volume", color = C.Text, fontSize = 14.sp)
                    val top = logged.entries.take(3).joinToString(" · ") { (id, list) ->
                        val best = list.filterNotNull().filter { it.w != null && it.r != null }.maxByOrNull { PlanLogic.e1rm(it.w!!, it.r!!) }
                        exName(plan, id) + (best?.let { " ${fmtN(it.w)}×${fmtN(it.r)}" } ?: "")
                    }
                    Muted(top + if (logged.size > 3) " …" else "")
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (picking) DateDialog(PlanLogic.addD(PlanLogic.today(), -1), onDismiss = { picking = false }) { d -> picking = false; pickedDate = d }
    pickedDate?.let { d ->
        AlertDialog(
            onDismissRequest = { pickedDate = null },
            containerColor = C.Card,
            title = { Text("Which workout on ${PlanLogic.fmtD(d)}?", color = C.Text) },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    plan.workouts.forEach { (k, w) ->
                        Chip(w.name, false) {
                            Repo.updateLog(d) { it.copy(workout = k) }
                            pickedDate = null; open = d
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickedDate = null }) { Text("Cancel") } },
        )
    }
}

/** One day's sets, all editable. */
@Composable
private fun SessionEditor(date: String, onBack: () -> Unit) {
    val state by Repo.state.collectAsStateWithLifecycle()
    val logs by Repo.logs.collectAsStateWithLifecycle()
    val plan = state.plan
    val toast = LocalToast.current
    val l = logs[date] ?: DayLog(date)
    val wk = l.workout ?: PlanLogic.workoutForDay(plan, PlanLogic.dow(date))
    val planned = wk?.let { plan.workouts[it]?.exercises?.map { e -> e.id } }.orEmpty()
    val ids = (planned + l.sets.keys.filter { k -> l.sets[k].orEmpty().isNotEmpty() }).distinct()
    var confirmDelete by remember { mutableStateOf(false) }
    var addingEx by remember { mutableStateOf(false) }

    fun edit(exId: String, f: (MutableList<SetEntry?>) -> Unit) = Repo.updateLog(date) { lg ->
        val list = lg.sets[exId].orEmpty().toMutableList()
        f(list)
        val clean = list.dropLastWhile { it == null }
        lg.copy(sets = if (clean.isEmpty()) lg.sets - exId else lg.sets + (exId to clean), workout = lg.workout ?: wk)
    }

    Scaffold(
        containerColor = C.Bg,
        topBar = {
            TopAppBar(
                title = { Column { Text(wk?.let { plan.workouts[it]?.name } ?: "Workout"); Text(PlanLogic.fmtD(date), fontSize = 13.sp, color = C.Muted) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = C.Bg),
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp)) {
            item {
                Muted("Changes save as you type. Tap × to delete a set.", Modifier.padding(bottom = 10.dp))
                if (plan.workouts.size > 1) {
                    Muted("Workout")
                    FlowRow(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        plan.workouts.forEach { (k, w) -> Chip(w.name, k == wk) { Repo.updateLog(date) { it.copy(workout = k) } } }
                    }
                }
            }
            items(ids, key = { it }) { id ->
                val list = l.sets[id].orEmpty()
                Section(exName(plan, id)) {
                    Row {
                        Text("SET", color = C.Muted, fontSize = 12.sp, modifier = Modifier.width(40.dp))
                        Text("LB", color = C.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Text("REPS", color = C.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(40.dp))
                    }
                    list.forEachIndexed { i, s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", color = C.Text, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                            NumField("lb", s?.w, "hw$date$id$i${list.size}", Modifier.weight(1f)) { v ->
                                edit(id) { it[i] = (it.getOrNull(i) ?: SetEntry()).copy(w = v, ok = if (v != null) true else it.getOrNull(i)?.ok) }
                            }
                            Spacer(Modifier.width(8.dp))
                            NumField("reps", s?.r, "hr$date$id$i${list.size}", Modifier.weight(1f)) { v ->
                                edit(id) { it[i] = (it.getOrNull(i) ?: SetEntry()).copy(r = v, ok = if (v != null) true else it.getOrNull(i)?.ok) }
                            }
                            IconButton(onClick = { edit(id) { it.removeAt(i) } }) { Icon(Ico.Close, "Delete set", tint = C.Muted, modifier = Modifier.size(20.dp)) }
                        }
                    }
                    if (list.isEmpty()) Muted("No sets logged.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("Add set", Ico.Plus) {
                            edit(id) { it.add((it.lastOrNull { s -> s?.w != null } ?: SetEntry()).copy(r = null, ok = null)) }
                        }
                        if (list.isNotEmpty()) PillButton("Clear", Ico.Trash) { Repo.updateLog(date) { it.copy(sets = it.sets - id) } }
                    }
                }
            }
            item {
                AddButton("Add an exercise", Modifier.padding(bottom = 12.dp)) { addingEx = true }
                if (l.sets.isNotEmpty()) TextButton(onClick = { confirmDelete = true }) { Text("Delete this whole workout", color = C.Red) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (addingEx) AlertDialog(
        onDismissRequest = { addingEx = false },
        containerColor = C.Card,
        title = { Text("Add an exercise", color = C.Text) },
        text = {
            Column(Modifier.height(360.dp)) {
                LazyColumn {
                    plan.workouts.values.forEach { w ->
                        item { Text(w.name, style = H3, modifier = Modifier.padding(top = 8.dp)) }
                        items(w.exercises.filter { it.id !in ids }, key = { w.name + it.id }) { e ->
                            Text(e.name, color = C.Text, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().clickable {
                                edit(e.id) { it.add(SetEntry()) }; addingEx = false
                            }.padding(vertical = 10.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { addingEx = false }) { Text("Cancel") } },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        containerColor = C.Card,
        title = { Text("Delete this workout?", color = C.Text) },
        text = { Text("Removes every set logged on ${PlanLogic.fmtD(date)}. Your weight, food and other numbers for that day stay.", color = C.Text) },
        confirmButton = {
            Button(onClick = {
                Repo.updateLog(date) { it.copy(sets = emptyMap(), workout = null) }
                confirmDelete = false; toast("Workout deleted"); onBack()
            }) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}
