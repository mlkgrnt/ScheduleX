package com.schedulex

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.schedulex.data.db.AppDatabase
import com.schedulex.data.repository.CourseRepository
import com.schedulex.llm.LlmService
import com.schedulex.llm.ParsedCourse
import com.schedulex.ui.settings.LlmKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

val Context.llmDataStore: DataStore<Preferences> by preferencesDataStore(name = "llm_settings")

class ScheduleXApp : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val courseRepository by lazy {
        CourseRepository(database.courseDao(), database.timeSlotDao())
    }
    val llmService by lazy { LlmService() }

    // Shared state: parsed courses from import flow
    @Volatile var lastParsedCourses: List<ParsedCourse> = emptyList()
    
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        autoConfigureLlm()
    }

    private fun autoConfigureLlm() {
        appScope.launch {
            try {
                val prefs = llmDataStore.data.first()
                val existingKey = prefs[LlmKeys.API_KEY]
                if (!existingKey.isNullOrBlank()) return@launch

                // Read API key from raw resource (dev only)
                val key = try {
                    resources.openRawResource(R.raw.api_key)
                        .bufferedReader().readText().trim()
                } catch (_: Exception) { "" }
                
                if (key.isBlank()) return@launch

                android.util.Log.d("ScheduleXApp", "Auto-configuring LLM from resource")
                llmDataStore.edit { settings ->
                    settings[LlmKeys.PROVIDER] = "MiMo"
                    settings[LlmKeys.BASE_URL] = "https://token-plan-cn.xiaomimimo.com/v1"
                    settings[LlmKeys.API_KEY] = key
                    settings[LlmKeys.MODEL] = "mimo-v2.5-pro"
                }
                android.util.Log.d("ScheduleXApp", "LLM auto-configured successfully")
            } catch (e: Exception) {
                android.util.Log.e("ScheduleXApp", "Auto-config failed: ${e.message}")
            }
        }
    }
}
