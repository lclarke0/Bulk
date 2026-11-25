package com.bulkplan.tracker.net

import com.bulkplan.tracker.data.ChatMessage
import com.bulkplan.tracker.data.Repo
import com.bulkplan.tracker.data.Settings
import com.bulkplan.tracker.logic.Macros
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Talks to whichever AI service you pick in Settings, with your own API key.
 * Anthropic uses its Messages API; every other provider uses the OpenAI-style
 * chat completions API, which Gemini, OpenAI, DeepSeek, OpenRouter, Mistral,
 * Groq, xAI, Together and local servers like Ollama all accept.
 */
object Ai {
    data class Provider(
        val id: String,
        val name: String,
        val base: String,
        val defaultModel: String,
        val keyUrl: String,
        val note: String,
        val vision: Boolean = true,
        val anthropic: Boolean = false,
    )

    val PROVIDERS = listOf(
        Provider("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-3.8-flash",
            "https://aistudio.google.com/apikey", "Cheapest place to start: Google has a free tier, then low per-use prices. Reads label photos."),
        Provider("deepseek", "DeepSeek", "https://api.deepseek.com", "deepseek-chat",
            "https://platform.deepseek.com/api_keys", "Very cheap for coaching and meal estimates. Text only, so label photos won’t work.", vision = false),
        Provider("openai", "OpenAI (ChatGPT)", "https://api.openai.com/v1", "gpt-5-mini",
            "https://platform.openai.com/api-keys", "Billed per use, separate from a ChatGPT subscription."),
        Provider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "openrouter/auto",
            "https://openrouter.ai/keys", "One key for hundreds of models from every company. Tap Load models to pick one."),
        Provider("mistral", "Mistral", "https://api.mistral.ai/v1", "mistral-small-latest",
            "https://console.mistral.ai/api-keys", "Low-cost models with a free experiment tier."),
        Provider("anthropic", "Anthropic (Claude)", "https://api.anthropic.com/v1", "claude-haiku-5-5",
            "https://console.anthropic.com/settings/keys", "Haiku is the cheapest Claude model.", anthropic = true),
        Provider("custom", "Other (OpenAI-compatible)", "", "",
            "", "Groq, xAI, Together, Ollama and others. Paste the base URL, usually ending in /v1."),
    )

    fun provider(id: String) = PROVIDERS.firstOrNull { it.id == id } ?: PROVIDERS.first()

    data class Cfg(val p: Provider, val key: String, val model: String, val base: String)

    fun providerId(s: Settings): String = s.aiProvider.ifBlank { if (s.claudeKey.isNotBlank()) "anthropic" else "gemini" }

    fun keyFor(s: Settings, id: String): String = s.aiKeys[id] ?: if (id == "anthropic") s.claudeKey else ""

    fun modelFor(s: Settings, id: String): String =
        s.aiModels[id]?.takeIf { it.isNotBlank() }
            ?: (if (id == "anthropic" && s.claudeKey.isNotBlank() && s.claudeModel.isNotBlank()) s.claudeModel else provider(id).defaultModel)

    fun cfg(s: Settings): Cfg {
        val id = providerId(s); val p = provider(id)
        return Cfg(p, keyFor(s, id).trim(), modelFor(s, id).trim(), (if (id == "custom") s.aiBaseUrl else p.base).trim().trimEnd('/'))
    }

    fun hasKey(s: Settings): Boolean = cfg(s).let { it.key.isNotBlank() || (it.p.id == "custom" && it.base.isNotBlank()) }

    /** Short name for the current AI, used in buttons and messages. */
    fun label(s: Settings): String = provider(providerId(s)).name.substringBefore(" (")

    class NoKey : Exception("Add an AI API key in Settings › AI coach first.")

    // ---------- low-level calls ----------

    private fun errorMessage(c: Cfg, e: HttpError): String {
        val msg = runCatching {
            val root = Repo.json.parseToJsonElement(e.body)
            val err = (root as? JsonArray)?.firstOrNull()?.jsonObject?.get("error") ?: root.jsonObject["error"]
            (err as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull ?: (err as? JsonPrimitive)?.contentOrNull
        }.getOrNull()
        val who = c.p.name.substringBefore(" (")
        return when (e.code) {
            401, 403 -> "$who rejected the API key. Check it in Settings › AI coach." + (msg?.let { " ($it)" } ?: "")
            402 -> "$who says the account is out of credit." + (msg?.let { " ($it)" } ?: "")
            404 -> "$who couldn’t find model “${c.model}”. Tap Load models in Settings › AI coach and pick one."
            429 -> "Too many requests or quota used up at $who. Try again in a minute." + (msg?.let { " ($it)" } ?: "")
            500, 502, 503, 529 -> "$who is busy right now. Try again shortly."
            else -> msg ?: "$who couldn’t reply (HTTP ${e.code})."
        }
    }

    /** Sends a conversation. Each message's content is a plain string or a list of parts (text and images). */
    private suspend fun send(c: Cfg, messages: List<Pair<String, JsonElement>>, maxTokens: Int, timeoutMs: Int, system: String? = null): String {
        if (c.key.isBlank() && !(c.p.id == "custom" && c.base.isNotBlank())) throw NoKey()
        if (c.base.isBlank()) throw Exception("Add the base URL for your AI service in Settings › AI coach.")
        if (c.model.isBlank()) throw Exception("Pick a model in Settings › AI coach (tap Load models).")
        return if (c.p.anthropic) sendAnthropic(c, messages, maxTokens, timeoutMs, system) else sendOpenAi(c, messages, maxTokens, timeoutMs, system)
    }

    private suspend fun sendAnthropic(c: Cfg, messages: List<Pair<String, JsonElement>>, maxTokens: Int, timeoutMs: Int, system: String?): String {
        val body = buildJsonObject {
            put("model", c.model)
            put("max_tokens", maxTokens)
            if (!system.isNullOrBlank()) put("system", system)
            put("messages", buildJsonArray {
                messages.forEach { (role, content) ->
                    add(buildJsonObject { put("role", role); put("content", toAnthropicContent(content)) })
                }
            })
        }
        val text = try {
            Http.request("${c.base}/messages", "POST", mapOf(
                "x-api-key" to c.key, "anthropic-version" to "2023-06-01", "content-type" to "application/json",
            ), body.toString(), timeoutMs)
        } catch (e: HttpError) { throw Exception(errorMessage(c, e)) }
        val root = Repo.json.parseToJsonElement(text).jsonObject
        val out = root["content"]?.jsonArray?.mapNotNull { blk ->
            val o = blk.jsonObject
            if (o["type"]?.jsonPrimitive?.contentOrNull == "text") o["text"]?.jsonPrimitive?.contentOrNull else null
        }?.joinToString("\n")?.trim().orEmpty()
        if (out.isBlank() && root["stop_reason"]?.jsonPrimitive?.contentOrNull == "max_tokens") throw Exception("The reply was cut off. Try again.")
        return out
    }

    /** Images arrive as OpenAI-style parts; Anthropic wants its own image blocks. */
    private fun toAnthropicContent(content: JsonElement): JsonElement {
        if (content !is JsonArray) return content
        return buildJsonArray {
            content.forEach { part ->
                val o = part.jsonObject
                when (o["type"]?.jsonPrimitive?.contentOrNull) {
                    "image_url" -> {
                        val url = o["image_url"]?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull.orEmpty()
                        val media = url.substringAfter("data:").substringBefore(";")
                        add(buildJsonObject {
                            put("type", "image")
                            put("source", buildJsonObject { put("type", "base64"); put("media_type", media); put("data", url.substringAfter("base64,")) })
                        })
                    }
                    else -> add(part)
                }
            }
        }
    }

    private suspend fun sendOpenAi(c: Cfg, messages: List<Pair<String, JsonElement>>, maxTokens: Int, timeoutMs: Int, system: String?): String {
        // OpenAI's newer models only accept max_completion_tokens; most other services want max_tokens.
        var tokenParam = if (c.p.id == "openai") "max_completion_tokens" else "max_tokens"
        val headers = buildMap {
            if (c.key.isNotBlank()) put("Authorization", "Bearer ${c.key}")
            put("content-type", "application/json")
            if (c.p.id == "openrouter") { put("HTTP-Referer", "https://github.com/lclarke0/Bulk"); put("X-Title", "Bulk Plan") }
        }
        for (attempt in 0..1) {
            val body = buildJsonObject {
                put("model", c.model)
                put(tokenParam, maxTokens)
                put("messages", buildJsonArray {
                    if (!system.isNullOrBlank()) add(buildJsonObject { put("role", "system"); put("content", system) })
                    messages.forEach { (role, content) -> add(buildJsonObject { put("role", role); put("content", content) }) }
                })
            }
            val text = try {
                Http.request("${c.base}/chat/completions", "POST", headers, body.toString(), timeoutMs)
            } catch (e: HttpError) {
                val other = if (tokenParam == "max_tokens") "max_completion_tokens" else "max_tokens"
                if (attempt == 0 && e.code == 400 && (e.body.contains(tokenParam) || e.body.contains(other))) { tokenParam = other; continue }
                throw Exception(errorMessage(c, e))
            }
            val root = Repo.json.parseToJsonElement(text).jsonObject
            val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            val content = choice?.get("message")?.jsonObject?.get("content")
            val out = when (content) {
                is JsonPrimitive -> content.contentOrNull.orEmpty()
                is JsonArray -> content.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }.joinToString("\n")
                else -> ""
            }.trim()
            if (out.isBlank()) {
                if (choice?.get("finish_reason")?.jsonPrimitive?.contentOrNull == "length") throw Exception("The reply was cut off before it finished. Try again, or pick a different model.")
                throw Exception("${c.p.name.substringBefore(" (")} sent an empty reply. Try again.")
            }
            return out
        }
        throw Exception("Couldn’t reach the AI.")
    }

    // ---------- features ----------

    /**
     * Coach conversation. [system] carries the coaching rules plus a fresh copy of your
     * plan and logs on every call, so the coach always sees current data.
     */
    suspend fun chat(s: Settings, messages: List<ChatMessage>, system: String? = null): String = chat(cfg(s), messages, system)

    suspend fun chat(c: Cfg, messages: List<ChatMessage>, system: String? = null, maxTokens: Int = 4000, timeoutMs: Int = 180_000): String =
        send(c, prepare(messages).map { it.first to JsonPrimitive(it.second) }, maxTokens, timeoutMs, system)

    /**
     * Turns the stored chat into alternating user/assistant turns that end on a user turn.
     * User turns use what you typed (older versions stored the whole data dump in the first one).
     */
    internal fun prepare(messages: List<ChatMessage>, keep: Int = 30): List<Pair<String, String>> {
        val clean = mutableListOf<Pair<String, String>>()
        for (m in messages.filter { (it.role == "user" || it.role == "assistant") && it.failed != true }) {
            val text = (if (m.role == "user") m.display ?: m.content else m.content).trim()
            if (text.isBlank()) continue
            if (clean.isNotEmpty() && clean.last().first == m.role) clean[clean.size - 1] = m.role to (clean.last().second + "\n\n" + text)
            else clean += m.role to text
        }
        var tail = clean.takeLast(keep)
        while (tail.isNotEmpty() && tail.first().first != "user") tail = tail.drop(1)
        return tail
    }

    /** One long request, used to build a whole plan from the intake document. */
    suspend fun buildPlan(s: Settings, document: String): String =
        send(cfg(s), listOf("user" to JsonPrimitive(document)), maxTokens = 32_000, timeoutMs = 420_000)

    /** Lists the models your key can use, so you never have to guess a model name. */
    suspend fun listModels(c: Cfg): List<String> {
        if (c.base.isBlank()) throw Exception("Add the base URL first.")
        val headers = if (c.p.anthropic) mapOf("x-api-key" to c.key, "anthropic-version" to "2023-06-01")
        else if (c.key.isNotBlank()) mapOf("Authorization" to "Bearer ${c.key}") else emptyMap()
        val text = try { Http.request("${c.base}/models" + if (c.p.anthropic) "?limit=100" else "", "GET", headers) }
        catch (e: HttpError) { throw Exception(errorMessage(c, e)) }
        val root = Repo.json.parseToJsonElement(text)
        val arr = (root as? JsonArray) ?: root.jsonObject["data"]?.jsonArray ?: root.jsonObject["models"]?.jsonArray ?: JsonArray(emptyList())
        val skip = Regex("embed|tts|whisper|dall-e|image|audio|moderation|realtime|transcribe|search|computer-use|aqa|imagen|veo|lyria|rerank|guard", RegexOption.IGNORE_CASE)
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            (o["id"] ?: o["name"])?.jsonPrimitive?.contentOrNull?.removePrefix("models/")
        }.filterNot { skip.containsMatchIn(it) }.distinct().sorted()
    }

    data class FoodGuess(val name: String, val macros: Macros, val note: String = "")

    private const val FOOD_RULES = """Reply with ONLY a JSON object, no other text, in this shape:
{"items":[{"name":"short food name with portion","kcal":0,"protein":0,"carbs":0,"fat":0}],"note":"one short line on how sure you are"}
Numbers are for the whole portion eaten, in kcal and grams. Use typical US portion sizes when none is given."""

    /** Estimates calories and macros from a description like "Chipotle chicken burrito, no beans". */
    suspend fun estimateMeal(s: Settings, description: String): List<FoodGuess> =
        parseFood(send(cfg(s), listOf("user" to JsonPrimitive("Estimate the calories and macros for what I ate: \"$description\"\n\n$FOOD_RULES")), 1500, 90_000))

    /** Reads a nutrition label (or a plate of food) from a photo. */
    suspend fun readPhoto(s: Settings, jpegBase64: String, servings: Double, hint: String): List<FoodGuess> {
        val c = cfg(s)
        if (!c.p.vision) throw Exception("${c.p.name} can’t read photos. Use Describe it, or switch to Gemini or OpenAI in Settings › AI coach.")
        val ask = "This is a photo of a nutrition facts label or a meal. If it's a label, read the per-serving values and multiply by $servings serving(s). If it's a meal, estimate the portion shown. ${if (hint.isNotBlank()) "The food is: $hint." else ""}\n\n$FOOD_RULES"
        val parts = buildJsonArray {
            add(buildJsonObject { put("type", "image_url"); put("image_url", buildJsonObject { put("url", "data:image/jpeg;base64,$jpegBase64") }) })
            add(buildJsonObject { put("type", "text"); put("text", ask) })
        }
        return parseFood(send(c, listOf("user" to parts), 1500, 120_000))
    }

    internal fun parseFood(text: String): List<FoodGuess> {
        val i = text.indexOf('{'); val j = text.lastIndexOf('}')
        if (i < 0 || j <= i) throw Exception("The AI didn’t return food numbers. Try describing it differently.")
        val o = Repo.json.parseToJsonElement(text.substring(i, j + 1)).jsonObject
        val note = o["note"]?.jsonPrimitive?.contentOrNull ?: ""
        val items = (o["items"] as? JsonArray).orEmpty().mapNotNull { e ->
            val it = e as? JsonObject ?: return@mapNotNull null
            fun d(k: String) = it[k]?.jsonPrimitive?.doubleOrNull ?: 0.0
            FoodGuess(it["name"]?.jsonPrimitive?.contentOrNull ?: "Food", Macros(d("kcal"), d("protein"), d("carbs"), d("fat")), note)
        }
        if (items.isEmpty()) throw Exception("The AI couldn’t identify the food.")
        return items
    }
}
