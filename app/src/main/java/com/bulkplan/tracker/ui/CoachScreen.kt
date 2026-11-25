@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bulkplan.tracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.bulkplan.tracker.logic.CoachChat
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
import com.bulkplan.tracker.net.Ai
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

/** A short, honest status line for the mascot's speech bubble, built from your logs. */
private fun coachLine(s: AppState, logs: Map<String, DayLog>): String {
    val p = s.plan; val t = PlanLogic.today(); val w = PlanLogic.weekOf(p, t)
    if (w < 1) {
        val left = PlanLogic.prepFor(p, s.settings).count { s.prepDone[it.id] != true }
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
    var details by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var paste by remember { mutableStateOf("") }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = LocalDateTime.now() } }
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    val clip = LocalClipboardManager.current
    val msgs = state.coach.messages
    val hasKey = Ai.hasKey(state.settings)

    val busy by CoachChat.busy.collectAsStateWithLifecycle()

    /** Sends a message and opens the chat page, or copies the prompt when no key is set. */
    fun send(text: String) {
        if (text.isBlank() || busy) return
        if (!CoachChat.send(text)) {
            clip.setText(AnnotatedString(Coach.fullPrompt(state, logs, text)))
            toast("No API key yet, so it’s copied. Paste it into any AI chat.")
            return
        }
        onGo("chat")
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
                if (next == null) Muted("There’s no weekly review on your schedule. Add one in Today › Edit (type: Coach check-in), or start a check-in any time.")
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
        // Chat preview: the full conversation lives on its own page.
        item {
            Section("Chat with Your Coach", icon = Ico.Coach, action = {
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (hasKey) C.Green else C.Muted))
                Spacer(Modifier.width(6.dp))
                Text(if (hasKey) "Online" else "Not connected", color = if (hasKey) C.Green else C.Muted, fontSize = 14.sp)
            }) {
                val recent = msgs.filter { it.role != "note" }.takeLast(2)
                if (recent.isEmpty()) Bubble(ChatMessage("assistant", if (hasKey) "Need help with your plan, meals or training? Ask me anything!" else "Add an AI API key in Settings › AI coach to chat here (Gemini and DeepSeek are cheap). Until then, anything you send is copied so you can paste it into any AI chat."), preview = true)
                recent.forEach { Bubble(it, preview = true) }
                if (busy) Muted("Your coach is typing…")
                if (state.coach.pending != null) Tag("Plan changes are waiting for your OK")
                AddButton(if (msgs.isEmpty()) "Start a chat" else "Open chat", icon = Ico.Chat) { onGo("chat") }
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
                Muted("Copy-and-paste check-in for any AI chat, and the history of plan changes with undo.")
                if (more) {
                    Text("Copy-and-paste check-in", style = H3)
                    Muted("1) Copy the check-in and paste it into any AI chat. 2) Answer its questions there. 3) Paste its final reply (the one with the json block) below.")
                    PillButton("Copy check-in", Ico.Clipboard) {
                        clip.setText(AnnotatedString(Coach.fullPrompt(state, logs, Coach.STARTERS[0].second) + "\n\n(I’m using a tracker app. When we agree on changes, put them in the json block so I can paste your reply into the app.)"))
                        toast("Check-in copied")
                    }
                    Field(paste, { paste = it }, "Paste the AI’s reply", Modifier.fillMaxWidth(), singleLine = false, minLines = 3)
                    Button(onClick = {
                        val p = Coach.parseProposal(paste)
                        if (p == null) toast("No change list found. Paste the reply that ends with the json block.")
                        else { Repo.update { it.copy(coach = it.coach.copy(pending = p.copy(source = "pasted reply"))) }; paste = ""; onGo("chat") }
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

/** Light markdown: **bold**, # headings and - bullets, which is what AI replies mostly use. */
private fun md(text: String): AnnotatedString = buildAnnotatedString {
    val lines = text.trim().lines()
    lines.forEachIndexed { li, raw ->
        var line = raw.trimEnd()
        val heading = Regex("^#{1,6}\\s+").find(line)
        if (heading != null) line = line.removeRange(heading.range)
        Regex("^(\\s*)[-*•]\\s+").find(line)?.let { line = it.groupValues[1] + "•  " + line.substring(it.range.last + 1) }
        if (heading != null) pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        var rest = line
        while (true) {
            val i = rest.indexOf("**"); if (i < 0) break
            val j = rest.indexOf("**", i + 2); if (j < 0) break
            append(rest.substring(0, i))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(rest.substring(i + 2, j)) }
            rest = rest.substring(j + 2)
        }
        append(rest)
        if (heading != null) pop()
        if (li < lines.size - 1) append("\n")
    }
}

@Composable
private fun Bubble(m: ChatMessage, preview: Boolean = false) {
    val maxW = if (preview) 290.dp else 320.dp
    when (m.role) {
        "user" -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text((m.display ?: m.content).let { if (preview && it.length > 160) it.take(160) + "…" else it } + if (m.failed == true) "\n(not sent)" else "",
                color = Color.White, fontSize = 16.sp, lineHeight = 22.sp,
                modifier = Modifier.widthIn(max = maxW).clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)).background(C.Blue).padding(12.dp))
        }
        "note" -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val bad = m.content.startsWith("Applied") || m.content.startsWith("Undid")
            Text(m.content, color = if (bad) C.Green else C.Yellow, fontSize = 13.sp,
                modifier = Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(10.dp)).background((if (bad) C.Green else C.Yellow).copy(alpha = .12f)).padding(horizontal = 10.dp, vertical = 6.dp))
        }
        else -> Row(verticalAlignment = Alignment.Top) {
            CoachAvatar(if (preview) 40.dp else 34.dp)
            Spacer(Modifier.width(10.dp))
            val body = Coach.stripJson(m.content).let { if (preview && it.length > 220) it.take(220).trimEnd() + "…" else it }
            SelectionContainer {
                Text(md(body), color = C.Text, fontSize = 16.sp, lineHeight = 23.sp,
                    modifier = Modifier.widthIn(max = maxW).clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)).background(C.CardHi).padding(12.dp))
            }
        }
    }
}

