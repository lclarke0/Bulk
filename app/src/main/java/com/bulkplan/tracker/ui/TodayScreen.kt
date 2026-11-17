@file:OptIn(ExperimentalMaterial3Api::class)

package com.bulkplan.tracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bulkplan.tracker.LocalToast
import com.bulkplan.tracker.data.DayLog
import com.bulkplan.tracker.data.Repo
import com.bulkplan.tracker.logic.Actions
import com.bulkplan.tracker.logic.PlanLogic
import com.bulkplan.tracker.logic.PlanLogic.fmtN
import com.bulkplan.tracker.net.Garmin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Composable
fun TodayScreen(onGo: (String) -> Unit) {
    val state by Repo.state.collectAsStateWithLifecycle()
    val logs by Repo.logs.collectAsStateWithLifecycle()
    var nowM by remember { mutableIntStateOf(PlanLogic.nowMins()) }
    var t by remember { mutableStateOf(PlanLogic.today()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); nowM = PlanLogic.nowMins(); t = PlanLogic.today() } }
    val plan = state.plan
    val lg = logs[t] ?: DayLog(t)
    val items = PlanLogic.itemsFor(plan, t)
    val done = items.count { lg.checks[it.id] == true }
    val next = items.firstOrNull { PlanLogic.mins(it.time) > nowM && lg.checks[it.id] != true }
    val nowIt = items.lastOrNull { PlanLogic.mins(it.time) <= nowM && nowM - PlanLogic.mins(it.time) <= 60 && lg.checks[it.id] != true }
    val focus = nowIt ?: next
    val totals = PlanLogic.foodTotals(lg)
    val pTarget = PlanLogic.proteinTarget(plan, logs)
    val w = PlanLogic.weekOf(plan, t)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        if (w < 1) item {
            val dn = plan.prep.count { state.prepDone[it.id] == true }
            Section("Before week 1", action = { Tag("$dn/${plan.prep.size} done") }) {
                Muted("Your plan starts ${PlanLogic.fmtD(plan.startDate)}. Change the date in Settings.")
                plan.prep.forEach { p ->
                    val on = state.prepDone[p.id] == true
                    Row(Modifier.fillMaxWidth().clickable { Repo.update { it.copy(prepDone = it.prepDone + (p.id to !on)) } }, verticalAlignment = Alignment.CenterVertically) {
                        Text(p.text, modifier = Modifier.weight(1f), fontSize = 15.sp)
                        Checkbox(checked = on, onCheckedChange = { v -> Repo.update { it.copy(prepDone = it.prepDone + (p.id to v)) } })
                    }
                }
            }
        }
        plan.milestones.filter { it.week == w }.takeIf { it.isNotEmpty() }?.let { ms ->
            item {
                Section {
                    Tag("This week")
                    Text(ms.joinToString(" · ") { it.text }, style = H3)
                }
            }
        }
        item {
            val ink = MaterialTheme.colorScheme.onBackground
            val bg = MaterialTheme.colorScheme.background
            Column(
                Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(18.dp)).background(ink).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (focus != null) {
                    val (tm, ap) = PlanLogic.timeParts(focus.time)
                    Text(if (nowIt != null) "Up now" else "Next · in ${PlanLogic.durText(PlanLogic.mins(focus.time) - nowM)}", color = bg.copy(alpha = .75f), fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(tm, color = bg, fontSize = 54.sp, fontWeight = FontWeight.Black, lineHeight = 54.sp)
                        Text(" $ap", color = bg.copy(alpha = .75f), fontSize = 22.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Text(focus.title, color = bg, style = H2)
                    Text(focus.detail, color = bg.copy(alpha = .85f), fontSize = 15.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { Actions.setChecked(t, focus, true) }) { Text("Mark done") }
                        if (focus.kind == "train") OutlinedButton(onClick = { onGo("workout") }, border = BorderStroke(1.dp, bg.copy(alpha = .5f))) { Text("Open workout", color = bg) }
                        if (focus.kind == "coach") OutlinedButton(onClick = { onGo("coach") }, border = BorderStroke(1.dp, bg.copy(alpha = .5f))) { Text("Open Coach", color = bg) }
                    }
                } else {
                    Text("All set", color = bg.copy(alpha = .75f), fontSize = 14.sp)
                    Text("Nothing left on today’s list", color = bg, style = H2)
                    Text("Lights out by 10:30 for 8 hours of sleep.", color = bg.copy(alpha = .85f))
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text("$done of ${items.size} done", color = bg.copy(alpha = .85f), fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("${fmtN(totals.kcal)} / ${fmtN(plan.targets.calories.toDouble())} kcal · ${fmtN(totals.protein)} / $pTarget g", color = bg.copy(alpha = .85f), fontSize = 13.sp)
                }
                Meter(if (items.isEmpty()) 0f else done / items.size.toFloat(), track = bg.copy(alpha = .2f))
            }
        }
        item {
            Text(PlanLogic.DAYN[PlanLogic.dow(t)], style = H2, modifier = Modifier.padding(vertical = 6.dp))
        }
        items(items, key = { it.id }) { i ->
            val d = lg.checks[i.id] == true
            val (tm, ap) = PlanLogic.timeParts(i.time)
            val isFocus = focus?.id == i.id && !d
            Column {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (isFocus) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background)
                        .clickable { Actions.setChecked(t, i, !d) }.padding(vertical = 10.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.width(64.dp)) {
                        Text(tm, fontSize = 24.sp, fontWeight = FontWeight.Black, color = if (d) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground)
                        Text(ap, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(i.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = if (d) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground)
                        Muted(i.detail + (i.kcal?.let { " · ${i.protein ?: 0} g · $it kcal" } ?: ""))
                        if (i.kind == "train") TextButton(onClick = { onGo("workout") }, contentPadding = PaddingValues(0.dp)) { Text("Log this workout") }
                    }
                    Checkbox(checked = d, onCheckedChange = { Actions.setChecked(t, i, it) })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            Section("Today’s numbers") {
                val g = lg.garmin
                NumField("Morning weight (lb)", lg.weight, "w$t", Modifier.fillMaxWidth(), hint = g?.weight?.let { "Garmin: ${fmtN(it)}" }) { v -> Repo.updateLog(t) { it.copy(weight = v) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Sleep (h)", lg.sleep, "s$t", Modifier.weight(1f), hint = g?.sleepH?.let { "Garmin: ${fmtN(it)}" }) { v -> Repo.updateLog(t) { it.copy(sleep = v) } }
                    NumField("Steps", lg.steps, "st$t", Modifier.weight(1f), hint = g?.steps?.let { "Garmin: ${fmtN(it)}" }) { v -> Repo.updateLog(t) { it.copy(steps = v) } }
                }
                if (g != null) Muted(buildString {
                    append("From Garmin: ")
                    append(listOfNotNull(
                        g.steps?.let { "${fmtN(it)} steps" }, g.sleepH?.let { "${fmtN(it)} h sleep" },
                        g.restingHr?.let { "resting HR ${fmtN(it)}" }, g.bodyBattery?.let { "Body Battery ${fmtN(it)}" },
                        g.weight?.let { "${fmtN(it)} lb" },
                    ).joinToString(" · ").ifEmpty { "no data for today yet" })
                    append(". Blank fields use Garmin’s numbers.")
                })
                if (state.settings.garminKey.isNotBlank()) TextButton(onClick = {
                    scope.launch { toast(runCatching { Garmin.sync(ctx) }.getOrElse { it.message ?: "Garmin sync failed" }) }
                }) { Text("Sync Garmin now") }
            }
        }
        item {
            Section("How you feel") {
                Muted("1 = awful, 5 = great. Claude uses these at check-ins.")
                ScaleRow("Energy", lg.energy) { v -> Repo.updateLog(t) { it.copy(energy = v) } }
                ScaleRow("Soreness (5 = very sore)", lg.sore) { v -> Repo.updateLog(t) { it.copy(sore = v) } }
                ScaleRow("Appetite", lg.hunger) { v -> Repo.updateLog(t) { it.copy(hunger = v) } }
                TextIn("Notes: pain, missed meals, anything Claude should know", lg.notes ?: "", "n$t", Modifier.fillMaxWidth(), singleLine = false, minLines = 3) { v ->
                    Repo.updateLog(t) { it.copy(notes = v.ifBlank { null }) }
                }
            }
        }
        item { MeasurementsCard(t, lg) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun MeasurementsCard(d: String, lg: DayLog) {
    var open by remember { mutableStateOf(lg.m.values.any { it != null }) }
    Section("Measurements", action = { TextButton(onClick = { open = !open }) { Text(if (open) "Hide" else "Show") } }) {
        Muted("Monthly: waist, body measurements, blood pressure, resting heart rate. DEXA results when you get them.")
        if (open) {
            val fields = listOf("waist" to "Waist at navel (in)", "arm" to "Arm (in)", "chest" to "Chest (in)", "thigh" to "Thigh (in)",
                "rhr" to "Resting heart rate", "bf" to "DEXA body fat %", "lean" to "DEXA lean mass (lb)")
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
