package com.schedulex.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.schedulex.llmDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal object LlmKeys {
    val PROVIDER = stringPreferencesKey("llm_provider")
    val BASE_URL = stringPreferencesKey("llm_base_url")
    val API_KEY = stringPreferencesKey("llm_api_key")
    val MODEL = stringPreferencesKey("llm_model")
}

data class LlmProviderDefaults(
    val name: String,
    val baseUrl: String
)

private val providers = listOf(
    LlmProviderDefaults("MiMo", "https://token-plan-cn.xiaomimimo.com/v1"),
    LlmProviderDefaults("DeepSeek", "https://api.deepseek.com/v1"),
    LlmProviderDefaults("OpenAI", "https://api.openai.com/v1"),
    LlmProviderDefaults("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
    LlmProviderDefaults("自定义", "")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedProviderIndex by remember { mutableIntStateOf(0) }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showProviderDialog by remember { mutableStateOf(false) }

    // Model list from API
    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedModel by remember { mutableStateOf("") }
    var isFetchingModels by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    var modelFetchError by remember { mutableStateOf<String?>(null) }

    val selectedProvider = providers[selectedProviderIndex]

    // Load saved settings
    LaunchedEffect(Unit) {
        val prefs = context.llmDataStore.data.first()
        val savedProvider = prefs[LlmKeys.PROVIDER] ?: "MiMo"
        val providerIndex = providers.indexOfFirst { it.name == savedProvider }.coerceAtLeast(0)
        selectedProviderIndex = providerIndex
        baseUrl = prefs[LlmKeys.BASE_URL] ?: providers[providerIndex].baseUrl
        apiKey = prefs[LlmKeys.API_KEY] ?: ""
        selectedModel = prefs[LlmKeys.MODEL] ?: ""
        isLoading = false
    }

    // Auto-fetch models when both URL and Key are present and we have no models yet
    LaunchedEffect(baseUrl, apiKey) {
        if (!isLoading && baseUrl.isNotBlank() && apiKey.isNotBlank() && availableModels.isEmpty() && selectedModel.isBlank()) {
            isFetchingModels = true
            modelFetchError = null
            val app = context.applicationContext as com.schedulex.ScheduleXApp
            val result = app.llmService.fetchModels(baseUrl, apiKey)
            result.fold(
                onSuccess = { models ->
                    availableModels = models
                    if (selectedModel.isBlank() && models.isNotEmpty()) {
                        selectedModel = models.first()
                    }
                },
                onFailure = { modelFetchError = it.message }
            )
            isFetchingModels = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LLM 配置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Provider selector
            OutlinedTextField(
                value = selectedProvider.name,
                onValueChange = {},
                readOnly = true,
                label = { Text("模型提供商") },
                trailingIcon = {
                    IconButton(onClick = { showProviderDialog = true }) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "展开")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showProviderDialog = true },
                enabled = false
            )

            // Base URL
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it; availableModels = emptyList(); selectedModel = "" },
                label = { Text("Base URL") },
                placeholder = { Text("https://api.example.com/v1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // API Key
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; availableModels = emptyList(); selectedModel = "" },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Text(if (showPassword) "\uD83D\uDE48" else "\uD83D\uDC41")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Fetch models button + model dropdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        isFetchingModels = true
                        modelFetchError = null
                        availableModels = emptyList()
                        scope.launch {
                            val app = context.applicationContext as com.schedulex.ScheduleXApp
                            val result = app.llmService.fetchModels(baseUrl, apiKey)
                            result.fold(
                                onSuccess = { models ->
                                    availableModels = models
                                    if (selectedModel !in models && models.isNotEmpty()) {
                                        selectedModel = models.first()
                                    }
                                },
                                onFailure = { modelFetchError = it.message }
                            )
                            isFetchingModels = false
                        }
                    },
                    enabled = baseUrl.isNotBlank() && apiKey.isNotBlank() && !isFetchingModels
                ) {
                    if (isFetchingModels) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("获取中...")
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("获取模型")
                    }
                }
            }

            // Model dropdown
            if (availableModels.isNotEmpty()) {
                Box {
                    OutlinedTextField(
                        value = selectedModel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("模型") },
                        trailingIcon = {
                            IconButton(onClick = { modelDropdownExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "展开")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { modelDropdownExpanded = true },
                        enabled = false
                    )
                    DropdownMenu(
                        expanded = modelDropdownExpanded,
                        onDismissRequest = { modelDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        availableModels.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m) },
                                onClick = {
                                    selectedModel = m
                                    modelDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            } else if (modelFetchError != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        text = "\u274C ${modelFetchError}",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                // Manual model entry as fallback
                OutlinedTextField(
                    value = selectedModel,
                    onValueChange = { selectedModel = it },
                    label = { Text("模型名称（手动输入）") },
                    placeholder = { Text("mimo-v2.5-pro") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (selectedModel.isNotBlank()) {
                // Show saved model (haven't fetched list yet)
                OutlinedTextField(
                    value = selectedModel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("已保存模型") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false
                )
            }

            // Test result
            testResult?.let { result ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.startsWith("\u2705"))
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = result,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        testResult = "\u23F3 正在测试连接..."
                        scope.launch {
                            val app = context.applicationContext as com.schedulex.ScheduleXApp
                            val result = app.llmService.testConnection(baseUrl, apiKey, selectedModel)
                            testResult = result.fold(
                                onSuccess = { "\u2705 $it" },
                                onFailure = { "\u274C ${it.message}" }
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = selectedModel.isNotBlank()
                ) {
                    Text("测试连接")
                }
                Button(
                    onClick = {
                        isSaving = true
                        scope.launch {
                            context.llmDataStore.edit { prefs ->
                                prefs[LlmKeys.PROVIDER] = selectedProvider.name
                                prefs[LlmKeys.BASE_URL] = baseUrl
                                prefs[LlmKeys.API_KEY] = apiKey
                                prefs[LlmKeys.MODEL] = selectedModel
                            }
                            isSaving = false
                            testResult = "\u2705 配置已保存"
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isSaving && selectedModel.isNotBlank()
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("保存")
                    }
                }
            }
        }
    }

    // Provider selection dialog
    if (showProviderDialog) {
        AlertDialog(
            onDismissRequest = { showProviderDialog = false },
            title = { Text("选择模型提供商") },
            text = {
                Column {
                    providers.forEachIndexed { index, provider ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedProviderIndex = index
                                    if (provider.name != "自定义") {
                                        baseUrl = provider.baseUrl
                                    }
                                    availableModels = emptyList()
                                    selectedModel = ""
                                    showProviderDialog = false
                                }
                                .padding(12.dp)
                        ) {
                            RadioButton(
                                selected = selectedProviderIndex == index,
                                onClick = {
                                    selectedProviderIndex = index
                                    if (provider.name != "自定义") {
                                        baseUrl = provider.baseUrl
                                    }
                                    availableModels = emptyList()
                                    selectedModel = ""
                                    showProviderDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(provider.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProviderDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
