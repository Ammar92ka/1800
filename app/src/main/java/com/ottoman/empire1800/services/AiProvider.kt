package com.ottoman.empire1800.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class AiException(
    override val message: String,
    val retryable: Boolean = false,
    val modelProblem: Boolean = false,
    val status: Int? = null,
) : Exception(message)

interface AiProvider {
    val id: String
    val name: String
    val defaultModel: String
    val fallbackModels: List<String>
    val supportsSearch: Boolean

    suspend fun generate(
        prompt: String,
        apiKey: String,
        model: String,
        json: Boolean = false,
        maxTokens: Int = 2048,
        search: Boolean = false,
    ): String
}

const val GEMINI_ROUND_MAX_TOKENS: Int = 8000
const val GROQ_ROUND_MAX_TOKENS: Int = 5000

val aiProviders: List<AiProvider> = listOf(GeminiProvider(), GroqProvider())

fun aiProviderById(id: String): AiProvider =
    aiProviders.firstOrNull { it.id == id } ?: aiProviders.first()

private fun httpError(status: Int): AiException = when {
    status == 400 -> AiException(
        "الطلب مرفوض. تحقق من المفتاح ومن اسم النموذج في الإعدادات.",
        status = 400,
    )
    status == 401 || status == 403 -> AiException(
        "المفتاح غير صالح أو لا يملك صلاحية ($status).",
        status = status,
    )
    status == 404 -> AiException(
        "النموذج غير موجود. غيّر اسم النموذج من الإعدادات.",
        modelProblem = true,
        status = 404,
    )
    status == 429 -> AiException(
        "تجاوزت حد الاستخدام أو الحصة (429). انتظر قليلا ثم حاول.",
        retryable = true,
        status = 429,
    )
    status == 503 || status >= 500 -> AiException(
        "خدمة المزود غير متاحة حاليا (رمز $status).",
        retryable = true,
        status = status,
    )
    else -> AiException("فشل الطلب (رمز $status).", status = status)
}

private fun timeoutMsFor(json: Boolean, search: Boolean = false): Int =
    if (json) (if (search) 200_000 else 150_000) else 40_000

private suspend fun postJson(
    urlString: String,
    headers: Map<String, String>,
    body: JSONObject,
    timeoutMs: Int,
): Pair<Int, String> = withContext(Dispatchers.IO) {
    var conn: HttpURLConnection? = null
    try {
        val url = URL(urlString)
        conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            doOutput = true
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        conn.outputStream.use { out ->
            out.write(body.toString().toByteArray(Charsets.UTF_8))
        }
        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        status to text
    } catch (_: SocketTimeoutException) {
        throw AiException("انتهت مهلة الاتصال. حاول مرة أخرى.", retryable = true)
    } catch (_: IOException) {
        throw AiException("تعذر الاتصال بالخدمة. تحقق من الإنترنت.", retryable = true)
    } finally {
        conn?.disconnect()
    }
}

fun parseGeminiText(body: String): String {
    val data = JSONObject(body)
    val candidates = data.optJSONArray("candidates")
    if (candidates != null && candidates.length() > 0) {
        val first = candidates.optJSONObject(0)
        val content = first?.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        if (parts != null) {
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val p = parts.optJSONObject(i)
                val t = p?.optString("text", "") ?: ""
                sb.append(t)
            }
            val text = sb.toString().trim()
            if (text.isNotEmpty()) return text
        }
    }
    throw AiException("لم يرجع النموذج نصا. قد يكون المحتوى محجوبا.")
}

fun parseChatCompletionText(body: String): String {
    val data = JSONObject(body)
    val choices = data.optJSONArray("choices")
    if (choices != null && choices.length() > 0) {
        val first = choices.optJSONObject(0)
        val message = first?.optJSONObject("message")
        val content = message?.opt("content") as? String
        if (!content.isNullOrBlank()) return content.trim()
    }
    throw AiException("لم يرجع النموذج نصا.")
}

class GeminiProvider : AiProvider {
    override val id: String = "gemini"
    override val name: String = "Gemini"
    override val defaultModel: String = "gemini-4-hydrogen"
    override val fallbackModels: List<String> = listOf(
        "gemini-3.8-flash",
        "gemini-3.5-flash-lite",
        "gemini-3.5-flash",
    )
    override val supportsSearch: Boolean = true

    override suspend fun generate(
        prompt: String,
        apiKey: String,
        model: String,
        json: Boolean,
        maxTokens: Int,
        search: Boolean,
    ): String {
        if (apiKey.trim().isEmpty()) {
            throw AiException("لا يوجد مفتاح. أضفه من الإعدادات.")
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val body = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt)),
                    ),
                ),
            )
            if (search) {
                put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            }
            put(
                "generationConfig",
                JSONObject().apply {
                    put("maxOutputTokens", if (json) GEMINI_ROUND_MAX_TOKENS else maxTokens)
                    if (json && !search) put("responseMimeType", "application/json")
                    if (json && !model.startsWith("gemini-4")) put("temperature", 0.9)
                },
            )
        }
        val (status, respBody) = postJson(
            urlString = url,
            headers = mapOf(
                "Content-Type" to "application/json",
                "x-goog-api-key" to apiKey.trim(),
            ),
            body = body,
            timeoutMs = timeoutMsFor(json, search),
        )
        if (status != 200) throw httpError(status)
        return parseGeminiText(respBody)
    }
}

class GroqProvider : AiProvider {
    override val id: String = "groq"
    override val name: String = "Groq"
    override val defaultModel: String = "openai/gpt-oss-120b"
    override val fallbackModels: List<String> = listOf(
        "llama-3.3-70b-versatile",
        "openai/gpt-oss-20b",
        "llama-3.1-8b-instant",
    )
    override val supportsSearch: Boolean = false

    override suspend fun generate(
        prompt: String,
        apiKey: String,
        model: String,
        json: Boolean,
        maxTokens: Int,
        search: Boolean,
    ): String {
        if (apiKey.trim().isEmpty()) {
            throw AiException("لا يوجد مفتاح. أضفه من الإعدادات.")
        }
        val url = "https://api.groq.com/openai/v1/chat/completions"
        val body = JSONObject().apply {
            put("model", model)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().put("role", "user").put("content", prompt),
                ),
            )
            put("max_tokens", if (json) GROQ_ROUND_MAX_TOKENS else maxTokens)
            if (json) put("response_format", JSONObject().put("type", "json_object"))
        }
        val (status, respBody) = postJson(
            urlString = url,
            headers = mapOf(
                "Content-Type" to "application/json",
                "Authorization" to "Bearer ${apiKey.trim()}",
            ),
            body = body,
            timeoutMs = timeoutMsFor(json),
        )
        if (status != 200) throw httpError(status)
        return parseChatCompletionText(respBody)
    }
}
