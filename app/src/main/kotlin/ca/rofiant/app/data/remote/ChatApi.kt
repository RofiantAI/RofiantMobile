package ca.rofiant.app.data.remote

import android.util.Log
import ca.rofiant.app.data.auth.AuthConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ChatRequestMessage(val role: String, val content: String, val imageDataUrl: String? = null)

@Serializable
private data class StreamOptions(val include_usage: Boolean = true)

// messages is built by hand (see buildMessagesJson) rather than via a
// @Serializable list field, since vision turns need an array `content`
// (text + image_url parts) instead of the usual plain string.
private fun buildRequestBody(
    model: String,
    messages: List<ChatRequestMessage>,
    stream: Boolean,
    reasoningEffort: String? = null,
    maxTokens: Int? = null,
    temperature: Double? = null,
): JsonObject = buildJsonObject {
    put("model", model)
    put("messages", buildMessagesJson(messages))
    put("stream", stream)
    if (stream) put("stream_options", buildJsonObject { put("include_usage", true) })
    if (reasoningEffort != null) put("reasoning_effort", reasoningEffort)
    if (maxTokens != null) put("max_tokens", maxTokens)
    if (temperature != null) put("temperature", temperature)
}

private fun buildMessagesJson(messages: List<ChatRequestMessage>): JsonArray = buildJsonArray {
    for (m in messages) {
        add(
            buildJsonObject {
                put("role", m.role)
                if (m.imageDataUrl != null) {
                    put(
                        "content",
                        buildJsonArray {
                            add(buildJsonObject { put("type", "text"); put("text", m.content) })
                            add(
                                buildJsonObject {
                                    put("type", "image_url")
                                    put("image_url", buildJsonObject { put("url", m.imageDataUrl) })
                                }
                            )
                        },
                    )
                } else {
                    put("content", m.content)
                }
            }
        )
    }
}

@Serializable
private data class ChunkDelta(val content: String? = null)

// Some responses (e.g. when the upstream model can't stream) come back as a
// single aggregated chat.completion object instead of chunks — "message" is
// that shape's equivalent of "delta".
@Serializable
private data class ChunkMessage(val content: String? = null)

@Serializable
private data class ChunkChoice(
    val delta: ChunkDelta = ChunkDelta(),
    val message: ChunkMessage? = null,
    val finish_reason: String? = null,
)

@Serializable
private data class ChunkUsage(val prompt_tokens: Int = 0, val completion_tokens: Int = 0)

@Serializable
private data class ChatChunk(val choices: List<ChunkChoice> = emptyList(), val usage: ChunkUsage? = null)

sealed interface ChatStreamEvent {
    data class Delta(val text: String) : ChatStreamEvent
    data class Usage(val inputTokens: Int, val outputTokens: Int) : ChatStreamEvent
    data object Done : ChatStreamEvent
    data class Error(val message: String) : ChatStreamEvent
}

/**
 * Streams a chat completion from the same Supabase Edge Function
 * (groq-proxy) rofiant-desktop's Rust backend calls in run_agent() —
 * see src-tauri/src/lib.rs. No tool-calling loop here: the file/shell/MCP
 * agent tools that loop implements have no safe sandboxed equivalent on
 * Android, so this is a plain single-turn streaming request.
 *
 * SSE is parsed by hand rather than via the okhttp-sse library: groq-proxy
 * sends genuine `data: {...}` framing but labels the response
 * `Content-Type: application/json` instead of `text/event-stream`, and
 * okhttp-sse refuses to parse anything that isn't the exact latter — it
 * silently routes the whole raw body into onFailure instead. Reading the
 * body ourselves sidesteps that content-type sniffing entirely.
 */