/** Full-screen chat, like a messaging app: newest message at the bottom, input pinned below. */
@Composable
fun ChatScreen(onClose: () -> Unit, onOpenSettings: () -> Unit) {
    BackHandler(onBack = onClose)
    val state by Repo.state.collectAsStateWithLifecycle()
    val busy by CoachChat.busy.collectAsStateWithLifecycle()
    val msgs = state.coach.messages
    val hasKey = Ai.hasKey(state.settings)
    var input by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    LaunchedEffect(msgs.size, busy, state.coach.pending) { list.animateScrollToItem(0) }
    fun go(text: String) { if (text.isNotBlank() && !busy && CoachChat.send(text)) input = "" }

    Scaffold(
        containerColor = C.Bg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CoachAvatar(36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("AI Coach", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(if (busy) "typing…" else if (hasKey) "${Ai.label(state.settings)} · ${Ai.cfg(state.settings).model}" else "Not connected",
                                fontSize = 12.sp, color = if (busy || hasKey) C.Green else C.Muted, maxLines = 1)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    TextButton(enabled = msgs.isNotEmpty() && !busy, onClick = { menu = true }) { Text("New chat", color = C.BlueSoft) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = C.Card),
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().background(C.Card).navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(input, { input = it }, modifier = Modifier.weight(1f),
                    placeholder = { Text(if (hasKey) "Message your coach…" else "Add an API key to chat") },
                    shape = RoundedCornerShape(24.dp), colors = fieldColors(), enabled = hasKey, maxLines = 6)
                Spacer(Modifier.width(8.dp))
                val can = input.isNotBlank() && !busy && hasKey
                Box(Modifier.size(52.dp).clip(CircleShape).background(if (can) C.Blue else C.BlueTint).clickable(enabled = can) { go(input) },
                    contentAlignment = Alignment.Center) { Icon(Ico.Send, "Send", tint = Color.White, modifier = Modifier.size(24.dp)) }
            }
        },
    ) { pad ->
        LazyColumn(
            state = list, reverseLayout = true,
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            // Bottom-anchored so a short chat sits just above the input, like a messaging app.
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
        ) {
            // reverseLayout: the first item here sits at the bottom, just above the input.
            if (busy) item(key = "busy") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoachAvatar(34.dp); Spacer(Modifier.width(10.dp))
                    CircularProgressIndicator(Modifier.size(18.dp), color = C.Blue, strokeWidth = 2.dp); Muted("  Thinking…")
                }
            }
            if (!busy && CoachChat.lastFailed(msgs)) item(key = "retry") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PillButton("Try again", Ico.Sync) { CoachChat.retry() }
                }
            }
            state.coach.pending?.let { p -> item(key = "pending") { ProposalCard(p) } }
            itemsIndexed(msgs.asReversed(), key = { i, _ -> "m${msgs.size - i}" }) { _, m -> Bubble(m) }
            if (msgs.isEmpty()) item(key = "welcome") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Bubble(ChatMessage("assistant", if (hasKey) "Hey${state.settings.name.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""}! I can see your plan, food log, weights, sleep, steps and lifts. What do you want to work on?"
                        else "Add an AI API key in Settings › AI coach and I can chat here with your real numbers."))
                    if (hasKey) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Coach.STARTERS.forEach { (label, ask) -> Chip(label, false) { go(ask) } }
                    } else Button(onClick = onOpenSettings) { Text("Open Settings") }
                }
            }
        }
    }
    if (menu) AlertDialog(
        onDismissRequest = { menu = false },
        containerColor = C.Card,
        title = { Text("Start a new chat?", color = C.Text) },
        text = { Text("Clears this conversation. Your plan, logs and applied changes stay, and the coach still sees all your data.", color = C.Text) },
        confirmButton = { Button(onClick = { Repo.update { it.copy(coach = it.coach.copy(messages = emptyList(), pending = null)) }; menu = false }) { Text("New chat") } },
        dismissButton = { TextButton(onClick = { menu = false }) { Text("Cancel") } },
    )
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
