@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bulkplan.tracker.ui

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bulkplan.tracker.LocalToast
import com.bulkplan.tracker.data.AppState
import com.bulkplan.tracker.data.ChangeLogEntry
import com.bulkplan.tracker.data.ChatMessage
import com.bulkplan.tracker.data.DayLog
import com.bulkplan.tracker.data.Proposal
import com.bulkplan.tracker.data.Repo
import com.bulkplan.tracker.logic.Coach
import com.bulkplan.tracker.logic.PlanLogic
import com.bulkplan.tracker.net.Claude
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

/** A short, honest status line for the mascot's speech bubble, built from your logs. */
private fun coachLine(s: AppState, logs: Map<String, DayLog>): String {
    val p = s.plan; val t = PlanLogic.today(); val w = PlanLogic.weekOf(p, t)
    if (w < 1) {
        val left = p.prep.count { s.prepDone[it.id] != true }
        return "Your plan starts ${PlanLogic.fmtD(p.startDate)}." + if (left > 0) " $left thing${if (left == 1) "" else "s"} left on your before-week-1 list." else " You’re all set to start."
    }
    val days = (0..6).map { PlanLogic.addD(t, -it.toLong()) }
    val target = PlanLogic.proteinTarget(p, logs) * 0.9
    val protein = days.count { (PlanLogic.proteinOf(logs[it]) ?: 0.0) >= target }
    val workouts = days.count { d -> logs[d]?.sets?.values?.any { PlanLogic.validSets(it).isNotEmpty() } == true }
    val parts = mutableListOf("Protein on target $protein of the last 7 days, $workouts workout${if (workouts == 1) "" else "s"} logged.")
    if (PlanLogic.weightOf(logs[t]) == null) parts += "Don’t forget today’s weigh-in."
    else parts += if (protein >= 5) "Keep it up." else "Let’s get protein up this week."
    return parts.joinToString(" ")
}

/** The next time the weekly review sits on your schedule. */
private fun nextCheckIn(s: AppState): LocalDateTime? {
    val now = LocalDateTime.now(); val t = PlanLogic.today()
    for (i in 0..7L) {
        val d = PlanLogic.addD(t, i)
        PlanLogic.itemsFor(s.plan, d).filter { it.kind == "coach" }.forEach { item ->
            val m = PlanLogic.mins(item.time)
            val at = PlanLogic.date(d).atTime(m / 60, m % 60)
            if (at.isAfter(now)) return at
        }
    }
    return null
}

