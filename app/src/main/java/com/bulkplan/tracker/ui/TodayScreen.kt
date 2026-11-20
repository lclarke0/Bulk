@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bulkplan.tracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bulkplan.tracker.LocalToast
import com.bulkplan.tracker.data.DayLog
import com.bulkplan.tracker.data.Repo
import com.bulkplan.tracker.data.ScheduleItem
import com.bulkplan.tracker.logic.Actions
import com.bulkplan.tracker.logic.PlanLogic
import com.bulkplan.tracker.logic.PlanLogic.fmtN
import com.bulkplan.tracker.net.Garmin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Icon for a schedule item: meals by time of day, then by kind. */
fun iconFor(i: ScheduleItem): ImageVector = when (i.kind) {
    "meal" -> when (Actions.slotFor(i.time)) {
        "Breakfast" -> Ico.Sunrise; "Lunch" -> Ico.Sun; "Dinner" -> Ico.Moon; else -> Ico.Cookie
    }
    "train" -> Ico.Barbell
    "supp" -> Ico.Pill
    "skin" -> Ico.SkinDrop
    "sleep" -> Ico.Moon
    "coach" -> Ico.Coach
    else -> Ico.TaskIcon
}

private val FILTERS = listOf("All" to null, "Meals" to "meal", "Training" to "train", "Supplements" to "supp", "Skin care" to "skin", "Other" to "other")

private fun endTime(i: ScheduleItem): String {
    val m = PlanLogic.mins(i.time) + (i.dur ?: if (i.kind == "meal") 30 else 15)
    return "%02d:%02d".format((m / 60) % 24, m % 60)
}

