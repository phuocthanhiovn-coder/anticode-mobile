package vn.anticode.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class TerminalResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val workingDir: String = "",
    val isError: Boolean = exitCode != 0
) {
    val output: String get() = if (stderr.isNotBlank() && stdout.isNotBlank()) "$stdout\n$stderr"
        else stderr.ifBlank { stdout }.ifBlank { if (exitCode == 0) "✓ Done" else "✗ Failed" }
}

data class VpsSpecs(
    val cpu: String = "Unknown",
    val cores: String = "?",
    val ram: String = "?",
    val disk: String = "?",
    val os: String = "Linux"
)

object TerminalManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(65, TimeUnit.SECONDS) // VPS timeout is 60s
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private var baseUrl = ""
    private var apiKey = ""

    fun configure(baseUrl: String, apiKey: String) {
        this.baseUrl = baseUrl.trimEnd('/')
        this.apiKey = apiKey
    }

    fun isConfigured() = baseUrl.isNotBlank() && apiKey.isNotBlank()

    /**
     * Execute a command on the VPS via server API.
     */
    suspend fun execute(command: String): TerminalResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext TerminalResult("", "Terminal not configured. Set API key first.", -1)
        }

        try {
            val json = JSONObject().put("command", command)
            val body = json.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/api/terminal/exec")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val obj = JSONObject(responseBody)
                TerminalResult(
                    stdout = obj.optString("stdout", ""),
                    stderr = obj.optString("stderr", ""),
                    exitCode = obj.optInt("exitCode", 0),
                    workingDir = obj.optString("workingDir", "")
                )
            } else {
                val errorMsg = try {
                    val obj = JSONObject(responseBody)
                    obj.optString("error", "HTTP ${response.code}")
                } catch (_: Exception) { "HTTP ${response.code}" }
                TerminalResult("", errorMsg, response.code)
            }
        } catch (e: Exception) {
            TerminalResult("", "Connection error: ${e.message}", -1)
        }
    }

    /**
     * Get VPS hardware specs.
     */
    suspend fun getSpecs(): VpsSpecs = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext VpsSpecs()

        try {
            val request = Request.Builder()
                .url("$baseUrl/api/terminal/specs")
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val obj = JSONObject(response.body?.string() ?: "{}")
                VpsSpecs(
                    cpu = obj.optString("cpu", "Unknown"),
                    cores = obj.optString("cores", "?"),
                    ram = obj.optString("ram", "?"),
                    disk = obj.optString("disk", "?"),
                    os = obj.optString("os", "Linux")
                )
            } else {
                VpsSpecs()
            }
        } catch (_: Exception) {
            VpsSpecs()
        }
    }
}
