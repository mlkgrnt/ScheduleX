package com.schedulex.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.schedulex.data.model.ScheduleSettings
import com.schedulex.data.model.scheduleDataStore
import com.schedulex.data.model.loadScheduleSettings
import com.schedulex.data.model.saveScheduleSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dataStore = context.scheduleDataStore

    var settings by remember { mutableStateOf(ScheduleSettings()) }
    var totalPeriods by remember { mutableFloatStateOf(15f) }
    var periodStartsText by remember { mutableStateOf(List(15) { "" }) }
    var periodDurationsText by remember { mutableStateOf(List(15) { "45" }) }
    var useSameDuration by remember { mutableStateOf(true) }
    var classDuration by remember { mutableStateOf("45") }
    var isLoaded by remember { mutableStateOf(false) }
    var showSaveSuccess by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 时间选择器状态
    var showTimePicker by remember { mutableStateOf(false) }
    var editingPeriodIndex by remember { mutableIntStateOf(-1) }

    // 保存成功后显示Snackbar
    LaunchedEffect(showSaveSuccess) {
        if (showSaveSuccess) {
            snackbarHostState.showSnackbar(
                message = "设置已保存",
                duration = SnackbarDuration.Short
            )
            showSaveSuccess = false
        }
    }

    // Load saved settings
    LaunchedEffect(Unit) {
        val loaded = loadScheduleSettings(dataStore)
        settings = loaded
        classDuration = loaded.classDuration.toString()
        totalPeriods = loaded.totalPeriods.toFloat()
        useSameDuration = loaded.useSameDuration
        periodStartsText = loaded.periodStarts.map { minutesToString(it) }
        periodDurationsText = loaded.periodDurations.map { it.toString() }
        isLoaded = true
    }

    // 计算结束时间
    fun getEndTime(startTime: String, duration: Int): String {
        val startMinutes = stringToMinutes(startTime)
        val endMinutes = startMinutes + duration
        return minutesToString(endMinutes)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑时间表") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                val periodCount = totalPeriods.toInt()
                                val periodDurations = if (useSameDuration) {
                                    List(periodCount) { classDuration.toIntOrNull() ?: 45 }
                                } else {
                                    periodDurationsText.map { it.toIntOrNull() ?: 45 }
                                }
                                
                                // 保留原有的currentWeek和weekAnchorDate
                                val newSettings = settings.copy(
                                    periodStarts = periodStartsText.map { stringToMinutes(it) },
                                    classDuration = classDuration.toIntOrNull() ?: 45,
                                    totalPeriods = periodCount,
                                    useSameDuration = useSameDuration,
                                    periodDurations = periodDurations
                                )
                                saveScheduleSettings(dataStore, newSettings)
                                settings = newSettings
                                showSaveSuccess = true
                            }
                        }
                    ) {
                        Text("保存")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 时长设置卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 开关：每节课时长是否相同
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("每节课时长相同")
                        Checkbox(
                            checked = useSameDuration,
                            onCheckedChange = { useSameDuration = it }
                        )
                    }

                    // 统一时长输入
                    if (useSameDuration) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("一节课时长")
                            Text("${classDuration} 分钟")
                        }
                        OutlinedTextField(
                            value = classDuration,
                            onValueChange = { classDuration = it.filter { c -> c.isDigit() } },
                            label = { Text("分钟") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }

            // 总节数滑块
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("总节数")
                        Text("${totalPeriods.toInt()} 节")
                    }
                    Slider(
                        value = totalPeriods,
                        onValueChange = { newValue ->
                            val newTotal = newValue.toInt()
                            val oldTotal = totalPeriods.toInt()
                            totalPeriods = newValue
                            
                            // 扩展或截断列表
                            if (newTotal > oldTotal) {
                                periodStartsText = periodStartsText + List(newTotal - oldTotal) { "" }
                                periodDurationsText = periodDurationsText + List(newTotal - oldTotal) { "45" }
                            } else if (newTotal < oldTotal) {
                                periodStartsText = periodStartsText.take(newTotal)
                                periodDurationsText = periodDurationsText.take(newTotal)
                            }
                        },
                        valueRange = 1f..20f,
                        steps = 18,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 课表时间卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val periodCount = totalPeriods.toInt()
                    
                    for (i in 1..periodCount) {
                        val startTime = periodStartsText.getOrElse(i - 1) { "" }
                        val duration = if (useSameDuration) {
                            classDuration.toIntOrNull() ?: 45
                        } else {
                            periodDurationsText.getOrElse(i - 1) { "45" }.toIntOrNull() ?: 45
                        }
                        val endTime = if (startTime.isNotEmpty()) getEndTime(startTime, duration) else ""
                        
                        PeriodTimeRow(
                            period = i,
                            startTime = startTime,
                            endTime = endTime,
                            duration = duration,
                            showDurationInput = !useSameDuration,
                            onTimeClick = {
                                editingPeriodIndex = i - 1
                                showTimePicker = true
                            },
                            onDurationChange = { newDuration ->
                                periodDurationsText = periodDurationsText.toMutableList().apply {
                                    set(i - 1, newDuration)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // 时间选择器对话框
    if (showTimePicker && editingPeriodIndex >= 0) {
        val currentTime = periodStartsText.getOrElse(editingPeriodIndex) { "08:00" }
        val parts = currentTime.split(":")
        val initialHour = parts.getOrElse(0) { "8" }.toIntOrNull() ?: 8
        val initialMinute = parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0
        
        TimePickerDialog(
            initialHour = initialHour,
            initialMinute = initialMinute,
            period = editingPeriodIndex + 1,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                periodStartsText = periodStartsText.toMutableList().apply {
                    set(editingPeriodIndex, String.format("%02d:%02d", hour, minute))
                }
                showTimePicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    period: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置第${period}节开始时间") },
        text = {
            TimePicker(state = timePickerState)
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(timePickerState.hour, timePickerState.minute) }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun PeriodTimeRow(
    period: Int,
    startTime: String,
    endTime: String,
    duration: Int,
    showDurationInput: Boolean,
    onTimeClick: () -> Unit,
    onDurationChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 节次
        Text(
            text = "第${period}节",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.width(60.dp)
        )
        
        // 开始时间
        Surface(
            onClick = onTimeClick,
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = if (startTime.isNotEmpty()) startTime else "点击设置",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = if (startTime.isNotEmpty()) MaterialTheme.colorScheme.onSurface 
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // 分隔符
        Text(
            text = " - ",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        
        // 结束时间
        Text(
            text = if (endTime.isNotEmpty()) endTime else "--:--",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        
        // 时长输入（如果需要）
        if (showDurationInput) {
            OutlinedTextField(
                value = duration.toString(),
                onValueChange = onDurationChange,
                label = { Text("分钟") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(80.dp),
                singleLine = true
            )
        }
    }
}

private fun minutesToString(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return String.format("%02d:%02d", h, m)
}

private fun stringToMinutes(time: String): Int {
    val parts = time.split(":")
    if (parts.size != 2) return 480
    val h = parts[0].toIntOrNull() ?: 8
    val m = parts[1].toIntOrNull() ?: 0
    return h * 60 + m
}
