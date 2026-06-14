package com.schedulex.data.model

data class School(
    val code: String,
    val name: String,
    val province: String,
    val systemType: String,  // "qiangzhi", "zhengfang", "urp", etc.
    val loginUrl: String,
    val scheduleUrl: String? = null
)
