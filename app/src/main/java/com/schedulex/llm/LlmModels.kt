package com.schedulex.llm

import kotlinx.serialization.Serializable

@Serializable
data class LlmRequest(
    val model: String,
    val messages: List<LlmMessage>,
    val temperature: Double = 0.1,
    val max_tokens: Int = 4096,
    val enable_thinking: Boolean? = null
)

@Serializable
data class LlmMessage(
    val role: String,
    val content: String,
    val reasoning_content: String? = null
)

@Serializable
data class LlmResponse(
    val choices: List<LlmChoice>? = null,
    val error: LlmError? = null
)

@Serializable
data class LlmChoice(
    val message: LlmMessage
)

@Serializable
data class LlmError(
    val message: String? = null,
    val type: String? = null,
    val param: String? = null,
    val code: String? = null
)

@Serializable
data class ParsedCourse(
    val name: String,
    val teacher: String? = null,
    val location: String? = null,
    val day: Int? = null,
    val startPeriod: Int? = null,
    val endPeriod: Int? = null,
    val weeks: List<Int> = emptyList(),
    val type: String = "all"
) {
    /** Check if this course has all required fields for import */
    val isValid: Boolean get() = name.isNotBlank() && day != null && day in 1..7 &&
                                  startPeriod != null && startPeriod!! >= 1 &&
                                  endPeriod != null && endPeriod!! >= startPeriod!! &&
                                  weeks.isNotEmpty()
}

@Serializable
data class ModelsResponse(
    val `object`: String? = null,
    val data: List<ModelInfo>? = null
)

@Serializable
data class ModelInfo(
    val id: String? = null,
    val `object`: String? = null,
    val owned_by: String? = null
)
