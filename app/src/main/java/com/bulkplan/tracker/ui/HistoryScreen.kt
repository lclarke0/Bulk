@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bulkplan.tracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
fun WorkoutHistoryScreen(onClose: () -> Unit, onGarmin: () -> Unit = {}) {
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
                AddButton("Log a past workout", Modifier.padding(bottom = 10.dp)) { picking = true }
                if (state.settings.garminKey.isNotBlank()) AddButton("Import reps from Garmin", Modifier.padding(bottom = 16.dp), icon = Ico.Sync) { onGarmin() }
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
                    Text("${logged.size} exercise${if (logged.size == 1) "" else "s"} · $sets set${if (sets == 1) "" else "s"} · ${fmtN(Math.rint(volume))} lb volume", color = C.Text, fontSize = 14.sp)
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

/** Pulls the sets and reps your watch counted in a Strength activity into the workout log. */
@Composable
fun GarminRepsScreen(onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val state by Repo.state.collectAsStateWithLifecycle()
    val plan = state.plan
    val toast = LocalToast.current
    var acts by remember { mutableStateOf<List<com.bulkplan.tracker.net.Garmin.StrengthActivity>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<com.bulkplan.tracker.net.Garmin.StrengthActivity?>(null) }
    var sets by remember { mutableStateOf<List<com.bulkplan.tracker.net.Fit.FitSet>?>(null) }
    var reload by remember { mutableStateOf(0) }
    BackHandler { if (picked != null) { picked = null; sets = null } else onClose() }

    androidx.compose.runtime.LaunchedEffect(reload) {
        error = null
        if (state.settings.garminKey.isBlank()) { error = "Add your Garmin Personal API key in Settings › Garmin first."; acts = emptyList(); return@LaunchedEffect }
        try { acts = com.bulkplan.tracker.net.Garmin.strengthActivities(ctx) } catch (e: Exception) { error = e.message; acts = emptyList() }
    }
    androidx.compose.runtime.LaunchedEffect(picked) {
        val a = picked ?: return@LaunchedEffect
        sets = null; error = null
        try { sets = com.bulkplan.tracker.net.Garmin.strengthSets(ctx, a.id) } catch (e: Exception) { error = e.message; picked = null }
    }

    Scaffold(
        containerColor = C.Bg,
        topBar = {
            TopAppBar(
                title = { Text(picked?.let { "Sets from ${PlanLogic.fmtD(it.date)}" } ?: "Reps from Garmin") },
                navigationIcon = { IconButton(onClick = { if (picked != null) { picked = null; sets = null } else onClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = C.Bg),
            )
        },
    ) { pad ->
        val a = picked
        val s = sets
        if (a != null && s != null) {
            Box(Modifier.fillMaxSize().padding(pad)) { SetAssigner(a, s, plan, onSaved = { d -> toast("Saved to ${PlanLogic.fmtD(d)}. Fix anything in History."); onClose() }) }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp)) {
            item {
                Section("How to record reps", icon = Ico.Barbell) {
                    Muted("On the watch: start a Strength activity with rep counting on, do your set, then press the button to end the set and start the rest timer. Sync to Garmin Connect when you’re done, then pick the workout below.")
                    Muted("Garmin says leg exercises may not be counted, so you may need to type those reps in. Add the weights here too, since the watch doesn’t know them unless you enter them on it.")
                }
            }
            error?.let { item { Text(it, color = C.Red, fontSize = 15.sp, modifier = Modifier.padding(bottom = 12.dp)) } }
            val list = acts
            if (list == null || (picked != null && sets == null)) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp)); Muted(if (picked != null) "Downloading the workout…" else "Looking for strength workouts…")
                }
            } else {
                if (list.isEmpty() && error == null) item { Muted("No Strength activities in the last 3 weeks. Record one on the watch, sync it, then tap Refresh.") }
                items(list, key = { it.id }) { act ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(16.dp)).background(C.Card)
                            .border(1.dp, C.Line, RoundedCornerShape(16.dp)).clickable { picked = act }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Ico.Barbell, null, tint = androidx.compose.ui.graphics.Color.Unspecified, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(act.name, style = H3)
                            Muted(PlanLogic.fmtD(act.date) + (act.time.takeIf { it.isNotBlank() }?.let { " · " + PlanLogic.fmtT(it) } ?: "") +
                                (if (Repo.logs.value[act.date]?.sets?.isNotEmpty() == true) " · already has sets logged" else ""))
                        }
                        Icon(Ico.Right, null, tint = C.Muted, modifier = Modifier.size(20.dp))
                    }
                }
                item { PillButton("Refresh", Ico.Sync) { acts = null; reload++ } }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SetAssigner(act: com.bulkplan.tracker.net.Garmin.StrengthActivity, all: List<com.bulkplan.tracker.net.Fit.FitSet>, plan: Plan, onSaved: (String) -> Unit) {
    val logs by Repo.logs.collectAsStateWithLifecycle()
    val date = act.date
    val sets = all.filter { it.active && (it.reps ?: 0) > 0 || it.active && it.reps == null && (it.durationS ?: 0.0) > 5 }
    val dayLog = logs[date]
    var wk by remember { mutableStateOf(dayLog?.workout ?: PlanLogic.workoutForDay(plan, PlanLogic.dow(date)) ?: plan.workouts.keys.firstOrNull() ?: "") }
    val exercises = plan.workouts[wk]?.exercises.orEmpty()
    val block = PlanLogic.blockFor(plan, maxOf(1, PlanLogic.weekOf(plan, date)))
    val assigned = remember(wk) {
        androidx.compose.runtime.mutableStateListOf<String?>().apply {
            addAll(com.bulkplan.tracker.net.Fit.assign(sets, exercises.map { it.id to it.name }, exercises.map { PlanLogic.setsFor(plan, it, block) }))
        }
    }
    val reps = remember { androidx.compose.runtime.mutableStateListOf<Double?>().apply { addAll(sets.map { it.reps?.toDouble() }) } }
    val weights = remember(wk) {
        val seen = HashMap<String, Int>()
        androidx.compose.runtime.mutableStateListOf<Double?>().apply {
            sets.forEachIndexed { i, s ->
                val id = assigned.getOrNull(i)
                val n = id?.let { seen.merge(it, 1, Int::plus)!! - 1 } ?: 0
                add(
                    id?.let { dayLog?.sets?.get(it)?.getOrNull(n)?.w }
                        ?: id?.let { PlanLogic.lastSession(logs, it, date)?.second?.mapNotNull { e -> e.w }?.maxOrNull() }
                        ?: s.weightKg?.let { Math.rint(it * 2.20462 / 2.5) * 2.5 }
                )
            }
        }
    }
    var choosing by remember { mutableStateOf<Int?>(null) }
    val names = exercises.associate { it.id to it.name }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Muted("${sets.size} set${if (sets.size == 1) "" else "s"} counted by your watch. Check which exercise each one was, add weights, then save.", Modifier.padding(bottom = 10.dp))
            FlowRow(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                plan.workouts.forEach { (k, w) -> Chip(w.name, k == wk) { wk = k } }
            }
        }
        items(sets.size) { i ->
            val s = sets[i]
            Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(16.dp)).background(C.Card).border(1.dp, C.Line, RoundedCornerShape(16.dp)).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Set ${i + 1}", style = H3, modifier = Modifier.weight(1f))
                    s.durationS?.let { d -> Muted("${(d / 60).toInt()}:${(d % 60).toInt().toString().padStart(2, '0')}") }
                }
                Spacer(Modifier.height(6.dp))
                PillButton(assigned.getOrNull(i)?.let { names[it] } ?: "Skip this set", Ico.Dumbbell, selected = assigned.getOrNull(i) != null) { choosing = i }
                Spacer(Modifier.height(8.dp))
                Row {
                    NumField("lb", weights.getOrNull(i), "gw$wk$i", Modifier.weight(1f)) { v -> weights[i] = v }
                    Spacer(Modifier.width(8.dp))
                    NumField("reps", reps.getOrNull(i), "gr$i", Modifier.weight(1f)) { v -> reps[i] = v }
                }
            }
        }
        item {
            val ready = assigned.count { it != null }
            Button(enabled = ready > 0, modifier = Modifier.fillMaxWidth(), onClick = {
                val grouped = LinkedHashMap<String, MutableList<SetEntry?>>()
                sets.indices.forEach { i ->
                    val id = assigned.getOrNull(i) ?: return@forEach
                    grouped.getOrPut(id) { mutableListOf() } += SetEntry(weights.getOrNull(i), reps.getOrNull(i), true)
                }
                Repo.updateLog(date) { it.copy(sets = it.sets + grouped, workout = wk) }
                onSaved(date)
            }) { Text("Save $ready set${if (ready == 1) "" else "s"} to ${PlanLogic.fmtD(date)}") }
            Muted("Exercises you save here replace what was logged for them that day; other exercises stay.", Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(24.dp))
        }
    }
    choosing?.let { i ->
        AlertDialog(
            onDismissRequest = { choosing = null },
            containerColor = C.Card,
            title = { Text("Set ${i + 1} was…", color = C.Text) },
            text = {
                Column {
                    exercises.forEach { e ->
                        Text(e.name, color = if (assigned.getOrNull(i) == e.id) C.BlueSoft else C.Text, fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth().clickable { assigned[i] = e.id; choosing = null }.padding(vertical = 10.dp))
                    }
                    Text("Skip this set", color = C.Muted, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable { assigned[i] = null; choosing = null }.padding(vertical = 10.dp))
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = null }) { Text("Cancel") } },
        )
    }
}
