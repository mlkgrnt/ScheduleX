package com.schedulex.ui.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.schedulex.BuildConfig
import com.schedulex.data.model.ScheduleSettings
import com.schedulex.data.model.scheduleDataStore
import com.schedulex.data.model.loadScheduleSettings
import com.schedulex.data.model.saveScheduleSettings
import com.schedulex.data.model.saveScheduleSettingsWithAnchor
import com.schedulex.data.model.calculateActualWeek
import com.schedulex.widget.WidgetWideReceiver
import com.schedulex.widget.WidgetSingleReceiver
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToLlmSettings: () -> Unit,
    onNavigateToImport: () -> Unit = {},
    onNavigateToTimeSettings: () -> Unit = {},
    onNavigateToScreenshotImport: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dataStore = context.scheduleDataStore
    
    var settings by remember { mutableStateOf(ScheduleSettings()) }
    var showWeekDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        settings = loadScheduleSettings(dataStore)
    }

    val themeNames = listOf("跟随系统", "浅色模式", "深色模式")
    
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("设置") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // 基础设置 Section
            Text(
                text = "基础设置",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
            )

            ListItem(
                headlineContent = { Text("外观模式") },
                supportingContent = { Text(themeNames[settings.themeMode]) },
                leadingContent = {
                    Icon(Icons.Default.DarkMode, contentDescription = null)
                },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = { showThemeDialog = true })
            )

            ListItem(
                headlineContent = { Text("LLM 配置") },
                supportingContent = { Text("配置 AI 大语言模型接口") },
                leadingContent = {
                    Icon(Icons.Default.SmartToy, contentDescription = null)
                },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = onNavigateToLlmSettings)
            )

            ListItem(
                headlineContent = { Text("时间设置") },
                supportingContent = { Text("设置每节课时长、课间时间和上课时间") },
                leadingContent = {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = onNavigateToTimeSettings)
            )
            
            ListItem(
                headlineContent = { Text("当前周设置") },
                supportingContent = { Text("设置当前是第几周，用于显示本周课程") },
                leadingContent = {
                    Icon(Icons.Default.DateRange, contentDescription = null)
                },
                trailingContent = {
                    Text("第 ${calculateActualWeek(settings)} 周")
                },
                modifier = Modifier.clickable(onClick = { showWeekDialog = true })
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Import Section
            Text(
                text = "数据",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
            )

            ListItem(
                headlineContent = { Text("导入课表") },
                supportingContent = { Text("从教务系统导入课程") },
                leadingContent = {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = onNavigateToImport)
            )

            ListItem(
                headlineContent = { Text("截图导入") },
                supportingContent = { Text("用视觉LLM解析课表") },
                leadingContent = {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = onNavigateToScreenshotImport)
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // About Section
            Text(
                text = "关于",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
            )

            ListItem(
                headlineContent = { Text("ScheduleX") },
                supportingContent = { Text("版本 ${BuildConfig.VERSION_NAME}") },
                leadingContent = {
                    Icon(Icons.Default.Info, contentDescription = null)
                }
            )

            ListItem(
                headlineContent = { Text("GitHub") },
                supportingContent = { Text("开源仓库 · 欢迎 Star & PR") },
                leadingContent = {
                    Icon(Icons.Default.Code, contentDescription = null)
                },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                },
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(
                        "https://github.com/mlkgrnt/ScheduleX"
                    ))
                    context.startActivity(intent)
                }
            )
        }
    }
    
    // 主题选择对话框
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("外观模式") },
            text = {
                Column {
                    themeNames.forEachIndexed { index, name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        val newSettings = settings.copy(themeMode = index)
                                        saveScheduleSettings(dataStore, newSettings)
                                        settings = loadScheduleSettings(dataStore)
                                        // 小组件同步刷新
                                        WidgetWideReceiver.notifyAll(context)
                                        WidgetSingleReceiver.notifyAllUpdate(context)
                                        showThemeDialog = false
                                    }
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            RadioButton(
                                selected = settings.themeMode == index,
                                onClick = {
                                    scope.launch {
                                        val newSettings = settings.copy(themeMode = index)
                                        saveScheduleSettings(dataStore, newSettings)
                                        settings = loadScheduleSettings(dataStore)
                                        WidgetWideReceiver.notifyAll(context)
                                        WidgetSingleReceiver.notifyAllUpdate(context)
                                        showThemeDialog = false
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(name, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 周设置对话框
    if (showWeekDialog) {
        var weekInput by remember { mutableStateOf(calculateActualWeek(settings).toString()) }
        
        AlertDialog(
            onDismissRequest = { showWeekDialog = false },
            title = { Text("设置当前周") },
            text = {
                Column {
                    Text("请输入当前是第几周（1-20）")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = weekInput,
                        onValueChange = { weekInput = it.filter { c -> c.isDigit() } },
                        label = { Text("周数") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val week = weekInput.toIntOrNull() ?: 1
                        if (week in 1..20) {
                            scope.launch {
                                val newSettings = settings.copy(currentWeek = week)
                                saveScheduleSettingsWithAnchor(dataStore, newSettings)
                                settings = loadScheduleSettings(dataStore)
                                // 小组件同步更新
                                WidgetWideReceiver.notifyAll(context)
                                WidgetSingleReceiver.notifyAllUpdate(context)
                                showWeekDialog = false
                            }
                        } else {
                            showWeekDialog = false
                        }
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWeekDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
