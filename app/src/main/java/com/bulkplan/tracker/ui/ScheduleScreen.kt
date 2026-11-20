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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bulkplan.tracker.LocalToast
import com.bulkplan.tracker.data.Repo
import com.bulkplan.tracker.data.ScheduleItem
import com.bulkplan.tracker.logic.PlanLogic

private val KINDS = listOf("task" to "Task", "meal" to "Meal", "train" to "Training", "supp" to "Supplement", "skin" to "Skin care", "coach" to "Coach check-in", "sleep" to "Sleep")
private val WEEK = listOf(1, 2, 3, 4, 5, 6, 0)

fun daysText(days: List<Int>): String = when {
    days.toSet() == (0..6).toSet() -> "Every day"
    days.toSet() == (1..5).toSet() -> "Weekdays"
    days.toSet() == setOf(0, 6) -> "Weekends"
    else -> WEEK.filter { it in days }.joinToString(", ") { PlanLogic.DAYS[it] }
}

/** The whole weekly schedule, one day at a time, with every item editable. */
@Composable
fun ScheduleScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val state by Repo.state.collectAsStateWithLifecycle()
    val plan = state.plan
    val toast = LocalToast.current
    var day by rememberSaveable { mutableStateOf(PlanLogic.dow(PlanLogic.today())) }
    var editing by remember { mutableStateOf<ScheduleItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var shiftOpen by remember { mutableStateOf(false) }
    val items = plan.schedule.filter { day in it.days }.sortedBy { PlanLogic.mins(it.time) }
    val wkName = PlanLogic.workoutForDay(plan, day)?.let { plan.workouts[it]?.name }
    val hasTrain = items.any { it.kind == "train" }

    Scaffold(
        containerColor = C.Bg,
        topBar = {
            TopAppBar(
                title = { Text("Edit schedule") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = C.Bg),
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp)) {
            item {
                Muted("Tap anything to change its time, days or details. Changes apply every week and can be undone from Coach › More.")
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WEEK.forEach { d ->
                        val sel = d == day
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (sel) C.Blue else C.Card)
                                .border(1.dp, if (sel) C.Blue else C.Line, RoundedCornerShape(12.dp)).clickable { day = d }.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(PlanLogic.DAYS[d].take(2), color = if (sel) Color.White else C.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("${plan.schedule.count { d in it.days }}", color = if (sel) Color.White else C.Muted, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(PlanLogic.DAYN[day], style = H2)
                        Muted(if (wkName != null) "Workout: $wkName" else "Rest day")
                    }
                    PillButton("Shift times", Ico.Timer) { shiftOpen = true }
                }
                if (wkName != null && !hasTrain) Muted("$wkName is set for ${PlanLogic.DAYN[day]} but there’s no training time on this day. Add one with type Training.", Modifier.padding(top = 6.dp))
                if (wkName == null && hasTrain) Muted("There’s a training time here but no workout assigned to ${PlanLogic.DAYN[day]}. Pick one under Workout days below.", Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(12.dp))
            }
            items(items, key = { it.id }) { i ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(16.dp)).background(C.Card)
                        .border(1.dp, C.Line, RoundedCornerShape(16.dp)).clickable { editing = i }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val (t, ap) = PlanLogic.timeParts(i.time)
                    Column(Modifier.width(64.dp)) {
                        Text(t, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.Text)
                        Text(ap.uppercase(), fontSize = 12.sp, color = C.Muted)
                    }
                    Icon(iconFor(i), null, tint = Color.Unspecified, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (i.kind == "train" && wkName != null) "Train: $wkName" else i.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(daysText(i.days) + (if (i.kind in state.settings.remindOff) " · reminder off" else ""), fontSize = 13.sp, color = C.Muted, maxLines = 1)
                    }
                    Icon(Ico.Edit, "Edit", tint = C.Muted, modifier = Modifier.size(20.dp))
                }
            }
            if (items.isEmpty()) item { Muted("Nothing on ${PlanLogic.DAYN[day]} yet.", Modifier.padding(bottom = 12.dp)) }
            item { AddButton("Add to ${PlanLogic.DAYN[day]}", Modifier.padding(bottom = 20.dp)) { adding = true } }
            item {
                Section("Workout days", icon = Ico.Dumbbell) {
                    Muted("Which day each workout falls on. Each workout day also needs a Training time on the schedule.")
                    plan.workouts.forEach { (key, w) ->
                        Text(w.name, style = H3, modifier = Modifier.padding(top = 6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            WEEK.forEach { d ->
                                Chip(PlanLogic.DAYS[d], w.day == d) {
                                    if (w.day != d) {
                                        val other = plan.workouts.entries.firstOrNull { it.key != key && it.value.day == d }
                                        Repo.changePlan("Moved ${w.name} to ${PlanLogic.DAYN[d]}" + (other?.let { " (swapped with ${it.value.name})" } ?: "")) { p ->
                                            p.copy(workouts = p.workouts.mapValues { (k, v) ->
                                                when {
                                                    k == key -> v.copy(day = d)
                                                    other != null && k == other.key -> v.copy(day = w.day)
                                                    else -> v
                                                }
                                            }.let { LinkedHashMap(it) })
                                        }
                                        toast(if (other != null) "Swapped ${w.name} and ${other.value.name}" else "${w.name} moved to ${PlanLogic.DAYN[d]}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    val target = editing
    if (target != null || adding) TaskDialog(target, onDismiss = { editing = null; adding = false }, fromDay = day, presetDays = listOf(day))
    if (shiftOpen) ShiftDialog(day, onDismiss = { shiftOpen = false })
}

/** Moves every item on a day earlier or later, e.g. when work starts an hour later. */
@Composable
private fun ShiftDialog(day: Int, onDismiss: () -> Unit) {
    var mins by remember { mutableStateOf(30) }
    var later by remember { mutableStateOf(true) }
    var fromTime by remember { mutableStateOf("00:00") }
    var picking by remember { mutableStateOf(false) }
    val toast = LocalToast.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Card,
        title = { Text("Shift ${PlanLogic.DAYN[day]}", color = C.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Muted("Items shared with other days only move on ${PlanLogic.DAYN[day]}: they’re split into a copy for this day.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Earlier", !later) { later = false }; Chip("Later", later) { later = true }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 30, 45, 60, 90, 120).forEach { m -> Chip(if (m < 60) "$m min" else "${m / 60}${if (m % 60 == 30) ".5" else ""} h", mins == m) { mins = m } }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Only items from", color = C.Text, modifier = Modifier.weight(1f))
                    PillButton(PlanLogic.fmtT(fromTime), Ico.Timer) { picking = true }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val delta = if (later) mins else -mins
                val cut = PlanLogic.mins(fromTime)
                Repo.changePlan("Shifted ${PlanLogic.DAYN[day]} ${if (later) "later" else "earlier"} by $mins min") { p ->
                    val out = mutableListOf<ScheduleItem>()
                    p.schedule.forEach { i ->
                        if (day !in i.days || PlanLogic.mins(i.time) < cut) { out += i; return@forEach }
                        val m = (PlanLogic.mins(i.time) + delta).coerceIn(0, 23 * 60 + 59)
                        val t = "%02d:%02d".format(m / 60, m % 60)
                        if (i.days.size == 1) out += i.copy(time = t)
                        else {
                            out += i.copy(days = i.days - day)
                            out += i.copy(id = i.id + "-" + PlanLogic.DAYS[day].lowercase(), time = t, days = listOf(day))
                        }
                    }
                    p.copy(schedule = out)
                }
                toast("Moved ${PlanLogic.DAYN[day]}")
                onDismiss()
            }) { Text("Shift") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (picking) TimeDialog(fromTime, onDismiss = { picking = false }) { fromTime = it; picking = false }
}

/** Clock-face time picker that returns "HH:MM". */
@Composable
fun TimeDialog(time: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val m = runCatching { PlanLogic.mins(time) }.getOrDefault(12 * 60)
    val st = rememberTimePickerState(initialHour = m / 60, initialMinute = m % 60, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Card,
        title = { Text("Pick a time", color = C.Text) },
        text = {
            TimePicker(st, colors = TimePickerDefaults.colors(
                clockDialColor = C.Inner, selectorColor = C.Blue, containerColor = C.Card,
                timeSelectorSelectedContainerColor = C.Blue, timeSelectorUnselectedContainerColor = C.Inner,
                timeSelectorSelectedContentColor = Color.White, timeSelectorUnselectedContentColor = C.Text,
                periodSelectorSelectedContainerColor = C.Blue, periodSelectorUnselectedContainerColor = C.Inner,
                periodSelectorSelectedContentColor = Color.White, periodSelectorUnselectedContentColor = C.Text,
                clockDialUnselectedContentColor = C.Text,
            ))
        },
        confirmButton = { Button(onClick = { onPick("%02d:%02d".format(st.hour, st.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Add a new schedule item, or edit/remove an existing one. Changes are saved to the plan and can be undone in Coach.
 * [fromDay] is the day you opened it from, so an item can be removed from just that day.
 */
@Composable
fun TaskDialog(item: ScheduleItem?, onDismiss: () -> Unit, fromDay: Int? = null, presetDays: List<Int>? = null) {
    var title by remember { mutableStateOf(item?.title ?: "") }
    var time by remember { mutableStateOf(item?.time ?: "12:00") }
    var detail by remember { mutableStateOf(item?.detail ?: "") }
    var kind by remember { mutableStateOf(item?.kind ?: "task") }
    var kcal by remember { mutableStateOf(item?.kcal?.toString() ?: "") }
    var protein by remember { mutableStateOf(item?.protein?.toString() ?: "") }
    var dur by remember { mutableStateOf((item?.dur ?: 90).toString()) }
    var picking by remember { mutableStateOf(false) }
    val days = remember { mutableStateListOf<Int>().apply { addAll(item?.days ?: presetDays ?: listOf(0, 1, 2, 3, 4, 5, 6)) } }
    val shared = item != null && fromDay != null && item.days.size > 1 && fromDay in item.days
    var onlyThisDay by remember { mutableStateOf(false) }
    val toast = LocalToast.current

    fun build(id: String, ds: List<Int>) = ScheduleItem(
        id = id, time = time, kind = kind, title = title.trim(), detail = detail.trim(), days = ds.sorted(),
        kcal = if (kind == "meal") kcal.toIntOrNull() else null, protein = if (kind == "meal") protein.toIntOrNull() else null,
        dur = if (kind == "train") dur.toIntOrNull()?.coerceIn(10, 300) ?: 90 else item?.dur,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Card,
        title = { Text(if (item == null) "Add to schedule" else "Edit ${item.title}", color = C.Text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(title, { title = it }, "Title", Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Time", color = C.Text, modifier = Modifier.weight(1f))
                    PillButton(PlanLogic.fmtT(time), Ico.Timer) { picking = true }
                }
                Field(detail, { detail = it }, "Details", Modifier.fillMaxWidth(), singleLine = false, minLines = 2)
                Muted("Type")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KINDS.forEach { (k, l) -> Chip(l, kind == k) { kind = k } }
                }
                if (kind == "meal") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(kcal, { kcal = it }, "kcal", Modifier.weight(1f), number = true)
                    Field(protein, { protein = it }, "Protein g", Modifier.weight(1f), number = true)
                }
                if (kind == "train") Field(dur, { dur = it }, "Length (minutes)", Modifier.fillMaxWidth(), number = true)
                if (shared) {
                    Muted("This is on ${daysText(item!!.days)}.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("Change every day", !onlyThisDay) { onlyThisDay = false }
                        Chip("Only ${PlanLogic.DAYN[fromDay!!]}", onlyThisDay) { onlyThisDay = true }
                    }
                }
                if (!onlyThisDay) {
                    Muted("Days")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WEEK.forEach { d -> Chip(PlanLogic.DAYS[d], d in days) { if (d in days) days.remove(d) else days.add(d) } }
                    }
                }
                if (item != null) {
                    if (shared) TextButton(onClick = {
                        Repo.changePlan("Removed ${item.title} on ${PlanLogic.DAYN[fromDay!!]}") { p ->
                            p.copy(schedule = p.schedule.map { if (it.id == item.id) it.copy(days = it.days - fromDay) else it })
                        }
                        toast("Removed from ${PlanLogic.DAYN[fromDay!!]}")
                        onDismiss()
                    }) { Text("Remove from ${PlanLogic.DAYN[fromDay!!]} only", color = C.Red) }
                    TextButton(onClick = {
                        Repo.changePlan("Removed ${item.title} at ${PlanLogic.fmtT(item.time)}") { p -> p.copy(schedule = p.schedule.filterNot { it.id == item.id }) }
                        toast("Removed. Undo from Coach › More.")
                        onDismiss()
                    }) { Text(if (shared) "Remove from every day" else "Remove from schedule", color = C.Red) }
                }
            }
        },
        confirmButton = {
            Button(enabled = title.isNotBlank() && (onlyThisDay || days.isNotEmpty()), onClick = {
                if (item != null && onlyThisDay && fromDay != null) {
                    // Split off a copy for this one day, leave the rest as they were.
                    val copyId = item.id + "-" + PlanLogic.DAYS[fromDay].lowercase()
                    Repo.changePlan("Changed ${title.trim()} on ${PlanLogic.DAYN[fromDay]}") { p ->
                        val id = if (p.schedule.any { it.id == copyId }) copyId + System.currentTimeMillis().toString(36).takeLast(4) else copyId
                        p.copy(schedule = p.schedule.map { if (it.id == item.id) it.copy(days = it.days - fromDay) else it } + build(id, listOf(fromDay)))
                    }
                } else {
                    val new = build(item?.id ?: ("t" + System.currentTimeMillis().toString(36)), days.toList())
                    Repo.changePlan((if (item == null) "Added " else "Edited ") + new.title) { p ->
                        p.copy(schedule = if (item == null) p.schedule + new else p.schedule.map { if (it.id == item.id) new else it })
                    }
                }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (picking) TimeDialog(time, onDismiss = { picking = false }) { time = it; picking = false }
}
