package com.schedulex.ui.import_

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.schedulex.ScheduleXApp
import com.schedulex.data.model.*
import com.schedulex.llm.ParsedCourse
import kotlinx.coroutines.launch
import com.schedulex.widget.refreshAllWidgets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPreviewScreen(
    onNavigateBack: () -> Unit,
    onImportComplete: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as ScheduleXApp
    val scope = rememberCoroutineScope()
    val dataStore = context.scheduleDataStore

    var courses by remember { mutableStateOf(app.lastParsedCourses) }
    var selectedIndices by remember(courses) { mutableStateOf(courses.indices.toSet()) }
    var isImporting by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<String?>(null) }

    // 检查当前课表是否有课程
    var existingCourseCount by remember { mutableIntStateOf(0) }
    var showImportModeDialog by remember { mutableStateOf(false) }

    var scheduleSettings by remember { mutableStateOf(ScheduleSettings()) }
    LaunchedEffect(Unit) {
        scheduleSettings = loadScheduleSettings(dataStore)
    }
    val activeScheduleId = scheduleSettings.activeScheduleId

    LaunchedEffect(activeScheduleId) {
        app.courseRepository.getCoursesBySchedule(activeScheduleId).collect { existing ->
            existingCourseCount = existing.size
        }
    }

    val dayNames = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导入预览 (${courses.size} 条)") },
                navigationIcon = {
                    IconButton(onClick = {
                        app.lastParsedCourses = emptyList()
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (courses.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("没有解析到课程", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("请返回重新提取", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = onNavigateBack) { Text("返回") }
                    }
                }
                return@Scaffold
            }

            // Select all / deselect all
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { selectedIndices = courses.indices.toSet() },
                    modifier = Modifier.weight(1f)
                ) { Text("全选") }
                OutlinedButton(
                    onClick = { selectedIndices = emptySet() },
                    modifier = Modifier.weight(1f)
                ) { Text("全不选") }
            }

            // Course list
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(courses.size) { index ->
                    val course = courses[index]
                    val isSelected = index in selectedIndices
                    val courseColors = listOf(
                        "#4FC3F7", "#81C784", "#FFB74D", "#E57373",
                        "#BA68C8", "#4DB6AC", "#FFD54F", "#7986CB"
                    )
                    val colorHex = courseColors[index % courseColors.size]
                    val cardColor = Color(android.graphics.Color.parseColor(colorHex))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.surfaceVariant
                            else
                                MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(cardColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = course.name.take(1),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = course.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                val weekRange = if (course.weeks.isNotEmpty()) {
                                    val sorted = course.weeks.sorted()
                                    if (sorted == (sorted.first()..sorted.last()).toList()) {
                                        "${sorted.first()}-${sorted.last()}周"
                                    } else {
                                        sorted.joinToString(",") + "周"
                                    }
                                } else ""

                                val typeStr = when (course.type) {
                                    "odd" -> " 单周"
                                    "even" -> " 双周"
                                    else -> ""
                                }

                                Text(
                                    text = "${dayNames.getOrNull(course.day ?: 0) ?: "?"} 第${course.startPeriod ?: "?"}-${course.endPeriod ?: "?"}节 | $weekRange$typeStr",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!course.teacher.isNullOrBlank()) {
                                    Text(
                                        text = "👩‍🏫 ${course.teacher}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!course.location.isNullOrBlank()) {
                                    Text(
                                        text = "📍 ${course.location}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    selectedIndices = if (checked) {
                                        selectedIndices + index
                                    } else {
                                        selectedIndices - index
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Import result
            importResult?.let { result ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.startsWith("✅"))
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

            // Bottom buttons
            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            app.lastParsedCourses = emptyList()
                            onNavigateBack()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("取消") }
                    Button(
                        onClick = {
                            if (existingCourseCount > 0) {
                                showImportModeDialog = true
                            } else {
                                // 空课表，直接导入到当前课表
                                isImporting = true
                                scope.launch {
                                    val selected = selectedIndices.map { courses[it] }
                                    val result = importCourses(selected, app, activeScheduleId, clearExisting = false)
                                    importResult = result
                                    isImporting = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isImporting && selectedIndices.isNotEmpty()
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("导入 ${selectedIndices.size} 门课")
                        }
                    }
                }
            }
        }
    }

    // 覆盖 or 新建课表 对话框
    if (showImportModeDialog) {
        var newScheduleName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportModeDialog = false },
            title = { Text("导入方式") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("当前课表已有 $existingCourseCount 门课程，选择导入方式：")

                    // 覆盖当前课表
                    OutlinedButton(
                        onClick = {
                            showImportModeDialog = false
                            isImporting = true
                            scope.launch {
                                val selected = selectedIndices.map { courses[it] }
                                val result = importCourses(selected, app, activeScheduleId, clearExisting = true)
                                importResult = result
                                isImporting = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("覆盖当前课表")
                    }

                    // 新建课表导入
                    OutlinedTextField(
                        value = newScheduleName,
                        onValueChange = { newScheduleName = it },
                        label = { Text("新课表名称（留空自动生成）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            showImportModeDialog = false
                            isImporting = true
                            scope.launch {
                                val name = newScheduleName.trim().ifBlank { "导入课表 ${System.currentTimeMillis() % 10000}" }
                                val newId = "schedule_${System.currentTimeMillis()}"
                                createSchedule(dataStore, newId, name)
                                val selected = selectedIndices.map { courses[it] }
                                val result = importCourses(selected, app, newId, clearExisting = false)
                                importResult = "已导入到「$name」\n$result"
                                isImporting = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("新建课表并导入")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showImportModeDialog = false }) { Text("取消") }
            }
        )
    }
}

private suspend fun importCourses(
    parsedCourses: List<ParsedCourse>,
    app: ScheduleXApp,
    scheduleId: String,
    clearExisting: Boolean = false
): String {
    return try {
        if (clearExisting) {
            app.courseRepository.clearScheduleData(scheduleId)
        }

        val validCourses = parsedCourses.filter { it.isValid }
        if (validCourses.isEmpty()) return "❌ 没有有效的课程数据"

        val grouped = validCourses.groupBy { it.name }
        var totalCourses = 0
        var totalSlots = 0

        for ((name, slots) in grouped) {
            val firstSlot = slots.first()
            val course = Course(
                name = name,
                teacher = firstSlot.teacher,
                color = listOf(
                    "#4FC3F7", "#81C784", "#FFB74D", "#E57373",
                    "#BA68C8", "#4DB6AC", "#FFD54F", "#7986CB"
                ).random(),
                scheduleId = scheduleId
            )

            val uniqueSlots = slots.distinctBy { "${it.day}-${it.startPeriod}-${it.endPeriod}" }

            val timeSlots = uniqueSlots.mapNotNull { slot ->
                val day = slot.day ?: return@mapNotNull null
                val startPeriod = slot.startPeriod ?: return@mapNotNull null
                val endPeriod = slot.endPeriod ?: return@mapNotNull null
                TimeSlot(
                    courseId = 0,
                    day = day,
                    startPeriod = startPeriod,
                    endPeriod = endPeriod,
                    location = slot.location,
                    weeks = "[${slot.weeks.sorted().joinToString(",")}]",
                    type = when (slot.type) {
                        "odd" -> WeekType.ODD
                        "even" -> WeekType.EVEN
                        else -> WeekType.ALL
                    },
                    scheduleId = scheduleId
                )
            }

            app.courseRepository.insertCourseWithTimeSlots(course, timeSlots)
            totalCourses++
            totalSlots += timeSlots.size
        }

        refreshAllWidgets(app)
        "✅ 导入成功！共 $totalCourses 门课程，$totalSlots 个时间安排"
    } catch (e: Exception) {
        "❌ 导入失败: ${e.message}"
    }
}
