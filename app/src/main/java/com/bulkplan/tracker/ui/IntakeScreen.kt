@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bulkplan.tracker.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bulkplan.tracker.LocalToast
import com.bulkplan.tracker.data.Coach
import com.bulkplan.tracker.data.Repo
import com.bulkplan.tracker.logic.Intake
import com.bulkplan.tracker.logic.PlanImport
import com.bulkplan.tracker.logic.PlanLogic
import com.bulkplan.tracker.net.Ai
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun shareText(ctx: Context, text: String, asFile: Boolean) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Custom plan request")
        if (asFile) {
            val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
            val f = File(dir, "plan-request.md").apply { writeText(text) }
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("plan-request.md", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else putExtra(Intent.EXTRA_TEXT, text)
    }
    ctx.startActivity(Intent.createChooser(send, "Send to an AI app"))
}

/**
 * First-launch questionnaire. It ends with a document you give to any AI, which
 * replies with a plan file the app imports.
 */
@Composable
fun IntakeScreen(startAtEnd: Boolean, onClose: () -> Unit, onImport: (String) -> Unit) {
    val state by Repo.state.collectAsStateWithLifecycle()
    val first = state.settings.intakeStatus.isBlank()
    val ctx = LocalContext.current
    val toast = LocalToast.current
    val clip = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val answers = remember {
        mutableStateMapOf<String, String>().apply {
            putAll(Intake.prefill(state.settings.name, state.plan, state.settings.skipTests))
            putAll(state.intake)
        }
    }
    var step by rememberSaveable { mutableStateOf(if (startAtEnd && state.intake.isNotEmpty()) Intake.STEPS.size else 0) }
    var picking by remember { mutableStateOf<String?>(null) }
    var building by remember { mutableStateOf(false) }
    var buildError by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    LaunchedEffect(step) { scroll.scrollTo(0) }
    val atEnd = step >= Intake.STEPS.size

    fun save(finish: Boolean) {
        val a = answers.toMap()
        Repo.update { s ->
            s.copy(intake = a, settings = s.settings.copy(
                name = s.settings.name.ifBlank { a["name"].orEmpty().trim() },
                skipTests = Intake.skipTests(a),
                intakeStatus = if (finish && s.settings.intakeStatus != "done") "pending" else s.settings.intakeStatus,
            ))
        }
    }
    val doc = if (atEnd) Intake.document(answers.toMap()) else ""
    val saveDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri: Uri? ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(doc.toByteArray()) } }.isSuccess }
            toast(if (ok) "Saved" else "Couldn’t save the file")
        }
    }

    BackHandler { if (step > 0 && !building) step-- else if (!first) onClose() }

    Scaffold(
        containerColor = C.Bg,
        topBar = {
            TopAppBar(
                title = { Text(if (atEnd) "Your plan request" else "Set up your plan") },
                navigationIcon = {
                    if (step > 0 || !first) IconButton(onClick = { if (step > 0) step-- else onClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = C.Bg),
            )
        },
        bottomBar = {
            if (!atEnd) Row(Modifier.fillMaxWidth().background(C.Card).navigationBarsPadding().imePadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val missing = Intake.missing(Intake.STEPS[step], answers)
                Text(if (missing.isEmpty()) "Step ${step + 1} of ${Intake.STEPS.size}" else "Fill in: " + missing.joinToString(", ") { it.label.lowercase() },
                    color = C.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 2)
                Spacer(Modifier.width(12.dp))
                if (step > 0) { OutlinedButton(onClick = { step-- }) { Text("Back") }; Spacer(Modifier.width(8.dp)) }
                Button(enabled = missing.isEmpty(), onClick = { save(step == Intake.STEPS.size - 1); step++ }) { Text(if (step == Intake.STEPS.size - 1) "Finish" else "Next") }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(scroll).padding(16.dp)) {
            Meter(step / Intake.STEPS.size.toFloat(), color = C.Blue)
            Spacer(Modifier.height(16.dp))
            if (!atEnd) {
                val st = Intake.STEPS[step]
                if (step == 0 && first) {
                    Section(highlight = true) {
                        Text("Let’s build a plan around you", style = H2)
                        Muted("Answer a few questions (about 5 minutes). At the end you’ll get a document to give to any AI: ChatGPT, Gemini, Claude or another. It writes your plan in a format this app imports. DEXA scans and bloodwork are optional.")
                        TextButton(onClick = { Repo.update { it.copy(settings = it.settings.copy(intakeStatus = "skipped")) }; onClose() }) {
                            Text("Skip for now and use the starter plan", color = C.BlueSoft)
                        }
                    }
                }
                Text(st.title, style = Big)
                Muted(st.blurb, Modifier.padding(bottom = 12.dp), size = 15)
                st.questions.forEach { q -> QuestionView(q, answers[q.id].orEmpty(), { answers[q.id] = it }, onPickTime = { picking = q.id }) }
            } else {
                Section("Your request is ready", icon = Ico.Sparkle, highlight = true) {
                    Text("1. Send it to any AI chat.\n2. Answer any questions it asks.\n3. Copy its whole reply and import it here.", color = C.Text, fontSize = 16.sp, lineHeight = 24.sp)
                }
                Section("Send it") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("Share text", Ico.Send) { shareText(ctx, doc, asFile = false) }
                        PillButton("Share file", Ico.Clipboard) { shareText(ctx, doc, asFile = true) }
                        PillButton("Copy", Ico.Clipboard) { clip.setText(AnnotatedString(doc)); toast("Copied. Paste it into any AI chat.") }
                        PillButton("Save file", Ico.Bookmark) { saveDoc.launch("plan-request.md") }
                    }
                }
                Section("Or build it right here", icon = Ico.Coach) {
                    if (Ai.hasKey(state.settings)) {
                        Muted("Sends the request to ${Ai.label(state.settings)} (${Ai.cfg(state.settings).model}) with your key. A full plan is long, so this can take a minute or two.")
                        if (building) Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp); Spacer(Modifier.width(12.dp)); Text("Writing your plan…", color = C.Text)
                        } else Button(onClick = {
                            building = true; buildError = null
                            scope.launch {
                                try {
                                    val reply = Ai.buildPlan(Repo.state.value.settings, doc + "\n\n(You're being called from inside the app, so you can't ask questions: make sensible assumptions, list them in your summary, and reply with the code block now.)")
                                    building = false
                                    onImport(reply)
                                } catch (e: Exception) { building = false; buildError = e.message ?: "The AI couldn’t build the plan." }
                            }
                        }) { Text("Build my plan with ${Ai.label(state.settings)}") }
                        buildError?.let { Text(it, color = C.Red, fontSize = 14.sp) }
                    } else Muted("Add an AI API key in Settings › AI coach to skip the copy and paste. Gemini has a free tier.")
                }
                Section("Got the AI’s reply?") {
                    Button(onClick = { onImport("") }, modifier = Modifier.fillMaxWidth()) { Text("Import the plan") }
                    TextButton(onClick = { step = 0 }) { Text("Change my answers", color = C.BlueSoft) }
                    TextButton(onClick = onClose) { Text("Done for now: keep using the current plan", color = C.Muted) }
                }
                Section("Preview") {
                    Text(doc.take(1600) + if (doc.length > 1600) "\n…" else "", color = C.Muted, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    picking?.let { id -> TimeDialog(answers[id] ?: "07:00", onDismiss = { picking = null }) { answers[id] = it; picking = null } }
}

@Composable
private fun QuestionView(q: Intake.Q, value: String, onChange: (String) -> Unit, onPickTime: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(q.label + if (q.required) " *" else "", color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (q.hint.isNotBlank() && q.type in listOf(Intake.Type.CHOICE, Intake.Type.MULTI, Intake.Type.DAYS)) Muted(q.hint)
        Spacer(Modifier.height(6.dp))
        when (q.type) {
            Intake.Type.TEXT, Intake.Type.NUMBER, Intake.Type.LONG ->
                Field(value, onChange, q.hint.ifBlank { q.label }, Modifier.fillMaxWidth(), number = q.type == Intake.Type.NUMBER,
                    singleLine = q.type != Intake.Type.LONG, minLines = if (q.type == Intake.Type.LONG) 2 else 1)
            Intake.Type.CHOICE -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                q.options.forEach { o -> Chip(o, value == o) { onChange(if (value == o) "" else o) } }
            }
            Intake.Type.MULTI -> {
                val set = value.split(", ").filter { it.isNotBlank() }.toSet()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    q.options.forEach { o -> Chip(o, o in set) { onChange(q.options.filter { (it in set) != (it == o) }.joinToString(", ")) } }
                }
            }
            Intake.Type.DAYS -> {
                val set = value.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 2, 3, 4, 5, 6, 0).forEach { d ->
                        Chip(PlanLogic.DAYS[d], d in set) { onChange((if (d in set) set - d else set + d).sorted().joinToString(",")) }
                    }
                }
            }
            Intake.Type.TIME -> PillButton(if (value.isBlank()) "Pick a time" else PlanLogic.fmtT(value), Ico.Timer, onClick = onPickTime)
        }
    }
}

