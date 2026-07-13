package id.krishn03.hermes.net

import id.krishn03.hermes.data.ApiKeyEntry
import id.krishn03.hermes.data.ChatMessage
import id.krishn03.hermes.data.Provider
import id.krishn03.hermes.data.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Streams a chat completion from whichever provider [key] belongs to.
 * Tokens are delivered incrementally via [onToken]; the call suspends until
 * the stream ends. Throws on transport / HTTP errors with a readable message.
 */
object LlmClient {

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Applies any user-defined headers last so they can override defaults. */
    private fun Request.Builder.applyCustomHeaders(key: ApiKeyEntry): Request.Builder {
        key.customHeaders.forEach { (name, value) ->
            if (name.isNotBlank()) header(name.trim(), value)
        }
        return this
    }

    suspend fun stream(
        key: ApiKeyEntry,
        history: List<ChatMessage>,
        onToken: (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val request = when (key.provider) {
            Provider.OPENAI -> openAiRequest(key, history)
            Provider.ANTHROPIC -> anthropicRequest(key, history)
            Provider.GEMINI -> geminiRequest(key, history)
        }

        http.newCall(request).execute().use { response ->
            val body = response.body ?: throw RuntimeException("Empty response from ${key.provider.label}")
            if (!response.isSuccessful) {
                val err = body.string().take(600)
                throw RuntimeException("${key.provider.label} error ${response.code}: $err")
            }
            val source = body.source()
            while (true) {
                coroutineContext.ensureActive() // cancel promptly if the user stops
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue
                if (!line.startsWith("data:")) continue
                val data = line.substring(5).trim()
                if (data == "[DONE]") break
                val token = when (key.provider) {
                    Provider.OPENAI -> parseOpenAi(data)
                    Provider.ANTHROPIC -> parseAnthropic(data)
                    Provider.GEMINI -> parseGemini(data)
                }
                if (!token.isNullOrEmpty()) onToken(token)
            }
        }
    }

    // ---- Model discovery ----

    /** Fetches the provider's available model ids so users can pick instead of
     *  typing. Returns them sorted; throws with a readable message on failure. */
    suspend fun listModels(key: ApiKeyEntry): List<String> = withContext(Dispatchers.IO) {
        val base = key.baseUrl.trimEnd('/')
        val builder = when (key.provider) {
            Provider.OPENAI -> Request.Builder()
                .url("$base/models")
                .header("Authorization", "Bearer ${key.key}")
            Provider.ANTHROPIC -> Request.Builder()
                .url("$base/models?limit=1000")
                .header("x-api-key", key.key)
                .header("anthropic-version", "2023-06-01")
            Provider.GEMINI -> Request.Builder()
                .url("$base/models?pageSize=1000&key=${key.key}")
        }
        val request = builder.get().applyCustomHeaders(key).build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw RuntimeException("${key.provider.label} error ${response.code}: ${text.take(300)}")
            }
            val root = json.parseToJsonElement(text).jsonObject
            val ids = when (key.provider) {
                Provider.GEMINI -> (root["models"]?.jsonArray ?: emptyList()).mapNotNull {
                    // "models/gemini-1.5-flash" -> "gemini-1.5-flash"
                    it.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.substringAfter("models/")
                }
                else -> (root["data"]?.jsonArray ?: emptyList()).mapNotNull {
                    it.jsonObject["id"]?.jsonPrimitive?.contentOrNull
                }
            }
            ids.filter { it.isNotBlank() }.distinct().sorted()
        }
    }

    // ---- OpenAI (and OpenAI-compatible endpoints) ----
    private fun openAiRequest(key: ApiKeyEntry, history: List<ChatMessage>): Request {
        val payload = buildJsonObject {
            put("model", key.model)
            put("stream", true)
            putJsonArray("messages") {
                history.forEach { msg ->
                    addJsonObject {
                        put("role", if (msg.role == Role.USER) "user" else "assistant")
                        if (msg.imageBase64 != null) {
                            putJsonArray("content") {
                                addJsonObject { put("type", "text"); put("text", msg.content) }
                                addJsonObject {
                                    put("type", "image_url")
                                    put("image_url", buildJsonObject {
                                        put("url", "data:${msg.imageMime ?: "image/jpeg"};base64,${msg.imageBase64}")
                                    })
                                }
                            }
                        } else {
                            put("content", msg.content)
                        }
                    }
                }
            }
        }
        val url = key.baseUrl.trimEnd('/') + "/chat/completions"
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${key.key}")
            .header("Accept", "text/event-stream")
            .applyCustomHeaders(key)
            .post(json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), payload).toRequestBody(jsonMedia))
            .build()
    }

    private fun parseOpenAi(data: String): String? = runCatching {
        json.parseToJsonElement(data).jsonObject["choices"]
            ?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    // ---- Anthropic ----

    private fun anthropicRequest(key: ApiKeyEntry, history: List<ChatMessage>): Request {
        val payload = buildJsonObject {
            put("model", key.model)
            put("max_tokens", 2048)
            put("stream", true)
            putJsonArray("messages") {
                history.forEach { msg ->
                    addJsonObject {
                        put("role", if (msg.role == Role.USER) "user" else "assistant")
                        if (msg.imageBase64 != null) {
                            putJsonArray("content") {
                                addJsonObject {
                                    put("type", "image")
                                    put("source", buildJsonObject {
                                        put("type", "base64")
                                        put("media_type", msg.imageMime ?: "image/jpeg")
                                        put("data", msg.imageBase64)
                                    })
                                }
                                addJsonObject { put("type", "text"); put("text", msg.content) }
                            }
                        } else {
                            put("content", msg.content)
                        }
                    }
                }
            }
        }
        val url = key.baseUrl.trimEnd('/') + "/messages"
        return Request.Builder()
            .url(url)
            .header("x-api-key", key.key)
            .header("anthropic-version", "2023-06-01")
            .header("Accept", "text/event-stream")
            .applyCustomHeaders(key)
            .post(json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), payload).toRequestBody(jsonMedia))
            .build()
    }

    private fun parseAnthropic(data: String): String? = runCatching {
        val obj = json.parseToJsonElement(data).jsonObject
        if (obj["type"]?.jsonPrimitive?.contentOrNull != "content_block_delta") return null
        obj["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    // ---- Gemini ----

    private fun geminiRequest(key: ApiKeyEntry, history: List<ChatMessage>): Request {
        val payload = buildJsonObject {
            putJsonArray("contents") {
                history.forEach { msg ->
                    addJsonObject {
                        put("role", if (msg.role == Role.USER) "user" else "model")
                        putJsonArray("parts") {
                            addJsonObject { put("text", msg.content) }
                            if (msg.imageBase64 != null) {
                                addJsonObject {
                                    put("inline_data", buildJsonObject {
                                        put("mime_type", msg.imageMime ?: "image/jpeg")
                                        put("data", msg.imageBase64)
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
        val url = "${key.baseUrl.trimEnd('/')}/models/${key.model}:streamGenerateContent?alt=sse&key=${key.key}"
        return Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .applyCustomHeaders(key)
            .post(json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), payload).toRequestBody(jsonMedia))
            .build()
    }

    private fun parseGemini(data: String): String? = runCatching {
        json.parseToJsonElement(data).jsonObject["candidates"]
            ?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("text")?.jsonPrimitive?.contentOrNull
    }.getOrNull()
}
