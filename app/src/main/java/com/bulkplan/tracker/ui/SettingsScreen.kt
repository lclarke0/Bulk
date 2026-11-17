@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bulkplan.tracker.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bulkplan.tracker.LocalToast
import com.bulkplan.tracker.data.DefaultPlan
import com.bulkplan.tracker.data.Repo
import com.bulkplan.tracker.data.ScheduleItem
import com.bulkplan.tracker.net.Claude
import com.bulkplan.tracker.net.Garmin
import com.bulkplan.tracker.work.Reminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun SettingsScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val state by Repo.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    val clip = LocalClipboardManager.current
    val plan = state.plan
    val st = state.settings

    var start by remember { mutableStateOf(plan.startDate) }
    var sw by remember { mutableStateOf(plan.profile.startWeight.toString()) }
    var cal by remember { mutableStateOf(plan.targets.calories.toString()) }
    var ppl by remember { mutableStateOf(plan.targets.proteinPerLb.toString()) }
    var steps by remember { mutableStateOf(plan.targets.steps.toString()) }
    val weak = remember { mutableStateListOf<String>().apply { addAll(plan.weakMuscles) } }

    var claudeKey by remember { mutableStateOf(st.claudeKey) }
    var model by remember { mutableStateOf(st.claudeModel) }
    var garminKey by remember { mutableStateOf(st.garminKey) }
    var usda by remember { mutableStateOf(st.usdaKey) }
    var diag by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }

    val exportDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(Repo.exportBackup().toByteArray()) } }.isSuccess }
            toast(if (ok) "Backup saved" else "Couldn’t save the backup")
        }
    }
    val importDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull() }
            toast(if (text != null && Repo.importBackup(text)) "Backup restored" else "That isn’t a Bulk Plan backup")
            if (text != null) onClose()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Section("Plan") {
                val num = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                OutlinedTextField(start, { start = it }, label = { Text("Start date (YYYY-MM-DD, a Monday)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(sw, { sw = it }, label = { Text("Start weight (lb)") }, singleLine = true, keyboardOptions = num, modifier = Modifier.weight(1f))
                    OutlinedTextField(cal, { cal = it }, label = { Text("Calories/day") }, singleLine = true, keyboardOptions = num, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(ppl, { ppl = it }, label = { Text("Protein per lb") }, singleLine = true, keyboardOptions = num, modifier = Modifier.weight(1f))
                    OutlinedTextField(steps, { steps = it }, label = { Text("Steps/day") }, singleLine = true, keyboardOptions = num, modifier = Modifier.weight(1f))
                }
                Muted("Weakest muscles (+1 set in weeks 13–25)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DefaultPlan.MUSCLES.forEach { m -> FilterChip(selected = m in weak, onClick = { if (m in weak) weak.remove(m) else weak.add(m) }, label = { Text(m) }) }
                }
                Button(onClick = {
                    val okDate = runCatching { LocalDate.parse(start.trim()) }.isSuccess
                    if (!okDate) { toast("Start date must look like 2026-11-23"); return@Button }
                    Repo.changePlan("Updated plan settings") { p ->
                        p.copy(
                            startDate = start.trim(),
                            profile = p.profile.copy(startWeight = sw.toDoubleOrNull() ?: p.profile.startWeight),
                            targets = p.targets.copy(
                                calories = cal.toIntOrNull() ?: p.targets.calories,
                                proteinPerLb = ppl.toDoubleOrNull() ?: p.targets.proteinPerLb,
                                steps = steps.toIntOrNull() ?: p.targets.steps,
                            ),
                            weakMuscles = weak.toList(),
                        )
                    }
                    toast("Plan settings saved")
                }) { Text("Save plan settings") }
            }

            Section("Claude") {
                Muted("Coach check-ins, meal estimates and label photos use the Claude API with your own key, billed per use by Anthropic and separate from a Claude subscription. Create a key at console.anthropic.com.")
                OutlinedTextField(claudeKey, { claudeKey = it }, label = { Text("Claude API key") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(model, { model = it }, label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        Repo.update { it.copy(settings = it.settings.copy(claudeKey = claudeKey.trim(), claudeModel = model.trim().ifBlank { "claude-sonnet-5-5" })) }
                        toast("Saved")
                    }) { Text("Save") }
                    OutlinedButton(enabled = !working, onClick = {
                        working = true
                        scope.launch {
                            toast(try {
                                Claude.chat(claudeKey.trim(), model.trim(), listOf(com.bulkplan.tracker.data.ChatMessage("user", "Reply with just: Connected.")))
                                "Claude is connected"
                            } catch (e: Exception) { e.message ?: "Claude test failed" })
                            working = false
                        }
                    }) { Text("Test") }
                }
            }

            Section("Garmin") {
                Muted("1) Open the Garmin key page and sign in. 2) Create a Personal API key (pick the longest expiry). 3) Paste it here. The app syncs steps, sleep, resting heart rate, Body Battery and weight every few hours.")
                OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Garmin.KEY_PAGE))) }) { Text("Open Garmin key page") }
                OutlinedTextField(garminKey, { garminKey = it }, label = { Text("Garmin Personal API key") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !working, onClick = {
                        Repo.update { it.copy(settings = it.settings.copy(garminKey = garminKey.trim())) }
                        ctx.getSharedPreferences("garmin", 0).edit().clear().apply()
                        Reminders.ensureGarminSync(ctx)
                        working = true
                        scope.launch { toast(runCatching { Garmin.sync(ctx, 14) }.getOrElse { it.message ?: "Garmin sync failed" }); working = false }
                    }) { Text("Save and sync") }
                    OutlinedButton(enabled = !working && garminKey.isNotBlank(), onClick = {
                        working = true
                        scope.launch { diag = Garmin.diagnose(ctx); working = false }
                    }) { Text("Test connection") }
                }
                if (st.garminLastSync.isNotBlank()) Muted("Last sync ${st.garminLastSync.take(16).replace('T', ' ')} UTC · ${st.garminStatus}")
                diag?.let { d ->
                    Text(d, fontSize = 12.sp, lineHeight = 15.sp)
                    TextButton(onClick = { clip.setText(AnnotatedString(d)); toast("Copied. Paste it to Claude if a number doesn’t fill in.") }) { Text("Copy this report") }
                }
            }

            Section("Food search") {
                Muted("Barcode lookups use Open Food Facts and need no key. Food search uses USDA FoodData Central: it works without a key at a low hourly limit. A free key from api.data.gov raises it to 1,000 searches an hour.")
                OutlinedTextField(usda, { usda = it }, label = { Text("USDA API key (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { Repo.update { it.copy(settings = it.settings.copy(usdaKey = usda.trim())) }; toast("Saved") }) { Text("Save") }
            }

            Section("Reminders") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Schedule notifications", modifier = Modifier.weight(1f))
                    Switch(checked = st.alerts, onCheckedChange = { v -> Repo.update { it.copy(settings = it.settings.copy(alerts = v)) } })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("9:00 am nudge if no weigh-in", modifier = Modifier.weight(1f))
                    Switch(checked = st.weighNudge, onCheckedChange = { v -> Repo.update { it.copy(settings = it.settings.copy(weighNudge = v)) } })
                }
                if (!Reminders.canExact(ctx) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Muted("Reminders may arrive a few minutes late. Allow exact alarms to get them on the minute.")
                    OutlinedButton(onClick = {
                        ctx.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName)))
                    }) { Text("Allow exact alarms") }
                }
                OutlinedButton(onClick = {
                    Reminders.notify(ctx, com.bulkplan.tracker.logic.PlanLogic.today(), ScheduleItem("test", "12:00", "task", "Test reminder", "Reminders are working."))
                    Reminders.schedule(ctx)
                }) { Text("Send a test notification") }
            }

            Section("Backup") {
                Muted("Save everything to a file, or restore from this app or the web tracker’s backup.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportDoc.launch("bulk-plan-backup-${LocalDate.now()}.json") }) { Text("Save backup") }
                    OutlinedButton(onClick = { importDoc.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Restore backup") }
                    OutlinedButton(onClick = { clip.setText(AnnotatedString(Repo.exportBackup())); toast("Backup copied") }) { Text("Copy backup") }
                }
            }

            Section("Reset") {
                Muted("Puts the plan back to the original from the plan doc. Your logs stay, and you can undo it from Coach.")
                OutlinedButton(onClick = {
                    Repo.changePlan("Reset the plan to the original") { DefaultPlan.create(it.startDate) }
                    toast("Plan reset")
                }) { Text("Reset plan") }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