@Composable
fun TodayScreen(onGo: (String) -> Unit, onEditSchedule: () -> Unit = {}) {
    val state by Repo.state.collectAsStateWithLifecycle()
    val logs by Repo.logs.collectAsStateWithLifecycle()
    var nowM by remember { mutableIntStateOf(PlanLogic.nowMins()) }
    var t by remember { mutableStateOf(PlanLogic.today()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); nowM = PlanLogic.nowMins(); t = PlanLogic.today() } }
    val plan = state.plan
    val lg = logs[t] ?: DayLog(t)
    val all = PlanLogic.itemsFor(plan, t)
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var showFilter by rememberSaveable { mutableStateOf(false) }
    val items = all.filter { i -> filter == null || (filter == "other" && i.kind !in listOf("meal", "train", "supp", "skin")) || i.kind == filter }
    val done = all.count { lg.checks[it.id] == true }
    val current = all.lastOrNull { PlanLogic.mins(it.time) <= nowM && lg.checks[it.id] != true && nowM - PlanLogic.mins(it.time) <= 90 }
        ?: all.firstOrNull { PlanLogic.mins(it.time) > nowM && lg.checks[it.id] != true }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val open = expanded ?: current?.id
    var editing by remember { mutableStateOf<ScheduleItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    val totals = PlanLogic.foodTotals(lg)
    val pTarget = PlanLogic.proteinTarget(plan, logs)
    val w = PlanLogic.weekOf(plan, t)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Section {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ring(if (all.isEmpty()) 0f else done / all.size.toFloat(), 84.dp, color = C.Blue, stroke = 9.dp) {
                        Text("$done", color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("of ${all.size}", color = C.Muted, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MacroBar(Ico.Flame, "Calories", C.Green, totals.kcal, plan.targets.calories.toDouble(), "")
                        MacroBar(Ico.Arm, "Protein", C.BlueSoft, totals.protein, pTarget.toDouble())
                    }
                }
            }
        }
        if (state.settings.intakeStatus == "pending") item {
            Section("Waiting for your custom plan", icon = Ico.Sparkle, highlight = true) {
                Muted("Send your plan request to any AI, then import its reply. You’re on the starter plan until then.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Import plan", Ico.Sparkle, selected = true) { onGo("import") }
                    PillButton("Get the request", Ico.Clipboard) { onGo("intake-end") }
                }
            }
        }
        if (w < 1) item {
            val prep = PlanLogic.prepFor(plan, state.settings)
            val dn = prep.count { state.prepDone[it.id] == true }
            Section("Before week 1", icon = Ico.TaskIcon, action = { Tag("$dn/${prep.size}") }) {
                Muted("Your plan starts ${PlanLogic.fmtD(plan.startDate)}. Change the date in Settings.")
                prep.forEach { p ->
                    val on = state.prepDone[p.id] == true
                    Row(Modifier.fillMaxWidth().clickable { Repo.update { it.copy(prepDone = it.prepDone + (p.id to !on)) } }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(p.text, modifier = Modifier.weight(1f), fontSize = 15.sp, color = if (on) C.Muted else C.Text)
                        Spacer(Modifier.width(10.dp))
                        CheckCircle(on) { Repo.update { it.copy(prepDone = it.prepDone + (p.id to !on)) } }
                    }
                }
            }
        }
        PlanLogic.milestonesFor(plan, state.settings).filter { it.week == w }.takeIf { it.isNotEmpty() }?.let { ms ->
            item { Section(highlight = true) { Tag("This week"); Text(ms.joinToString(" · ") { it.text }, style = H3) } }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(PlanLogic.DAYN[PlanLogic.dow(t)], style = H2, modifier = Modifier.weight(1f))
                PillButton("Edit", Ico.Edit, onClick = onEditSchedule)
                Spacer(Modifier.width(8.dp))
                PillButton(FILTERS.first { it.second == filter }.first.takeIf { filter != null } ?: "Filter", Ico.Filter, selected = filter != null) { showFilter = !showFilter }
            }
            if (showFilter) FlowRow(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FILTERS.forEach { (label, k) -> Chip(label, filter == k) { filter = k; showFilter = false } }
            }
        }
        items(items, key = { it.id }) { i ->
            ScheduleCard(i, done = lg.checks[i.id] == true, isCurrent = current?.id == i.id, open = open == i.id, lg = lg,
                onToggleOpen = { expanded = if (open == i.id) "" else i.id },
                onDone = { Actions.setChecked(t, i, lg.checks[i.id] != true) },
                onGo = onGo, onEdit = { editing = i })
        }
        item { AddButton("Add Task", Modifier.padding(bottom = 16.dp)) { adding = true } }
        item {
            Section("Today’s numbers", icon = Ico.Scale) {
                val g = lg.garmin
                NumField("Morning weight (lb)", lg.weight, "w$t", Modifier.fillMaxWidth(), hint = g?.weight?.let { "Garmin: ${fmtN(it)}" }) { v -> Repo.updateLog(t) { it.copy(weight = v) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Sleep (h)", lg.sleep, "s$t", Modifier.weight(1f), hint = g?.sleepH?.let { "Garmin: ${fmtN(it)}" }) { v -> Repo.updateLog(t) { it.copy(sleep = v) } }
                    NumField("Steps", lg.steps, "st$t", Modifier.weight(1f), hint = g?.steps?.let { "Garmin: ${fmtN(it)}" }) { v -> Repo.updateLog(t) { it.copy(steps = v) } }
                }
                if (g != null) Muted(
                    "From Garmin: " + listOfNotNull(
                        g.steps?.let { "${fmtN(it)} steps" }, g.sleepH?.let { "${fmtN(it)} h sleep" },
                        g.restingHr?.let { "resting HR ${fmtN(it)}" }, g.bodyBattery?.let { "Body Battery ${fmtN(it)}" },
                        g.weight?.let { "${fmtN(it)} lb" },
                    ).joinToString(" · ").ifEmpty { "no data for today yet" } + ". Blank fields use Garmin’s numbers."
                )
                if (state.settings.garminKey.isNotBlank()) PillButton("Sync Garmin now", Ico.Sync) {
                    scope.launch { toast(runCatching { Garmin.sync(ctx) }.getOrElse { it.message ?: "Garmin sync failed" }) }
                }
            }
        }
        item {
            Section("How you feel") {
                Muted("1 = awful, 5 = great. Your coach uses these at check-ins.")
                ScaleRow("Energy", lg.energy) { v -> Repo.updateLog(t) { it.copy(energy = v) } }
                ScaleRow("Soreness (5 = very sore)", lg.sore) { v -> Repo.updateLog(t) { it.copy(sore = v) } }
                ScaleRow("Appetite", lg.hunger) { v -> Repo.updateLog(t) { it.copy(hunger = v) } }
                TextIn("Notes: pain, missed meals, anything your coach should know", lg.notes ?: "", "n$t", Modifier.fillMaxWidth(), singleLine = false, minLines = 3) { v ->
                    Repo.updateLog(t) { it.copy(notes = v.ifBlank { null }) }
                }
            }
        }
        item { MeasurementsCard(t, lg, dexa = "dexa" !in state.settings.skipTests) }
        item { Spacer(Modifier.height(24.dp)) }
    }

    val target = editing
    if (target != null || adding) TaskDialog(target, onDismiss = { editing = null; adding = false }, fromDay = PlanLogic.dow(t))
}

