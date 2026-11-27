package com.bulkplan.tracker.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class HttpError(val code: Int, val body: String) : Exception("HTTP $code: ${body.take(300)}")

object Http {
    private const val UA = "BulkPlanTracker/1.0 (Android; personal use)"

    suspend fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        timeoutMs: Int = 30_000,
    ): String = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = timeoutMs
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                if (body.isNotEmpty() && headers.keys.none { it.equals("content-type", true) }) c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw HttpError(code, text)
            text
        } finally {
            c.disconnect()
        }
    }

    /** GET that returns raw bytes, for file downloads. */
    suspend fun bytes(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 60_000): ByteArray = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = timeoutMs
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept", "*/*")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            val code = c.responseCode
            if (code !in 200..299) throw HttpError(code, c.errorStream?.bufferedReader()?.use { it.readText() } ?: "")
            c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }
}