@Composable
fun CoachScreen(onOpenSettings: () -> Unit, onGo: (String) -> Unit) {
    val state by Repo.state.collectAsStateWithLifecycle()
    val logs by Repo.logs.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var details by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var paste by remember { mutableStateOf("") }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = LocalDateTime.now() } }
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    val clip = LocalClipboardManager.current
    val msgs = state.coach.messages
    val hasKey = state.settings.claudeKey.isNotBlank()

    fun send(text: String) {
        if (text.isBlank() || busy) return
        if (!hasKey) {
            clip.setText(AnnotatedString(Coach.fullPrompt(state, logs, text)))
            toast("No API key yet, so it’s copied. Paste it into a Claude chat.")
            return
        }
        val first = msgs.none { it.role == "user" }
        val um = ChatMessage("user", if (first) Coach.fullPrompt(state, logs, text) else text, display = text)
        Repo.update { it.copy(coach = it.coach.copy(messages = it.coach.messages + um)) }
        busy = true
        scope.launch {
            try {
                val s = Repo.state.value
                val reply = Claude.chat(s.settings.claudeKey, s.settings.claudeModel, s.coach.messages)
                val prop = Coach.parseProposal(reply)
                Repo.update { it.copy(coach = it.coach.copy(messages = it.coach.messages + ChatMessage("assistant", reply), pending = prop ?: it.coach.pending)) }
            } catch (e: Exception) {
                Repo.update { st ->
                    val ms = st.coach.messages.toMutableList()
                    val i = ms.indexOfLast { it.role == "user" }
                    if (i >= 0) ms[i] = ms[i].copy(failed = true)
                    st.copy(coach = st.coach.copy(messages = ms + ChatMessage("note", e.message ?: "Couldn’t reach Claude.")))
                }
            }
            busy = false
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        // Mascot and greeting
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CoachBot(132.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(C.Card).border(1.dp, C.Line, RoundedCornerShape(18.dp)).padding(16.dp)) {
                    Text("Hey${state.settings.name.takeIf { it.isNotBlank() }?.let { " $it" } ?: " there"}!", style = H2)
                    Spacer(Modifier.height(4.dp))
                    Text(coachLine(state, logs), color = C.Text, fontSize = 16.sp, lineHeight = 22.sp)
                }
            }
        }
        // Weekly check-in countdown
        item {
            val next = nextCheckIn(state)
            Section("Weekly Check-In", icon = Ico.Calendar, iconTint = C.Blue, highlight = true, action = {
                Icon(if (details) Ico.Up else Ico.Right, null, tint = C.BlueSoft, modifier = Modifier.size(24.dp).clickable { details = !details })
            }) {
                if (next == null) Muted("There’s no weekly review on your schedule. Add one from Today › Add Task (type: Other), or start a check-in any time.")
                else {
                    Muted("Your next check-in is in:", size = 15)
                    val secs = Duration.between(now, next).seconds.coerceAtLeast(0)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(secs / 86400 to "Days", (secs % 86400) / 3600 to "Hours", (secs % 3600) / 60 to "Minutes", secs % 60 to "Seconds").forEach { (v, l) ->
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(C.Inner).border(1.dp, C.Line, RoundedCornerShape(14.dp)).padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$v", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = C.Text)
                                Text(l, fontSize = 13.sp, color = C.Text)
                            }
                        }
                    }
                }
                AddButton(if (details) "Hide Details" else "View Details", icon = Ico.Calendar) { details = !details }
                if (details) {
                    Muted("At the check-in, your coach reviews your 2-week weight trend against the target pace, calories and protein from your food log, sleep and Garmin data, how your lifts moved, and your notes. It asks a few questions, then suggests changes for you to approve.")
                    Button(onClick = { send(Coach.STARTERS[0].second) }, enabled = !busy) { Text("Start check-in now") }
                }
            }
        }
        // Chat
        item {
            Section("Chat with Your Coach", icon = Ico.Coach, action = {
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (hasKey) C.Green else C.Muted))
                Spacer(Modifier.width(6.dp))
                Text(if (hasKey) "Online" else "Not connected", color = if (hasKey) C.Green else C.Muted, fontSize = 14.sp)
            }) {
                if (msgs.isEmpty()) Bubble(ChatMessage("assistant", if (hasKey) "Need help with your plan, meals or training? Ask me anything!" else "Add your Claude API key in Settings to chat here. Until then, anything you send is copied so you can paste it into a Claude chat."))
                msgs.forEach { Bubble(it) }
                if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
                    CoachAvatar(36.dp); Spacer(Modifier.width(10.dp))
                    CircularProgressIndicator(Modifier.size(18.dp), color = C.Blue, strokeWidth = 2.dp); Muted("  Thinking…")
                }
                state.coach.pending?.let { ProposalCard(it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(input, { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("Type a message…") },
                        shape = RoundedCornerShape(28.dp), colors = fieldColors(), enabled = !busy, maxLines = 5)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.size(56.dp).clip(CircleShape).background(if (input.isNotBlank() && !busy) C.Blue else C.BlueTint)
                        .clickable(enabled = input.isNotBlank() && !busy) { val v = input; input = ""; send(v) },
                        contentAlignment = Alignment.Center) { Icon(Ico.Send, "Send", tint = Color.White, modifier = Modifier.size(26.dp)) }
                }
                if (msgs.isNotEmpty()) TextButton(onClick = { Repo.update { it.copy(coach = it.coach.copy(messages = emptyList(), pending = null)) } }) {
                    Text("Start a new conversation", color = C.Muted)
                }
                if (!hasKey) TextButton(onClick = onOpenSettings) { Text("Add API key in Settings", color = C.BlueSoft) }
            }
        }
        // Quick actions
        item {
            Section("Quick Actions", icon = Ico.Bolt) {
                val qa = listOf(
                    Triple("Get Meal Advice", Ico.Fork, { send("Look at my food log and my plan's meals. Give me specific meal advice for hitting my calories and protein, and ask me what isn't working before suggesting plan changes.") }),
                    Triple("Adjust Goals", Ico.Chart, { send("Review my calorie, protein and weight-gain targets against my last 2 weeks of data. Ask me what you need to, then suggest any changes.") }),
                    Triple("View Progress", Ico.Dumbbell, { onGo("progress") }),
                    Triple("Nutrition Tips", Ico.Bulb, { send("Give me 3 specific nutrition tips based on my last week of logs. Keep them short.") }),
                )
                qa.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (label, icon, act) -> QuickAction(label, icon, Modifier.weight(1f), act) }
                    }
                }
                Muted("More topics")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Coach.STARTERS.drop(1).forEach { (label, ask) -> Chip(label, false) { send(ask) } }
                }
            }
        }
        // Copy-paste fallback and change history
        item {
            Section("More", action = { TextButton(onClick = { more = !more }) { Text(if (more) "Hide" else "Show", color = C.BlueSoft) } }) {
                Muted("Copy-and-paste check-in for any Claude chat, and the history of plan changes with undo.")
                if (more) {
                    Text("Copy-and-paste check-in", style = H3)
                    Muted("1) Copy the check-in and paste it into Claude. 2) Answer its questions there. 3) Paste Claude’s final reply (the one with the json block) below.")
                    PillButton("Copy check-in", Ico.Clipboard) {
                        clip.setText(AnnotatedString(Coach.fullPrompt(state, logs, Coach.STARTERS[0].second) + "\n\n(I’m using a tracker app. When we agree on changes, put them in the json block so I can paste your reply into the app.)"))
                        toast("Check-in copied")
                    }
                    Field(paste, { paste = it }, "Paste Claude’s reply", Modifier.fillMaxWidth(), singleLine = false, minLines = 3)
                    Button(onClick = {
                        val p = Coach.parseProposal(paste)
                        if (p == null) toast("No change list found. Paste the reply that ends with the json block.")
                        else { Repo.update { it.copy(coach = it.coach.copy(pending = p.copy(source = "pasted reply"))) }; paste = ""; toast("Changes are waiting in the chat above") }
                    }) { Text("Read changes") }
                    HorizontalDivider(color = C.Line)
                    ChangeLog(state.changelog)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.height(58.dp).clip(RoundedCornerShape(14.dp)).background(C.Inner).border(1.dp, C.Line, RoundedCornerShape(14.dp))
        .clickable(onClick = onClick).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = C.BlueSoft, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
    }
}

@Composable
private fun Bubble(m: ChatMessage) {
    when (m.role) {
        "user" -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text((m.display ?: m.content) + if (m.failed == true) "\n(not sent)" else "", color = Color.White, fontSize = 16.sp, lineHeight = 22.sp,
                modifier = Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)).background(C.Blue).padding(12.dp))
        }
        "note" -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(m.content, color = C.Green, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(C.Green.copy(alpha = .12f)).padding(horizontal = 10.dp, vertical = 6.dp))
        }
        else -> Row(verticalAlignment = Alignment.Top) {
            CoachAvatar(40.dp)
            Spacer(Modifier.width(10.dp))
            Text(Coach.stripJson(m.content).replace("**", ""), color = C.Text, fontSize = 16.sp, lineHeight = 22.sp,
                modifier = Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)).background(C.CardHi).padding(12.dp))
        }
    }
}

