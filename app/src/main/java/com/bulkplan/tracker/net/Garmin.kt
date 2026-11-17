package com.bulkplan.tracker.net

import android.content.Context
import android.util.Base64
import com.bulkplan.tracker.data.GarminDay
import com.bulkplan.tracker.data.Repo
import com.bulkplan.tracker.logic.PlanLogic
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.ZoneId

/**
 * Garmin Connect Personal API: you create a key at
 * connect.garmin.com/app/settings/personalApiToken, the app swaps it for a
 * 24-hour access token, then reads your own data (pull only).
 */
object Garmin {
    private const val BASE = "https://personal.connectapi.garmin.com"
    private const val TOKEN_PATH = "/personalapitoken-service/personal-api/personal-api-key/token"
    const val KEY_PAGE = "https://connect.garmin.com/app/settings/personalApiToken"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("garmin", Context.MODE_PRIVATE)

    private suspend fun token(ctx: Context, apiKey: String, force: Boolean = false): String {
        val p = prefs(ctx)
        val cached = p.getString("token", null)
        val exp = p.getLong("exp", 0L)
        if (!force && cached != null && System.currentTimeMillis() < exp - 5 * 60_000) return cached
        val text = try {
            Http.request(BASE + TOKEN_PATH, "POST", mapOf("Authorization" to "ApiKey ${apiKey.trim()}"), body = "")
        } catch (e: HttpError) {
            throw Exception(if (e.code == 401 || e.code == 403) "Garmin rejected the Personal API key. Make a new one and paste it in Settings." else "Garmin sign-in failed (HTTP ${e.code}).")
        }
        val (tok, expMs) = parseToken(text)
        p.edit().putString("token", tok).putLong("exp", expMs).apply()
        return tok
    }

    private fun parseToken(text: String): Pair<String, Long> {
        val trimmed = text.trim()
        var tok: String? = null
        var expMs: Long? = null
        val el = runCatching { Repo.json.parseToJsonElement(trimmed) }.getOrNull()
        if (el is JsonObject) {
            tok = listOf("access_token", "accessToken", "token", "jwt").firstNotNullOfOrNull { el[it]?.jsonPrimitive?.contentOrNull }
            el["expires_in"]?.jsonPrimitive?.longOrNull?.let { expMs = System.currentTimeMillis() + it * 1000 }
            el["expires_at"]?.jsonPrimitive?.longOrNull?.let { expMs = if (it > 10_000_000_000L) it else it * 1000 }
        } else if (el is JsonPrimitive) tok = el.contentOrNull
        if (tok == null && trimmed.count { it == '.' } == 2 && !trimmed.contains(' ')) tok = trimmed.trim('"')
        tok ?: throw Exception("Garmin sent an unexpected sign-in reply.")
        if (expMs == null) expMs = jwtExp(tok)
        return tok to (expMs ?: (System.currentTimeMillis() + 23 * 3600_000L))
    }

    private fun jwtExp(jwt: String): Long? = runCatching {
        val payload = jwt.split(".")[1]
        val bytes = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        Repo.json.parseToJsonElement(String(bytes)).jsonObject["exp"]?.jsonPrimitive?.longOrNull?.times(1000)
    }.getOrNull()

    private suspend fun get(ctx: Context, apiKey: String, path: String): String {
        return try {
            Http.request(BASE + path, headers = mapOf("Authorization" to "Bearer ${token(ctx, apiKey)}"))
        } catch (e: HttpError) {
            if (e.code == 401) Http.request(BASE + path, headers = mapOf("Authorization" to "Bearer ${token(ctx, apiKey, force = true)}")) else throw e
        }
    }

    private fun paths(s: String, e: String) = linkedMapOf(
        "Daily summaries" to "/usersummary-service/v2/dailysummaries?startDate=$s&endDate=$e",
        "Steps" to "/usersummary-service/stats/steps/daily/$s/$e",
        "Sleep" to "/sleep-service/sleep/dailySleepSummariesByDate?startDate=$s&endDate=$e",
        "Weight" to "/weight-service/weight/range/$s/$e?includeAll=true",
        "Body Battery" to "/wellness-service/wellness/bodyBattery/reports/daily?startDate=$s&endDate=$e",
    )