/** Paste or open the AI's reply, check it, preview it and switch to it. */
@Composable
fun ImportPlanScreen(initial: String, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val ctx = LocalContext.current
    val toast = LocalToast.current
    val clip = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(initial) }
    var result by remember { mutableStateOf<PlanImport.Result?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    fun check() {
        error = null; result = null
        try { result = PlanImport.parse(text) } catch (e: Exception) { error = e.message ?: "Couldn’t read that plan." }
    }
    LaunchedEffect(Unit) { if (initial.isNotBlank()) check() }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val t = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull() }
            if (t == null) toast("Couldn’t open that file") else { text = t; check() }
        }
    }

    Scaffold(
        containerColor = C.Bg,
        topBar = {
            TopAppBar(
                title = { Text("Import a plan from AI") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = C.Bg),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
            val r = result
            if (r == null) {
                Muted("Paste the AI’s whole reply (the part with the ```bulkplan code block), or open it as a file.", Modifier.padding(bottom = 10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Paste", Ico.Clipboard) { text = clip.getText()?.text.orEmpty(); if (text.isNotBlank()) check() else toast("The clipboard is empty") }
                    PillButton("Open file", Ico.Note) { open.launch(arrayOf("text/*", "application/json", "application/octet-stream", "*/*")) }
                }
                Spacer(Modifier.height(10.dp))
                Field(text, { text = it }, "AI reply", Modifier.fillMaxWidth(), singleLine = false, minLines = 8)
                Spacer(Modifier.height(10.dp))
                Button(enabled = text.isNotBlank(), onClick = { check() }, modifier = Modifier.fillMaxWidth()) { Text("Check the plan") }
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Red.copy(alpha = .12f)).border(1.dp, C.Red.copy(alpha = .4f), RoundedCornerShape(14.dp)).padding(14.dp)) {
                        Text(it, color = C.Text, fontSize = 15.sp)
                    }
                }
            } else {
                Section("Your new plan", icon = Ico.Sparkle, highlight = true) {
                    if (r.summary.isNotBlank()) Text(r.summary, color = C.Text, fontSize = 15.sp, lineHeight = 21.sp)
                    PlanImport.overview(r.plan).forEach { Text("• $it", color = C.Text, fontSize = 15.sp) }
                }
                if (r.warnings.isNotEmpty()) Section("Check these", icon = Ico.Bulb) {
                    r.warnings.forEach { Text("• $it", color = C.Yellow, fontSize = 14.sp) }
                }
                Section("Workouts", icon = Ico.Dumbbell) {
                    r.plan.workouts.values.sortedBy { (it.day + 6) % 7 }.forEach { w ->
                        Text("${PlanLogic.DAYN[w.day]}: ${w.name}", style = H3)
                        Muted(w.exercises.joinToString(" · ") { "${it.name} ${it.sets}×${it.reps}" })
                        Spacer(Modifier.height(6.dp))
                    }
                }
                Section("A typical day", icon = Ico.Calendar) {
                    val d = r.plan.workouts.values.firstOrNull()?.day ?: 1
                    PlanLogic.itemsFor(r.plan, PlanLogic.addD(r.plan.startDate, ((d - 1 + 7) % 7).toLong())).forEach { i ->
                        Text("${PlanLogic.fmtT(i.time)}  ${i.title}" + (i.kcal?.let { " · $it kcal" } ?: ""), color = C.Text, fontSize = 14.sp)
                    }
                }
                Button(onClick = {
                    Repo.changePlan("Switched to the custom plan from your AI", source = "AI") { r.plan }
                    Repo.update { s -> s.copy(settings = s.settings.copy(intakeStatus = "done"), coach = Coach()) }
                    toast("New plan in place. Undo it from Coach › More if needed.")
                    onClose()
                }, modifier = Modifier.fillMaxWidth()) { Text("Use this plan") }
                Muted("Your logs stay. The coach chat starts fresh so it sees the new plan.", Modifier.padding(top = 6.dp))
                TextButton(onClick = { result = null }) { Text("Back to the text", color = C.BlueSoft) }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
