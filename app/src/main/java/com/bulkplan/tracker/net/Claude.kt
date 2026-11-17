package com.bulkplan.tracker.net

import com.bulkplan.tracker.data.ChatMessage
import com.bulkplan.tracker.data.Repo
import com.bulkplan.tracker.logic.Macros
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Calls the Claude Messages API with your own API key (set in Settings). */
object Claude {
    private const val URL = "https://api.anthropic.com/v1/messages"

    class NoKey : Exception("Add your Claude API key in Settings first.")

    private suspend fun post(key: String, body: JsonObject): String {
        if (key.isBlank()) throw NoKey()
        val text = try {
            Http.request(URL, "POST", mapOf(
                "x-api-key" to key.trim(),
                "anthropic-version" to "2023-06-01",
                "content-type" to "application/json",
            ), body.toString(), timeoutMs = 120_000)
        } catch (e: HttpError) {
            val msg = runCatching { Repo.json.parseToJsonElement(e.body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull }.getOrNull()
            throw Exception(when (e.code) {
                401 -> "Claude rejected the API key. Check it in Settings."
                429 -> "Too many requests right now. Try again in a minute."
                529, 503 -> "Claude is busy right now. Try again shortly."
                else -> msg ?: "Claude couldn’t reply (HTTP ${e.code})."
            })
        }
        val root = Repo.json.parseToJsonElement(text).jsonObject
        return root["content"]?.jsonArray?.mapNotNull { blk ->
            val o = blk.jsonObject
            if (o["type"]?.jsonPrimitive?.contentOrNull == "text") o["text"]?.jsonPrimitive?.contentOrNull else null
        }?.joinToString("\n")?.trim().orEmpty()
    }

    /** Coach conversation. Messages must alternate and end on a user turn. */
    suspend fun chat(key: String, model: String, messages: List<ChatMessage>): String {
        val clean = mutableListOf<ChatMessage>()
        for (m in messages.filter { (it.role == "user" || it.role == "assistant") && it.failed != true && it.content.isNotBlank() }) {
            if (clean.isNotEmpty() && clean.last().role == m.role) clean[clean.size - 1] = clean.last().copy(content = clean.last().content + "\n\n" + m.content)
            else clean += m
        }
        while (clean.isNotEmpty() && clean.first().role != "user") clean.removeAt(0)
        // Keep the first message (it carries the plan and logs) plus the recent turns.
        val trimmed = if (clean.size > 21) {
            var tail = clean.drop(1).takeLast(20)
            while (tail.isNotEmpty() && tail.first().role != "assistant") tail = tail.drop(1)
            listOf(clean.first()) + tail
        } else clean
        val body = buildJsonObject {
            put("model", model.ifBlank { "claude-sonnet-5-5" })
            put("max_tokens", 2000)
            put("messages", buildJsonArray {
                trimmed.forEach { m -> add(buildJsonObject { put("role", m.role); put("content", m.content) }) }
            })
        }
        return post(key, body)
    }

    data class FoodGuess(val name: String, val macros: Macros, val note: String = "")

    private const val FOOD_RULES = """Reply with ONLY a JSON object, no other text, in this shape:
{"items":[{"name":"short food name with portion","kcal":0,"protein":0,"carbs":0,"fat":0}],"note":"one short line on how sure you are"}
Numbers are for the whole portion eaten, in kcal and grams. Use typical US portion sizes when none is given."""

    /** Estimates calories and macros from a description like "Chipotle chicken burrito, no beans". */
    suspend fun estimateMeal(key: String, model: String, description: String): List<FoodGuess> {
        val body = buildJsonObject {
            put("model", model.ifBlank { "claude-sonnet-5-5" })
            put("max_tokens", 800)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", "Estimate the calories and macros for what I ate: \"$description\"\n\n$FOOD_RULES")
                })
            })
        }
        return parseFood(post(key, body))
    }

    /** Reads a nutrition label (or a plate of food) from a photo. */
    suspend fun readPhoto(key: String, model: String, jpegBase64: String, servings: Double, hint: String): List<FoodGuess> {
        val ask = "This is a photo of a nutrition facts label or a meal. If it's a label, read the per-serving values and multiply by $servings serving(s). If it's a meal, estimate the portion shown. ${if (hint.isNotBlank()) "The food is: $hint." else ""}\n\n$FOOD_RULES"
        val body = buildJsonObject {
            put("model", model.ifBlank { "claude-sonnet-5-5" })
            put("max_tokens", 800)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "image")
                            put("source", buildJsonObject {
                                put("type", "base64"); put("media_type", "image/jpeg"); put("data", jpegBase64)
                            })
                        })
                        add(buildJsonObject { put("type", "text"); put("text", ask) })
                    })
                })
            })
        }
        return parseFood(post(key, body))
    }

    private fun parseFood(text: String): List<FoodGuess> {
        val i = text.indexOf('{'); val j = text.lastIndexOf('}')
        if (i < 0 || j <= i) throw Exception("Claude didn’t return food numbers. Try describing it differently.")
        val o = Repo.json.parseToJsonElement(text.substring(i, j + 1)).jsonObject
        val note = o["note"]?.jsonPrimitive?.contentOrNull ?: ""
        val items = (o["items"] as? JsonArray).orEmpty().mapNotNull { e ->
            val it = e as? JsonObject ?: return@mapNotNull null
            fun d(k: String) = it[k]?.jsonPrimitive?.doubleOrNull ?: 0.0
            FoodGuess(it["name"]?.jsonPrimitive?.contentOrNull ?: "Food", Macros(d("kcal"), d("protein"), d("carbs"), d("fat")), note)
        }
        if (items.isEmpty()) throw Exception("Claude couldn’t identify the food.")
        return items
    }
}