    /** Pulls the last [days] days and stores them on each day's log. Returns a short status line. */
    suspend fun sync(ctx: Context, days: Int = 7): String {
        val key = Repo.state.value.settings.garminKey
        if (key.isBlank()) return "Add your Garmin Personal API key in Settings."
        val e = PlanLogic.today(); val s = PlanLogic.addD(e, -(days - 1).toLong())
        val acc = mutableMapOf<String, MutableMap<String, Double>>()
        val failures = mutableListOf<String>()
        for ((label, path) in paths(s, e)) {
            try {
                collect(Repo.json.parseToJsonElement(get(ctx, key, path)), acc)
            } catch (ex: Exception) {
                failures += label
                if (ex.message?.contains("rejected") == true) throw ex
            }
        }
        val now = Instant.now().toString()
        var n = 0
        acc.forEach { (date, v) ->
            if (date < s || date > e) return@forEach
            val g = GarminDay(
                steps = v["steps"], sleepH = v["sleepS"]?.let { it / 3600.0 }, restingHr = v["rhr"],
                weight = v["weightLb"], bodyBattery = v["bb"], syncedAt = now,
            )
            Repo.updateLog(date) { it.copy(garmin = g) }
            n++
        }
        val status = if (failures.size == paths(s, e).size) "Couldn’t reach Garmin. Check your connection and key."
        else "Synced $n day${if (n == 1) "" else "s"}" + if (failures.isNotEmpty()) " (no ${failures.joinToString(", ").lowercase()} data)" else ""
        Repo.update { it.copy(settings = it.settings.copy(garminLastSync = now, garminStatus = status)) }
        return status
    }

    /** Raw look at each endpoint, so field names can be checked if something doesn't fill in. */
    suspend fun diagnose(ctx: Context): String {
        val key = Repo.state.value.settings.garminKey
        if (key.isBlank()) return "No key yet."
        val e = PlanLogic.today(); val s = PlanLogic.addD(e, -2)
        val sb = StringBuilder()
        try { token(ctx, key, force = true); sb.append("Sign-in: OK\n\n") } catch (ex: Exception) { return "Sign-in failed: ${ex.message}" }
        for ((label, path) in paths(s, e)) {
            sb.append("$label: ")
            try { sb.append(get(ctx, key, path).take(500)) } catch (ex: Exception) { sb.append("failed: ${ex.message}") }
            sb.append("\n\n")
        }
        return sb.toString()
    }

    // ---------- tolerant parsing: Garmin's field names vary between services ----------
    private val DATE_KEYS = listOf("calendarDate", "summaryDate", "date", "sleepDate", "calendar_date", "measurementDate")
    private val STEPS = listOf("totalSteps", "steps", "stepCount", "totalStepCount", "value")
    private val RHR = listOf("restingHeartRate", "restingHeartRateValue", "restingHR")
    private val SLEEP = listOf("sleepTimeSeconds", "sleepTimeInSeconds", "totalSleepSeconds", "sleepDurationInSeconds", "durationInSeconds")
    private val WEIGHT = listOf("weight", "weightInGrams")
    private val BB = listOf("bodyBatteryHighestValue", "highestValue", "highest", "maxBodyBattery", "charged")

    private fun dateOf(o: JsonObject): String? {
        for (k in DATE_KEYS) {
            val p = o[k] as? JsonPrimitive ?: continue
            p.contentOrNull?.takeIf { Regex("^\\d{4}-\\d{2}-\\d{2}").containsMatchIn(it) }?.let { return it.take(10) }
            p.longOrNull?.takeIf { it > 1_000_000_000_000L }?.let {
                return Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString()
            }
        }
        return null
    }

    private fun num(o: JsonObject, keys: List<String>): Double? =
        keys.firstNotNullOfOrNull { (o[it] as? JsonPrimitive)?.doubleOrNull }

    private fun collect(el: JsonElement, acc: MutableMap<String, MutableMap<String, Double>>, inheritedDate: String? = null) {
        when (el) {
            is JsonArray -> el.forEach { collect(it, acc, inheritedDate) }
            is JsonObject -> {
                val d = dateOf(el) ?: inheritedDate
                if (d != null) {
                    val m = acc.getOrPut(d) { mutableMapOf() }
                    // Steps only from objects that look like step/summary records.
                    if (el.keys.any { it.contains("step", true) }) num(el, STEPS)?.takeIf { it > 0 }?.let { m["steps"] = maxOf(m["steps"] ?: 0.0, it) }
                    num(el, RHR)?.takeIf { it in 25.0..150.0 }?.let { m["rhr"] = it }
                    num(el, SLEEP)?.takeIf { it in 1800.0..64800.0 && el.keys.any { k -> k.contains("sleep", true) } }?.let { m["sleepS"] = it }
                    num(el, WEIGHT)?.takeIf { it > 0 }?.let { w -> m["weightLb"] = if (w > 1000) w / 453.592 else w * 2.20462 }
                    if (el.keys.any { it.contains("bodyBattery", true) || it.contains("charged", true) }) num(el, BB)?.takeIf { it in 0.0..100.0 }?.let { m["bb"] = it }
                }
                el.values.forEach { if (it is JsonObject || it is JsonArray) collect(it, acc, d) }
            }
            else -> {}
        }
    }
}