class ChatApi(private val client: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json".toMediaType()
    private val streamingClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun streamChat(
        accessToken: String,
        model: String,
        messages: List<ChatRequestMessage>,
        reasoningEffort: String?,
        edgeFunction: String = "groq-proxy",
    ): Flow<ChatStreamEvent> = callbackFlow {
        val supportsEffort = model.startsWith("openai/gpt-oss")
        val body = buildRequestBody(
            model = model,
            messages = messages,
            stream = true,
            reasoningEffort = if (supportsEffort) reasoningEffort else null,
        ).toString()
        val request = Request.Builder()
            .url("${AuthConfig.FUNCTIONS_BASE}/$edgeFunction")
            .header("apikey", AuthConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "text/event-stream, application/json")
            .post(body.toRequestBody(jsonMedia))
            .build()

        val call = streamingClient.newCall(request)

        launch(Dispatchers.IO) {
            var streamErrored = false
            try {
                call.execute().use { response ->
                    Log.d(TAG, "Chat request to $edgeFunction returned HTTP ${response.code}")
                    if (!response.isSuccessful) {
                        val text = response.body?.string()?.takeIf { it.isNotBlank() }
                        streamErrored = true
                        trySend(ChatStreamEvent.Error(text ?: "Request failed (${response.code})"))
                        return@use
                    }
                    val source = response.body?.source() ?: run {
                        streamErrored = true
                        trySend(ChatStreamEvent.Error("Empty response"))
                        return@use
                    }

                    fun handleDataPayload(data: String) {
                        if (data.isEmpty() || data == "[DONE]") return
                        val chunk = runCatching { json.decodeFromString(ChatChunk.serializer(), data) }
                            .getOrElse {
                                // Payload wasn't a ChatChunk at all — e.g. an error object
                                // ({"error": "..."}) the proxy sent with a 200 status. Surface
                                // it instead of silently dropping it, which used to end the
                                // stream with an empty buffer and a generic "No response received."
                                streamErrored = true
                                trySend(ChatStreamEvent.Error(data.take(300)))
                                return
                            }
                        val content = chunk.choices.firstOrNull()?.delta?.content
                            ?: chunk.choices.firstOrNull()?.message?.content
                        if (!content.isNullOrEmpty()) trySend(ChatStreamEvent.Delta(content))
                        chunk.usage?.let { trySend(ChatStreamEvent.Usage(it.prompt_tokens, it.completion_tokens)) }
                    }

                    val firstLine = if (!source.exhausted()) source.readUtf8Line() else null
                    if (firstLine == null) {
                        streamErrored = true
                        trySend(ChatStreamEvent.Error("Empty response"))
                    } else if (firstLine.startsWith("data:")) {
                        // Real SSE framing.
                        handleDataPayload(firstLine.removePrefix("data:").trim())
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (!line.startsWith("data:")) continue
                            handleDataPayload(line.removePrefix("data:").trim())
                        }
                    } else {
                        // groq-proxy sometimes falls back to a single fully-buffered
                        // (non-chunked) completion object instead of streaming —
                        // same body shape "message.content" handles, just delivered
                        // as one Delta instead of many.
                        val rest = if (!source.exhausted()) source.readUtf8() else ""
                        handleDataPayload(firstLine + rest)
                    }
                    // An error is terminal. Emitting Done after it makes the view model
                    // treat the stream as an empty successful response and overwrite the
                    // useful error message.
                    if (!streamErrored) trySend(ChatStreamEvent.Done)
                }
            } catch (e: IOException) {
                streamErrored = true
                Log.w(TAG, "Chat request failed", e)
                trySend(ChatStreamEvent.Error(e.message ?: "Network error"))
            } catch (e: Exception) {
                streamErrored = true
                Log.e(TAG, "Chat stream processing failed", e)
                trySend(ChatStreamEvent.Error(e.message ?: "Could not process the chat response"))
            } finally {
                close()
            }
        }

        awaitClose { call.cancel() }
    }

    /**
     * Mirrors rofiant-desktop's generate_title (src-tauri/src/lib.rs) — a
     * cheap, low-effort single-shot completion fired off in the background
     * right after the first message in a new conversation is sent.
     */
    suspend fun generateTitle(accessToken: String, userText: String): String? {
        val body = buildRequestBody(
            model = TITLE_MODEL,
            messages = listOf(
                ChatRequestMessage("system", TITLE_SYSTEM_PROMPT),
                ChatRequestMessage("user", userText),
            ),
            stream = false,
            reasoningEffort = "low",
            maxTokens = 60,
            temperature = 0.3,
        ).toString()
        val request = Request.Builder()
            .url("${AuthConfig.FUNCTIONS_BASE}/groq-proxy")
            .header("Authorization", "Bearer $accessToken")
            .post(body.toRequestBody(jsonMedia))
            .build()

        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val raw = response.body?.string() ?: return@use null
                    val chunk = json.decodeFromString(ChatChunk.serializer(), raw)
                    chunk.choices.firstOrNull()?.message?.content
                        ?.trim()?.trim('"')
                        ?.takeIf { it.isNotEmpty() }
                }
            }.getOrNull()
        }
    }

    /**
     * Mirrors rofiant-desktop's transcribe_audio (src-tauri/src/lib.rs) —
     * multipart upload to the groq-transcribe-proxy Edge Function, same
     * Whisper model and no-speech-hallucination filtering.
     */
    suspend fun transcribeAudio(accessToken: String, audioFile: File, mimeType: String): String? {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", audioFile.name, audioFile.asRequestBody(mimeType.toMediaType()))
            .addFormDataPart("model", TRANSCRIBE_MODEL)
            .addFormDataPart("response_format", "verbose_json")
            .build()
        val request = Request.Builder()
            .url("${AuthConfig.FUNCTIONS_BASE}/groq-transcribe-proxy")
            .header("Authorization", "Bearer $accessToken")
            .post(requestBody)
            .build()

        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val raw = response.body?.string() ?: return@use null
                    val result = json.decodeFromString(TranscribeResponse.serializer(), raw)
                    val text = result.text?.trim().orEmpty()
                    if (text.isEmpty()) return@use text

                    // Whisper hallucinates stock phrases ("Thank you.", "you") on
                    // silent audio; verbose_json segments expose no_speech_prob so
                    // that can be detected and dropped instead of inserted as text.
                    val segments = result.segments
                    if (!segments.isNullOrEmpty() && segments.all { (it.no_speech_prob ?: 0.0) > NO_SPEECH_THRESHOLD }) {
                        ""
                    } else {
                        text
                    }
                }
            }.getOrNull()
        }
    }

    private companion object {
        const val TAG = "RofiantChat"
        const val TITLE_MODEL = "openai/gpt-oss-20b"
        const val TITLE_SYSTEM_PROMPT = "Generate a short, specific title (3-6 words, no quotes, " +
            "no trailing punctuation) that summarizes what the user wants. Reply with only the title, nothing else."
        const val TRANSCRIBE_MODEL = "whisper-large-v3-turbo"
        const val NO_SPEECH_THRESHOLD = 0.6
    }
}

@Serializable
private data class TranscribeSegment(val no_speech_prob: Double? = null)

@Serializable
private data class TranscribeResponse(val text: String? = null, val segments: List<TranscribeSegment>? = null)
