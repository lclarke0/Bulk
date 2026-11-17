package com.bulkplan.tracker.data

import android.content.Context
import com.bulkplan.tracker.work.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.time.Instant

/**
 * Single in-process store shared by the UI, notification actions and background sync.
 * Everything lives in two JSON files in the app's private storage.
 */
object Repo {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
        isLenient = true
    }

    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null
    private var loaded = false

    private val _state = MutableStateFlow(AppState())
    private val _logs = MutableStateFlow<Map<String, DayLog>>(emptyMap())
    val state: StateFlow<AppState> get() = _state
    val logs: StateFlow<Map<String, DayLog>> get() = _logs

    private fun stateFile() = File(app.filesDir, "state.json")
    private fun logsFile() = File(app.filesDir, "logs.json")

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        app = context.applicationContext
        runCatching { if (stateFile().exists()) _state.value = json.decodeFromString(AppState.serializer(), stateFile().readText()) }
        runCatching {
            if (logsFile().exists()) {
                val obj = json.parseToJsonElement(logsFile().readText()).jsonObject
                _logs.value = decodeLogs(obj)
            }
        }
        loaded = true
    }

    /** Decodes logs one day at a time so a single bad day never loses the rest. */
    fun decodeLogs(obj: JsonObject): Map<String, DayLog> =
        obj.mapNotNull { (k, v) -> runCatching { k to json.decodeFromJsonElement<DayLog>(v).copy(date = k) }.getOrNull() }.toMap()

    fun log(date: String): DayLog = _logs.value[date] ?: DayLog(date)

    fun update(f: (AppState) -> AppState) {
        val old = _state.value
        val new = f(old)
        if (new == old) return
        _state.value = trim(new)
        save()
        if (old.plan.schedule != new.plan.schedule || old.plan.workouts != new.plan.workouts ||
            old.settings.alerts != new.settings.alerts || old.settings.weighNudge != new.settings.weighNudge ||
            old.plan.startDate != new.plan.startDate
        ) Reminders.schedule(app)
    }

    fun updateLog(date: String, f: (DayLog) -> DayLog) {
        val cur = log(date)
        val new = f(cur)
        if (new == cur) return
        _logs.value = _logs.value + (date to new)
        save()
    }

    fun replaceAll(state: AppState, logs: Map<String, DayLog>) {
        _state.value = trim(state)
        _logs.value = logs
        save()
        Reminders.schedule(app)
    }

    /** Records a plan change with an undo snapshot. */
    fun changePlan(summary: String, source: String = "you", count: Int = 1, f: (Plan) -> Plan) = update { s ->
        val next = f(s.plan)
        if (next == s.plan) s
        else s.copy(plan = next, changelog = s.changelog + ChangeLogEntry(Instant.now().toString(), summary, count, source, s.plan))
    }

    private fun trim(s: AppState): AppState {
        var log = s.changelog.takeLast(12)
        // Keep undo snapshots only for the last 5 changes.
        log = log.mapIndexed { i, c -> if (i < log.size - 5) c.copy(before = null) else c }
        var msgs = s.coach.messages
        if (msgs.size > 60) {
            val first = msgs.firstOrNull { it.role == "user" }
            msgs = msgs.takeLast(59)
            if (first != null && first !in msgs) msgs = listOf(first) + msgs
        }
        return s.copy(changelog = log, coach = s.coach.copy(messages = msgs))
    }

    private fun save() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(300)
            writeNow()
        }
    }

    @Synchronized
    fun writeNow() {
        if (!loaded) return
        atomicWrite(stateFile(), json.encodeToString(AppState.serializer(), _state.value))
        atomicWrite(logsFile(), json.encodeToString(MapSerializer(String.serializer(), DayLog.serializer()), _logs.value))
    }

    private fun atomicWrite(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.writeText(text); tmp.delete() }
    }

    fun exportBackup(): String = json.encodeToString(Backup.serializer(), Backup(S = _state.value, logs = _logs.value))

    /** Accepts a backup from this app or from the web tracker. */
    fun importBackup(text: String): Boolean {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return false
        val s = root["S"] ?: return false
        val st = runCatching { json.decodeFromJsonElement<AppState>(s) }.getOrNull() ?: return false
        val lg = (root["logs"] as? JsonObject)?.let { decodeLogs(it) } ?: emptyMap()
        replaceAll(st, lg)
        return true
    }
}
