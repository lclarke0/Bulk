@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bulkplan.tracker.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bulkplan.tracker.LocalToast
import com.bulkplan.tracker.data.ChangeLogEntry
import com.bulkplan.tracker.data.ChatMessage
import com.bulkplan.tracker.data.Proposal
import com.bulkplan.tracker.data.Repo
import com.bulkplan.tracker.logic.Coach
import com.bulkplan.tracker.net.Claude
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun CoachScreen(onOpenSettings: () -> Unit) {
    val state by Repo.state.collectAsStateWithLifecycle()
    val logs by Repo.logs.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var paste by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    val clip = LocalClipboardManager.current
    val msgs = state.coach.messages
    val hasKey = state.settings.claudeKey.isNotBlank()
    val list = rememberLazyListState()

    fun send(text: String) {
        if (text.isBlank() || busy) return
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
                    st.copy(coach = st.coach.copy(messages = ms + ChatMessage("note", e.message ?: "Claude couldn’t reply.")))
                }
            }
            busy = false
        }
    }

    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) list.animateScrollToItem(1 + msgs.size - 1) }

    LazyColumn(state = list, contentPadding = PaddingValues(16.dp)) {
        item {
            if (!hasKey) Section("Connect Claude") {
                Text("Add your Claude API key in Settings to chat here. Until then, use the copy-and-paste check-in at the bottom: it works with any Claude chat.")
                Button(onClick = onOpenSettings) { Text("Open Settings") }
            }
            Section("Check in with Claude", action = { if (msgs.isNotEmpty()) TextButton(onClick = { Repo.update { it.copy(coach = it.coach.copy(messages = emptyList(), pending = null)) } }) { Text("New check-in") } }) {
                Muted("Claude sees your plan, 21 days of logs, your food diary totals, Garmin data and recent lifts. It asks follow-up questions first, then proposes changes you approve one by one.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Coach.STARTERS.forEach { (label, ask) ->
                        FilterChip(selected = false, enabled = !busy, onClick = {
                            if (hasKey) send(ask) else { clip.setText(AnnotatedString(Coach.fullPrompt(state, logs, ask))); toast("Copied. Paste it into a Claude chat.") }
                        }, label = { Text(label) })
                    }
                }
            }
        }
        itemsIndexed(msgs) { _, m -> Bubble(m) }
        item {
            if (busy) Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.height(20.dp)); Muted("  Claude is replying…")
            }
            state.coach.pending?.let { ProposalCard(it) }
            if (hasKey) Section {
                OutlinedTextField(input, { input = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, enabled = !busy,
                    label = { Text(if (msgs.isEmpty()) "Ask anything" else "Reply to Claude") })
                Button(onClick = { val v = input; input = ""; send(v) }, enabled = !busy && input.isNotBlank()) { Text("Send") }
            }
            Section("Copy-and-paste check-in") {
                Muted("Use this without an API key, or to talk in a regular Claude chat. 1) Copy the check-in and paste it into Claude. 2) Answer its questions there. 3) Paste Claude’s final reply (the one with the json block) below.")
                OutlinedButton(onClick = {
                    clip.setText(AnnotatedString(Coach.fullPrompt(state, logs, Coach.STARTERS[0].second) + "\n\n(I’m using a tracker app. When we agree on changes, put them in the json block so I can paste your reply into the app.)"))
                    toast("Check-in copied")
                }) { Text("Copy check-in for Claude") }
                OutlinedTextField(paste, { paste = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, label = { Text("Paste Claude’s reply") })
                Button(onClick = {
                    val p = Coach.parseProposal(paste)
                    if (p == null) toast("No change list found. Paste the reply that ends with the json block.")
                    else { Repo.update { it.copy(coach = it.coach.copy(pending = p.copy(source = "pasted reply"))) }; paste = "" }
                }) { Text("Read changes") }
            }
            ChangeLog(state.changelog)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage) {
    val user = m.role == "user"
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = when { user -> Alignment.CenterEnd; m.role == "note" -> Alignment.Center; else -> Alignment.CenterStart }) {
        val bg = when { user -> MaterialTheme.colorScheme.onBackground; m.role == "note" -> MaterialTheme.colorScheme.secondaryContainer; else -> MaterialTheme.colorScheme.surface }
        val fg = if (user) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface
        val text = if (user) (m.display ?: m.content) else Coach.stripJson(m.content).replace("**", "")
        Text(
            text + if (m.failed == true) "\n(not sent)" else "",
            color = fg, fontSize = if (m.role == "note") 14.sp else 16.sp,
            modifier = Modifier.widthIn(max = 340.dp).clip(RoundedCornerShape(14.dp)).background(bg).padding(12.dp),
        )
    }
}

@Composable
private fun ProposalCard(p: Proposal) {
    val state by Repo.state.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    if (p.error != null) {
        Section { Text(p.error); TextButton(onClick = { Repo.update { it.copy(coach = it.coach.copy(pending = null)) } }) { Text("Dismiss") } }
        return
    }
    val previews = remember(p, state.plan) { Coach.apply(state.plan, p.changes).second }
    val picked = remember(p) { mutableStateListOf<Int>().apply { previews.forEachIndexed { i, pv -> if (pv.ok) add(i) } } }
    Section {
        Tag("Proposed by ${p.source}")
        Text(p.summary, style = H3)
        previews.forEachIndexed { i, pv ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(checked = i in picked, enabled = pv.ok, onCheckedChange = { if (it) picked.add(i) else picked.remove(i) })
                Column(Modifier.padding(top = 10.dp)) {
                    Text(pv.label, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(pv.info, fontSize = 15.sp)
                    pv.why?.let { Muted(it) }
                    if (!pv.ok) Text("Can’t apply this one.", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
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
            OutlinedButton(onClick = { Repo.update { it.copy(coach = it.coach.copy(pending = null)) } }) { Text("Not now") }
        }
    }
}

@Composable
private fun ChangeLog(log: List<ChangeLogEntry>) {
    val toast = LocalToast.current
    Section("Applied changes") {
        if (log.isEmpty()) Muted("No changes yet.")
        log.asReversed().forEachIndexed { ri, c ->
            val idx = log.size - 1 - ri
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.summary, fontWeight = FontWeight.SemiBold)
                    Muted("${c.at.take(10)} · ${c.count} change${if (c.count == 1) "" else "s"} · ${c.source}")
                }
                val before = c.before
                if (before != null) TextButton(onClick = {
                    Repo.update { s ->
                        s.copy(plan = before, changelog = s.changelog.take(idx),
                            coach = s.coach.copy(messages = s.coach.messages + ChatMessage("note", "Undid: ${c.summary}")))
                    }
                    toast("Change undone")
                }) { Text("Undo") }
            }
        }
    }
}
