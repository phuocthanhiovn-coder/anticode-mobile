package vn.anticode.mobile.ai

import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val role: String,   // "user", "assistant", "system"
    val content: String
)

data class ModelInfo(
    val id: String,
    val name: String
)

/**
 * Anticode API Client
 *
 * Endpoints used:
 * - POST /v1/chat/completions  (OpenAI-compatible, streaming SSE)
 * - GET  /v1/models            (list available models)
 *
 * Auth: `Authorization: Bearer ak-xxxxx` (API Key from user settings)
 */
class AnticodeApi(
    private var baseUrl: String = "https://anticode.vn",
    private var apiKey: String = ""
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS) // long for streaming
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    fun configure(url: String, key: String) {
        baseUrl = url.trimEnd('/')
        apiKey = key.trim()
    }

    fun isConfigured(): Boolean = apiKey.isNotBlank()

    /**
     * Stream chat response — returns Flow of text chunks.
     * Uses /v1/chat/completions (OpenAI format) with SSE streaming.
     *
     * Server auth reads: Authorization: Bearer ak-xxx
     * If token starts with "ak-" → API key auth (LLM API mode)
     */
    fun streamChat(
        messages: List<ChatMessage>,
        model: String = "claude-sonnet-4-6",
        systemPrompt: String? = null
    ): Flow<String> = callbackFlow {

        // Build messages array
        val allMessages = mutableListOf<Map<String, String>>()

        // System prompt first (server treats role=system as system message)
        if (!systemPrompt.isNullOrBlank()) {
            allMessages.add(mapOf("role" to "system", "content" to systemPrompt))
        }

        // User/assistant messages
        for (msg in messages) {
            allMessages.add(mapOf("role" to msg.role, "content" to msg.content))
        }

        val body = mapOf(
            "model" to model,
            "messages" to allMessages,
            "stream" to true,
            "max_tokens" to 4096
        )

        val json = gson.toJson(body)

        val request = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        val call = client.newCall(request)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        val errBody = response.body?.string() ?: ""
                        // Parse server error message
                        val errMsg = try {
                            val obj = JsonParser.parseString(errBody).asJsonObject
                            obj.getAsJsonObject("error")?.get("message")?.asString ?: errBody
                        } catch (_: Exception) {
                            "HTTP ${response.code}: $errBody"
                        }
                        close(IOException(errMsg))
                        return
                    }

                    val source = response.body?.source()
                    if (source == null) {
                        close(IOException("Empty response"))
                        return
                    }

                    // Read SSE stream line by line
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break

                        // SSE format: "data: {...}"
                        if (!line.startsWith("data: ")) continue
                        val data = line.removePrefix("data: ").trim()
                        if (data == "[DONE]") break
                        if (data.isEmpty()) continue

                        try {
                            val obj = JsonParser.parseString(data).asJsonObject
                            val choices = obj.getAsJsonArray("choices")
                            if (choices != null && choices.size() > 0) {
                                val delta = choices[0].asJsonObject.getAsJsonObject("delta")
                                if (delta != null && delta.has("content")) {
                                    val content = delta.get("content")
                                    if (content != null && !content.isJsonNull) {
                                        trySend(content.asString)
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            // Skip malformed JSON chunks
                        }
                    }

                    close() // Stream finished successfully
                } catch (e: Exception) {
                    close(e)
                } finally {
                    response.close()
                }
            }
        })

        awaitClose {
            call.cancel() // Cancel HTTP call when flow is cancelled
        }
    }

    /**
     * Get available models from /v1/models.
     * Response format: { "object": "list", "data": [{"id": "claude-sonnet-4-6", "object": "model", ...}] }
     */
    suspend fun getModels(): List<ModelInfo> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext emptyList()

        val request = Request.Builder()
            .url("$baseUrl/v1/models")
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return@withContext emptyList()
            }

            val body = response.body?.string() ?: return@withContext emptyList()
            response.close()

            val json = JsonParser.parseString(body).asJsonObject
            val data = json.getAsJsonArray("data") ?: return@withContext emptyList()

            data.mapNotNull { elem ->
                try {
                    val obj = elem.asJsonObject
                    ModelInfo(
                        id = obj.get("id").asString,
                        name = obj.get("id").asString
                    )
                } catch (_: Exception) { null }
            }.sortedBy { it.name }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Check if API key is valid by calling /v1/models.
     * Returns true if server responds 200.
     */
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext false
        try {
            val request = Request.Builder()
                .url("$baseUrl/v1/models")
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()
            val response = client.newCall(request).execute()
            val ok = response.isSuccessful
            response.close()
            ok
        } catch (_: Exception) {
            false
        }
    }
}
