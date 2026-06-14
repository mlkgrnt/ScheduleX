package com.schedulex.llm

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.get
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class LlmService {

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                explicitNulls = false
            })
        }
        engine {
            config {
                connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    companion object {
        val SYSTEM_PROMPT = """你是一个课程表解析助手。用户会给你一个教务系统课表的数据。

数据有两种格式：
1. 结构化数据：{"name":"课程名","teacher":"教师","weeks":"3-14(周)[01-02节]","room":"教室"}
2. 原始文本：{"raw":"课程名\n教师\n3-14(周)[01-02节]\n教室"}

你的任务：从两种格式中提取课程信息，输出标准 JSON。

【输出格式 - 严格遵循，只输出 JSON，不要输出任何其他文字】
[{"name":"课程名称","teacher":"教师姓名","location":"上课地点","day":1,"startPeriod":1,"endPeriod":2,"weeks":[1,2,3],"type":"all"}]

【weeks 字段解析规则】
weeks 格式示例："3-14(周)[01-02节]"
- "3-14(周)" → 周次 3 到 14 → [3,4,5,...,14]
- "[01-02节]" → 节次 1 到 2 → startPeriod=1, endPeriod=2
- "[03-04-05节]" → 节次 3 到 5 → startPeriod=3, endPeriod=5
- "3-10,12-18(周)" → 周次 3-10 和 12-18 → [3,4,...,10,12,13,...,18]
- "单周" 或 "(单周)" → type="odd"
- "双周" 或 "(双周)" → type="even"
- 如果没有明确写单双周 → type="all"

【raw 文本解析规则】
raw 文本可能包含多行，通常是：课程名、教师、周次(节次)、教室
也可能有多门课用 "---" 分隔

【硬性规则】
1. day：必须是 1-7 的整数（周一=1, ..., 周日=7），绝对不能是 null
2. startPeriod/endPeriod：必须是 ≥1 的整数，绝对不能是 null
3. weeks：必须是非空整数数组，绝对不能是 null 或空数组
4. name：必须是非空字符串，绝对不能是 null
5. type：只能是 "all"、"odd" 或 "even"
6. 禁止输出注释、解释、markdown代码块标记，只输出纯 JSON 数组"""

        val VISION_PROMPT = """你是一个课程表解析助手。用户会给你一张课表截图，请从中提取所有课程信息，返回一个 JSON 数组。

【输出格式 - 严格遵循，只输出 JSON，不要输出任何其他文字】
[{"name":"课程名称","teacher":"教师姓名","location":"上课地点","day":1,"startPeriod":1,"endPeriod":2,"weeks":[1,2,3],"type":"all"}]

【硬性规则 - 违反任何一条都会导致解析失败】
1. day：必须是 1-7 的整数（周一=1, 周二=2, ..., 周日=7），根据课程所在列（或行）的星期标识确定，绝对不能是 null、字符串或其他类型
2. startPeriod/endPeriod：必须是 ≥1 的整数，使用截图中标注的节次号，绝对不能是 null、字符串或其他类型
3. weeks：必须是非空整数数组，绝对不能是 null 或空数组
4. name：必须是非空字符串，绝对不能是 null
5. type：只能是 "all"、"odd" 或 "even"
6. 周次展开为数组："1-16周" → [1,2,...,16]，"9,11,13周" → [9,11,13]
7. 多个时间段的课程，每个时间段单独一条记录
8. 禁止输出注释、解释、markdown代码块标记，只输出纯 JSON 数组"""
    }

    private fun normalizeUrl(url: String): String {
        var u = url.trim()
        if (u.isEmpty()) return u
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://$u"
        }
        if (u.endsWith("/")) u = u.removeSuffix("/")
        // Auto-append /v1 if missing (most OpenAI-compatible APIs need this)
        if (!u.endsWith("/v1") && !u.endsWith("/v1/") && !u.contains("/v1/")) {
            // Only append if it looks like a base domain (no path after domain)
            if (u.count { it == '/' } <= 3) { // https://domain.com or https://domain.com/path
                u = "$u/v1"
            }
        }
        return u
    }

    suspend fun fetchModels(baseUrl: String, apiKey: String): Result<List<String>> {
        return try {
            val normalizedUrl = normalizeUrl(baseUrl)
            if (normalizedUrl.isBlank()) return Result.failure(Exception("请输入 Base URL"))

            android.util.Log.d("LlmService", "=== FETCH MODELS ===")
            android.util.Log.d("LlmService", "URL: $normalizedUrl/models")
            android.util.Log.d("LlmService", "API Key length: ${apiKey.length}, first4: ${apiKey.take(4)}, last4: ${apiKey.takeLast(4)}")

            val response = client.get("$normalizedUrl/models") {
                header("Authorization", "Bearer $apiKey")
            }

            val body = response.bodyAsText()
            android.util.Log.d("LlmService", "Models response: $body")

            val modelsResp = json.decodeFromString<ModelsResponse>(body)
            val modelIds = modelsResp.data?.mapNotNull { it.id } ?: emptyList()
            if (modelIds.isEmpty()) {
                Result.failure(Exception("API 未返回任何模型"))
            } else {
                Result.success(modelIds)
            }
        } catch (e: Exception) {
            android.util.Log.e("LlmService", "fetchModels error", e)
            Result.failure(e)
        }
    }

    suspend fun testConnection(baseUrl: String, apiKey: String, model: String): Result<String> {
        return try {
            val normalizedUrl = normalizeUrl(baseUrl)
            if (normalizedUrl.isBlank()) return Result.failure(Exception("请输入 Base URL"))
            val request = LlmRequest(
                model = model.lowercase().trim(),
                messages = listOf(
                    LlmMessage(role = "user", content = "Hello, respond with 'OK' only.")
                ),
                max_tokens = 500
            )

            val requestJson = json.encodeToString(LlmRequest.serializer(), request)
            android.util.Log.d("LlmService", "=== TEST CONNECTION ===")
            android.util.Log.d("LlmService", "URL: $normalizedUrl/chat/completions")
            android.util.Log.d("LlmService", "Model: $model")
            android.util.Log.d("LlmService", "Request JSON: $requestJson")

            val response = client.post("$normalizedUrl/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(request)
            }

            val body = response.bodyAsText()
            android.util.Log.d("LlmService", "Response status: ${response.status}")
            android.util.Log.d("LlmService", "Response body: $body")

            val llmResponse = json.decodeFromString<LlmResponse>(body)

            if (llmResponse.error != null) {
                val err = llmResponse.error
                val detail = buildString {
                    append(err.message ?: "")
                    if (err.param != null && err.param != err.message) append(" | param: ${err.param}")
                    if (err.code != null) append(" | code: ${err.code}")
                    if (err.type != null) append(" | type: ${err.type}")
                }.ifBlank { "未知错误" }
                android.util.Log.e("LlmService", "API error: $detail")
                return Result.failure(Exception("API 错误: $detail"))
            }

            val content = llmResponse.choices?.firstOrNull()?.message?.content ?: "OK"
            Result.success("连接成功: $content")
        } catch (e: io.ktor.client.plugins.ResponseException) {
            android.util.Log.e("LlmService", "HTTP error: ${e.response.status}", e)
            val errBody = try { e.response.bodyAsText() } catch (_: Exception) { "" }
            android.util.Log.e("LlmService", "Error body: $errBody")
            // Try to parse error from response body
            if (errBody.isNotBlank()) {
                try {
                    val errResp = json.decodeFromString<LlmResponse>(errBody)
                    if (errResp.error != null) {
                        val err = errResp.error
                        val detail = buildString {
                            append(err.message ?: "")
                            if (err.param != null && err.param != err.message) append(" | param: ${err.param}")
                            if (err.code != null) append(" | code: ${err.code}")
                        }.ifBlank { "HTTP ${e.response.status}" }
                        return Result.failure(Exception("API 错误: $detail"))
                    }
                } catch (_: Exception) {}
            }
            Result.failure(Exception("HTTP ${e.response.status}: ${errBody.take(200)}"))
        } catch (e: Exception) {
            android.util.Log.e("LlmService", "Connection error", e)
            Result.failure(e)
        }
    }

    suspend fun parseScheduleHtml(
        html: String,
        baseUrl: String,
        apiKey: String,
        model: String
    ): Result<List<ParsedCourse>> {
        return try {
            val normalizedUrl = normalizeUrl(baseUrl)
            if (normalizedUrl.isBlank()) return Result.failure(Exception("请输入 Base URL"))
            
            // Truncate HTML if too large (keep under 50KB to avoid LLM issues)
            val trimmedHtml = if (html.length > 50000) {
                android.util.Log.w("LlmService", "HTML too large (${html.length} chars), trimming to 50K")
                html.substring(0, 50000)
            } else html
            
            val request = LlmRequest(
                model = model.lowercase().trim(),
                messages = listOf(
                    LlmMessage(role = "system", content = SYSTEM_PROMPT),
                    LlmMessage(role = "user", content = "请解析以下课表 HTML：\n\n$trimmedHtml")
                ),
                enable_thinking = false
            )

            android.util.Log.d("LlmService", "=== PARSE SCHEDULE ===")
            android.util.Log.d("LlmService", "URL: $normalizedUrl/chat/completions")
            android.util.Log.d("LlmService", "Model: ${model.lowercase().trim()}")
            android.util.Log.d("LlmService", "HTML length: ${trimmedHtml.length}")
            
            val response = client.post("$normalizedUrl/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(request)
            }

            val body = response.bodyAsText()
            android.util.Log.d("LlmService", "LLM response status: ${response.status}")
            android.util.Log.d("LlmService", "LLM response length: ${body.length}")
            
            val llmResponse = json.decodeFromString<LlmResponse>(body)

            if (llmResponse.error != null) {
                val err = llmResponse.error
                val detail = buildString {
                    append(err.message ?: "")
                    if (err.param != null && err.param != err.message) append(" | param: ${err.param}")
                    if (err.code != null) append(" | code: ${err.code}")
                    if (err.type != null) append(" | type: ${err.type}")
                }.ifBlank { "未知错误" }
                android.util.Log.e("LlmService", "API error: $detail")
                return Result.failure(Exception("API 错误: $detail"))
            }

            val content = llmResponse.choices?.firstOrNull()?.message?.content
            val reasoningContent = llmResponse.choices?.firstOrNull()?.message?.reasoning_content
            val actualContent = content?.ifBlank { null } ?: reasoningContent
            android.util.Log.d("LlmService", "Content length: ${content?.length ?: 0}")
            android.util.Log.d("LlmService", "Reasoning content length: ${reasoningContent?.length ?: 0}")
            android.util.Log.d("LlmService", "Actual content preview: ${actualContent?.take(200)}")
            
            if (actualContent.isNullOrBlank()) {
                return Result.failure(Exception("LLM 返回为空，请检查 API Key 和模型名称"))
            }

            val jsonStr = extractJsonArray(actualContent)
            android.util.Log.d("LlmService", "Extracted JSON length: ${jsonStr.length}")
            android.util.Log.d("LlmService", "Extracted JSON preview: ${jsonStr.take(200)}")
            
            val allCourses = json.decodeFromString<List<ParsedCourse>>(jsonStr)
            // Filter out invalid courses (missing required fields)
            val courses = allCourses.filter { it.isValid }
            val dropped = allCourses.size - courses.size
            android.util.Log.d("LlmService", "Parsed ${allCourses.size} courses, ${courses.size} valid, $dropped dropped")
            if (dropped > 0) {
                android.util.Log.w("LlmService", "Dropped courses: ${allCourses.filter { !it.isValid }.map { "${it.name}(day=${it.day},sp=${it.startPeriod},ep=${it.endPeriod},weeks=${it.weeks})" }}")
            }
            if (courses.isEmpty() && allCourses.isNotEmpty()) {
                return Result.failure(Exception("LLM 返回了 ${allCourses.size} 条课程但全部格式不正确，请重试"))
            }
            Result.success(courses)
        } catch (e: kotlinx.serialization.SerializationException) {
            android.util.Log.e("LlmService", "JSON parse error", e)
            Result.failure(Exception("LLM 返回格式错误: ${e.message?.take(100)}"))
        } catch (e: Exception) {
            android.util.Log.e("LlmService", "Parse error", e)
            Result.failure(Exception("解析失败: ${e.message?.take(100)}"))
        }
    }

    suspend fun parseScheduleFromImage(
        imageBase64: String,
        mimeType: String,
        baseUrl: String,
        apiKey: String,
        model: String
    ): Result<List<ParsedCourse>> {
        return try {
            val normalizedUrl = normalizeUrl(baseUrl)
            if (normalizedUrl.isBlank()) return Result.failure(Exception("请输入 Base URL"))

            // Build multimodal request JSON manually (content is array, not string)
            val escapedPrompt = VISION_PROMPT
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
            val requestJson = """
            {
                "model": "${model.lowercase().trim()}",
                "temperature": 0.1,
                "max_tokens": 8192,
                "enable_thinking": false,
                "messages": [
                    {
                        "role": "system",
                        "content": "$escapedPrompt"
                    },
                    {
                        "role": "user",
                        "content": [
                            {
                                "type": "text",
                                "text": "请解析这张课表截图中的所有课程信息。"
                            },
                            {
                                "type": "image_url",
                                "image_url": {
                                    "url": "data:$mimeType;base64,$imageBase64"
                                }
                            }
                        ]
                    }
                ]
            }
            """.trimIndent()

            android.util.Log.d("LlmService", "=== PARSE SCHEDULE FROM IMAGE ===")
            android.util.Log.d("LlmService", "URL: $normalizedUrl/chat/completions")
            android.util.Log.d("LlmService", "Model: ${model.lowercase().trim()}")
            android.util.Log.d("LlmService", "Image base64 length: ${imageBase64.length}")

            val response = client.post("$normalizedUrl/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(io.ktor.http.content.TextContent(requestJson, ContentType.Application.Json))
            }

            val body = response.bodyAsText()
            android.util.Log.d("LlmService", "LLM response status: ${response.status}")
            android.util.Log.d("LlmService", "LLM response length: ${body.length}")

            val llmResponse = json.decodeFromString<LlmResponse>(body)

            if (llmResponse.error != null) {
                val err = llmResponse.error
                val detail = buildString {
                    append(err.message ?: "")
                    if (err.param != null && err.param != err.message) append(" | param: ${err.param}")
                    if (err.code != null) append(" | code: ${err.code}")
                }.ifBlank { "未知错误" }
                return Result.failure(Exception("API 错误: $detail"))
            }

            val content = llmResponse.choices?.firstOrNull()?.message?.content
            val reasoningContent = llmResponse.choices?.firstOrNull()?.message?.reasoning_content
            val actualContent = content?.ifBlank { null } ?: reasoningContent
            android.util.Log.d("LlmService", "Content length: ${content?.length ?: 0}")
            android.util.Log.d("LlmService", "Reasoning content length: ${reasoningContent?.length ?: 0}")
            android.util.Log.d("LlmService", "Actual content preview: ${actualContent?.take(300)}")

            if (actualContent.isNullOrBlank()) {
                return Result.failure(Exception("LLM 返回为空，请检查模型是否支持图片输入"))
            }

            val jsonStr = extractJsonArray(actualContent)
            val allCourses = json.decodeFromString<List<ParsedCourse>>(jsonStr)
            // Filter out invalid courses (missing required fields)
            val courses = allCourses.filter { it.isValid }
            val dropped = allCourses.size - courses.size
            android.util.Log.d("LlmService", "Parsed ${allCourses.size} courses from image, ${courses.size} valid, $dropped dropped")
            if (courses.isEmpty() && allCourses.isNotEmpty()) {
                return Result.failure(Exception("LLM 返回了 ${allCourses.size} 条课程但全部格式不正确，请重试"))
            }
            Result.success(courses)
        } catch (e: kotlinx.serialization.SerializationException) {
            android.util.Log.e("LlmService", "JSON parse error", e)
            Result.failure(Exception("LLM 返回格式错误: ${e.message?.take(200)}"))
        } catch (e: Exception) {
            android.util.Log.e("LlmService", "Image parse error", e)
            Result.failure(Exception("解析失败: ${e.message?.take(200)}"))
        }
    }

    private fun extractJsonArray(text: String): String {
        // Try code block first
        try {
            val codeBlockRegex = Regex("""```(?:json)?\s*\n?(\[.*?])\s*\n?```""", RegexOption.DOT_MATCHES_ALL)
            codeBlockRegex.find(text)?.let { return it.groupValues[1] }
        } catch (e: Exception) {
            android.util.Log.e("LlmService", "codeBlockRegex failed", e)
        }

        // Try raw JSON array
        try {
            val arrayRegex = Regex("""\[\s*\{.*?}\s*]""", RegexOption.DOT_MATCHES_ALL)
            arrayRegex.find(text)?.let { return it.value }
        } catch (e: Exception) {
            android.util.Log.e("LlmService", "arrayRegex failed", e)
        }

        // Fallback: find first [ and last ]
        try {
            val start = text.indexOf('[')
            val end = text.lastIndexOf(']')
            if (start >= 0 && end > start) {
                return text.substring(start, end + 1)
            }
        } catch (e: Exception) {
            android.util.Log.e("LlmService", "fallback extract failed", e)
        }

        return text.trim()
    }
}