@Composable
private fun ScheduleCard(
    i: ScheduleItem, done: Boolean, isCurrent: Boolean, open: Boolean, lg: DayLog,
    onToggleOpen: () -> Unit, onDone: () -> Unit, onGo: (String) -> Unit, onEdit: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val (st, sa) = PlanLogic.timeParts(i.time)
    val (et, ea) = PlanLogic.timeParts(endTime(i))
    Box(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(shape)
            .background(if (isCurrent && !done) C.CardHi else C.Card)
            .border(if (isCurrent && !done) 1.5.dp else 1.dp, if (isCurrent && !done) C.Blue else C.Line, shape)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable(onClick = onToggleOpen),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(5.dp).fillMaxHeight().background(if (isCurrent && !done) C.Blue else Color.Transparent))
                Row(Modifier.weight(1f).padding(start = 12.dp, top = 14.dp, bottom = 14.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                        Icon(iconFor(i), null, tint = if (done) C.Muted else Color.Unspecified, modifier = Modifier.size(42.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(i.title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = if (done) C.Muted else C.Text, maxLines = 2)
                        Text("$st ${sa.uppercase()} – $et ${ea.uppercase()}", fontSize = 14.sp, color = C.Muted)
                    }
                    if (i.kcal != null) Text(
                        if (done) "${i.kcal} / ${i.kcal} cal" else "0 / ${i.kcal} cal",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (done) C.Green else C.Text,
                    ) else if (done) Icon(Ico.Check, "Done", tint = C.Green, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(8.dp))
                    Icon(if (open) Ico.Up else Ico.Down, null, tint = C.BlueSoft, modifier = Modifier.size(24.dp))
                }
            }
            AnimatedVisibility(open) {
                Column(Modifier.fillMaxWidth().background(C.Inner).padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (i.kind == "meal") {
                        i.detail.removeSuffix(".").split(",").map { it.trim().replaceFirstChar { c -> c.uppercase() } }.filter { it.isNotBlank() }.forEach { food ->
                            Text(food, fontSize = 16.sp, color = C.Text)
                        }
                        Row { Text("Protein", color = C.BlueSoft, fontSize = 15.sp, modifier = Modifier.weight(1f)); Text("${i.protein ?: 0} g", color = C.Text, fontSize = 15.sp) }
                    } else if (i.detail.isNotBlank()) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Ico.Note, null, tint = C.Muted, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(i.detail, fontSize = 15.sp, color = C.Muted, fontStyle = FontStyle.Italic)
                        }
                    }
                    HorizontalDivider(color = C.Line)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PillButton(if (done) "Done" else if (i.kind == "meal") "Ate it" else "Mark done", Ico.Check, selected = done, onClick = onDone)
                        if (i.kind == "train") PillButton("Open workout", Ico.Dumbbell) { onGo("workout") }
                        if (i.kind == "coach") PillButton("Open coach", Ico.Chat) { onGo("coach") }
                        if (i.kind == "meal") PillButton("Food log", Ico.Fork) { onGo("food") }
                        Spacer(Modifier.weight(1f))
                        if (i.id != "monthly") TextButton(onClick = onEdit) { Text("Edit", color = C.Muted) }
                    }
                    if (i.kind == "meal" && done && lg.foods.none { it.source == "plan:${i.id}" }) Muted("Ticked, but not in today’s food log.")
                }
            }
        }
    }
}

@Composable
fun MeasurementsCard(d: String, lg: DayLog, dexa: Boolean = true) {
    var open by remember { mutableStateOf(lg.m.values.any { it != null }) }
    Section("Measurements", action = { TextButton(onClick = { open = !open }) { Text(if (open) "Hide" else "Show", color = C.BlueSoft) } }) {
        Muted("Monthly: waist, body measurements, blood pressure, resting heart rate." + if (dexa) " DEXA results when you get them." else "")
        if (open) {
            val fields = listOf("waist" to "Waist at navel (in)", "arm" to "Arm (in)", "chest" to "Chest (in)", "thigh" to "Thigh (in)",
                "rhr" to "Resting heart rate") + if (dexa) listOf("bf" to "DEXA body fat %", "lean" to "DEXA lean mass (lb)") else emptyList()
            fields.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (k, label) ->
                        val cur = (lg.m[k] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
                        NumField(label, cur, "m$k$d", Modifier.weight(1f)) { v ->
                            Repo.updateLog(d) { it.copy(m = it.m + (k to v?.let { x -> JsonPrimitive(x) })) }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            TextIn("Blood pressure (e.g. 122/78)", (lg.m["bp"] as? JsonPrimitive)?.contentOrNull ?: "", "bp$d", Modifier.fillMaxWidth()) { v ->
                Repo.updateLog(d) { it.copy(m = it.m + ("bp" to v.takeIf { s -> s.isNotBlank() }?.let { s -> JsonPrimitive(s) })) }
            }
        }
    }
}
