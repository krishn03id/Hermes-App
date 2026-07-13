package id.krishn03.hermes.data

import kotlinx.serialization.Serializable

/** Supported provider families. Each maps to a native streaming client. */
enum class Provider(val label: String) {
    OPENAI("OpenAI"),
    ANTHROPIC("Anthropic"),
    GEMINI("Gemini");

    /** Default API base URL for the provider. */
    fun defaultBaseUrl(): String = when (this) {
        OPENAI -> "https://api.openai.com/v1"
        ANTHROPIC -> "https://api.anthropic.com/v1"
        GEMINI -> "https://generativelanguage.googleapis.com/v1beta"
    }

    /** A sensible default model id so a freshly added key works out of the box. */
    fun defaultModel(): String = when (this) {
        OPENAI -> "gpt-4o-mini"
        ANTHROPIC -> "claude-3-5-sonnet-20241022"
        GEMINI -> "gemini-1.5-flash"
    }
}

/**
 * A single stored credential. Users may add many of these, across providers.
 * [baseUrl] lets people point OpenAI-compatible endpoints (Groq, OpenRouter,
 * local servers, …) at the OpenAI client.
 */
@Serializable
data class ApiKeyEntry(
    val id: String,
    val label: String,
    val provider: Provider,
    val key: String,
    val model: String,
    val baseUrl: String,
    /** Extra HTTP headers sent with every request for this key (e.g. gateway
     *  auth, org routing). Applied last, so they can override defaults. */
    val customHeaders: Map<String, String> = emptyMap(),
)

enum class Role { USER, ASSISTANT }

@Serializable
data class ChatMessage(
    val role: Role,
    val content: String,
    /** Optional attached image, base64-encoded, for vision-capable models. */
    val imageBase64: String? = null,
    val imageMime: String? = null,
)

/**
 * Per-model usage counters, accumulated on device. We don't get exact token
 * counts from a streamed response without extra parsing, so we track something
 * honest and useful: number of messages sent and characters received.
 */
@Serializable
data class UsageStat(
    val model: String,
    val provider: Provider,
    val messages: Long = 0,
    val charsReceived: Long = 0,
) {
    /** Rough token estimate: ~4 chars/token, the usual English heuristic. */
    val estTokens: Long get() = (charsReceived + 3) / 4
}

