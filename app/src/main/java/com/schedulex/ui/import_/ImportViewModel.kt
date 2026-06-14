package com.schedulex.ui.import_

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schedulex.data.repository.CourseRepository
import com.schedulex.llm.LlmService
import com.schedulex.llm.ParsedCourse
import com.schedulex.llmDataStore
import com.schedulex.ui.settings.LlmKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ImportUiState(
    val isLoading: Boolean = false,
    val courses: List<ParsedCourse> = emptyList(),
    val error: String? = null,
    val status: String = ""
)

@kotlinx.serialization.Serializable
data class JsExtractResult(
    val error: String? = null,
    val system: String? = null,
    val courses: List<JsCourse>? = null,
    val filteredHtml: String? = null  // 智能过滤后的HTML，供LLM兜底
)

@kotlinx.serialization.Serializable
data class JsCourse(
    val d: Int = 0,          // day (1-7)
    val s: Int = 0,          // startPeriod
    val e: Int = 0,          // endPeriod
    val n: String? = null,   // name
    val t: String? = null,   // teacher
    val r: String? = null,   // room
    val w: List<Int>? = null, // weeks
    val tp: Int = 0,         // type: 0=all, 1=odd, 2=even
    val raw: String? = null  // raw text for LLM fallback
)

class ImportViewModel(
    private val courseRepository: CourseRepository,
    private val llmService: LlmService,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    /**
     * 解析JS输出：已解析的直接用，只有raw才走LLM
     */
    suspend fun parseJsOutput(jsonStr: String) {
        _uiState.value = ImportUiState(isLoading = true, status = "正在解析...")

        try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
            val jsResult = json.decodeFromString<JsExtractResult>(jsonStr)

            if (jsResult.error != null) {
                _uiState.value = ImportUiState(error = "提取失败: ${jsResult.error}")
                return
            }

            val courses = jsResult.courses ?: emptyList()

            // JS解析完全失败，但有filteredHtml → 用LLM兜底
            if (courses.isEmpty() && !jsResult.filteredHtml.isNullOrBlank()) {
                android.util.Log.d("ImportViewModel", "JS failed, using filteredHtml (${jsResult.filteredHtml.length} chars) for LLM fallback")
                parseWithLlm(jsResult.filteredHtml)
                return
            }

            if (courses.isEmpty()) {
                _uiState.value = ImportUiState(error = "未解析到课程，请确认已在课表页面")
                return
            }

            // 分离：JS已解析的 vs 需要LLM兜底的raw
            val parsed = mutableListOf<ParsedCourse>()
            val rawItems = mutableListOf<JsCourse>()

            for (c in courses) {
                if (c.n != null && c.w != null && c.w.isNotEmpty()) {
                    // JS已解析完成，直接转ParsedCourse
                    parsed.add(ParsedCourse(
                        name = c.n,
                        teacher = c.t?.ifBlank { null },
                        location = c.r?.ifBlank { null },
                        day = c.d,
                        startPeriod = c.s,
                        endPeriod = c.e,
                        weeks = c.w,
                        type = when (c.tp) { 1 -> "odd"; 2 -> "even"; else -> "all" }
                    ))
                } else if (c.raw != null && c.raw.length > 2) {
                    rawItems.add(c)
                }
            }

            android.util.Log.d("ImportViewModel", "JS parsed: ${parsed.size}, raw: ${rawItems.size}")

            // 如果有raw项，发给LLM兜底
            if (rawItems.isNotEmpty()) {
                _uiState.value = ImportUiState(isLoading = true, status = "AI解析${rawItems.size}条未识别课程...")

                val prefs = context.llmDataStore.data.first()
                val baseUrl = prefs[LlmKeys.BASE_URL] ?: ""
                val apiKey = prefs[LlmKeys.API_KEY] ?: ""
                val model = prefs[LlmKeys.MODEL] ?: ""

                if (baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()) {
                    val jsonArray = kotlinx.serialization.json.JsonArray(
                        rawItems.map { c ->
                            kotlinx.serialization.json.buildJsonObject {
                                put("day", kotlinx.serialization.json.JsonPrimitive(c.d))
                                put("startPeriod", kotlinx.serialization.json.JsonPrimitive(c.s))
                                put("endPeriod", kotlinx.serialization.json.JsonPrimitive(c.e))
                                put("raw", kotlinx.serialization.json.JsonPrimitive(c.raw!!))
                            }
                        }
                    )
                    val rawJson = jsonArray.toString()
                    val result = llmService.parseScheduleHtml(rawJson, baseUrl, apiKey, model)
                    result.onSuccess { llmCourses -> parsed.addAll(llmCourses) }
                    result.onFailure { e ->
                        android.util.Log.w("ImportViewModel", "LLM fallback failed for raw items: ${e.message}")
                    }
                }
            }

            val validCourses = parsed.filter { it.isValid }

            if (validCourses.isEmpty()) {
                _uiState.value = ImportUiState(error = "未解析到有效课程")
            } else {
                val app = context.applicationContext as? com.schedulex.ScheduleXApp
                app?.lastParsedCourses = validCourses
                _uiState.value = ImportUiState(
                    courses = validCourses,
                    status = "解析完成，共 ${validCourses.size} 条"
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("ImportViewModel", "JS parse exception", e)
            _uiState.value = ImportUiState(error = "解析异常: ${e.message}")
        }
    }

    suspend fun parseWithLlm(html: String) {
        _uiState.value = ImportUiState(isLoading = true, status = "正在调用 LLM 解析...")

        try {
            val prefs = context.llmDataStore.data.first()
            val baseUrl = prefs[LlmKeys.BASE_URL] ?: ""
            val apiKey = prefs[LlmKeys.API_KEY] ?: ""
            val model = prefs[LlmKeys.MODEL] ?: ""

            android.util.Log.d("ImportViewModel", "=== PARSE WITH LLM ===")
            android.util.Log.d("ImportViewModel", "HTML length: ${html.length}")
            android.util.Log.d("ImportViewModel", "Base URL: $baseUrl")
            android.util.Log.d("ImportViewModel", "Model: $model")
            android.util.Log.d("ImportViewModel", "API Key length: ${apiKey.length}")

            if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
                _uiState.value = ImportUiState(
                    error = "请先在 设置 → LLM 配置 中配置 AI 模型"
                )
                return
            }

            _uiState.value = ImportUiState(isLoading = true, status = "正在发送到 AI (${html.length / 1024}KB)...")

            val result = llmService.parseScheduleHtml(html, baseUrl, apiKey, model)

            result.fold(
                onSuccess = { courses ->
                    android.util.Log.d("ImportViewModel", "Parsed ${courses.size} courses")
                    if (courses.isEmpty()) {
                        _uiState.value = ImportUiState(
                            error = "未解析到课程，请确认已在课表页面"
                        )
                    } else {
                        // Store in app-level shared state for preview screen
                        val app = context.applicationContext as? com.schedulex.ScheduleXApp
                        app?.lastParsedCourses = courses
                        _uiState.value = ImportUiState(
                            courses = courses,
                            status = "解析完成，共 ${courses.size} 条"
                        )
                    }
                },
                onFailure = { e ->
                    android.util.Log.e("ImportViewModel", "LLM parse failed", e)
                    _uiState.value = ImportUiState(
                        error = "LLM 解析失败: ${e.message}"
                    )
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("ImportViewModel", "Parse exception", e)
            _uiState.value = ImportUiState(
                error = "解析异常: ${e.message}"
            )
        }
    }

    fun clearCourses() {
        _uiState.value = ImportUiState()
    }
    
    fun setParsedCourses(courses: List<ParsedCourse>) {
        android.util.Log.d("ImportViewModel", "Setting ${courses.size} parsed courses")
        if (courses.isEmpty()) {
            _uiState.value = ImportUiState(error = "未解析到课程")
        } else {
            val app = context.applicationContext as? com.schedulex.ScheduleXApp
            app?.lastParsedCourses = courses
            _uiState.value = ImportUiState(
                courses = courses,
                status = "解析完成，共 ${courses.size} 条"
            )
        }
    }
}