@Composable
private fun ProposalCard(p: Proposal) {
    val state by Repo.state.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Inner).border(1.5.dp, C.Blue, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (p.error != null) {
            Text(p.error, color = C.Text)
            TextButton(onClick = { Repo.update { it.copy(coach = it.coach.copy(pending = null)) } }) { Text("Dismiss") }
        } else ProposalBody(p, state.plan, toast)
    }
}

@Composable
private fun ProposalBody(p: Proposal, plan: com.bulkplan.tracker.data.Plan, toast: (String) -> Unit) {
    run {
        val previews = remember(p, plan) { Coach.apply(plan, p.changes).second }
        val picked = remember(p) { mutableStateListOf<Int>().apply { previews.forEachIndexed { i, pv -> if (pv.ok) add(i) } } }
        Tag("Proposed by ${p.source}")
        Text(p.summary, style = H3)
        previews.forEachIndexed { i, pv ->
            HorizontalDivider(color = C.Line)
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(checked = i in picked, enabled = pv.ok, onCheckedChange = { if (it) picked.add(i) else picked.remove(i) },
                    colors = CheckboxDefaults.colors(checkedColor = C.Blue, uncheckedColor = C.Muted))
                Column(Modifier.padding(top = 10.dp)) {
                    Text(pv.label, color = C.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(pv.info, color = C.Text, fontSize = 15.sp)
                    pv.why?.let { Muted(it) }
                    if (!pv.ok) Text("Can’t apply this one.", color = C.Red, fontSize = 14.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = picked.isNotEmpty(), onClick = {
                val chosen = picked.sorted().map { p.changes[it] }
                val (np, res) = Coach.apply(Repo.state.value.plan, chosen)
                val n = res.count { it.ok }
                if (np == null) { toast("Nothing could be applied"); return@Button }
                Repo.update { s ->
                    s.copy(
                        plan = np,
                        changelog = s.changelog + ChangeLogEntry(Instant.now().toString(), p.summary, n, p.source, s.plan),
                        coach = s.coach.copy(pending = null, messages = s.coach.messages + ChatMessage("note", "Applied $n change${if (n == 1) "" else "s"}: ${p.summary}")),
                    )
                }
                toast("Plan updated")
            }) { Text("Apply selected") }
            TextButton(onClick = { Repo.update { it.copy(coach = it.coach.copy(pending = null)) } }) { Text("Not now", color = C.Muted) }
        }
    }
}

@Composable
private fun ChangeLog(log: List<ChangeLogEntry>) {
    val toast = LocalToast.current
    Text("Applied changes", style = H3)
    if (log.isEmpty()) Muted("No changes yet.")
    log.asReversed().forEachIndexed { ri, c ->
        val idx = log.size - 1 - ri
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.summary, color = C.Text, fontWeight = FontWeight.SemiBold)
                Muted("${c.at.take(10)} · ${c.count} change${if (c.count == 1) "" else "s"} · ${c.source}")
            }
            val before = c.before
            if (before != null) TextButton(onClick = {
                Repo.update { s ->
                    s.copy(plan = before, changelog = s.changelog.take(idx),
                        coach = s.coach.copy(messages = s.coach.messages + ChatMessage("note", "Undid: ${c.summary}")))
                }
                toast("Change undone")
            }) { Text("Undo", color = C.BlueSoft) }
        }
    }
}
