package com.schedulex.data.model

data class LlmConfig(
    val provider: String = "custom",  // "openai", "deepseek", "qwen", "custom"
    val apiKey: String = "",
    val baseUrl: String = "",
    val model: String = ""
)
