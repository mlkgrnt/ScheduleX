package com.schedulex.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schedulex.ScheduleXApp
import com.schedulex.data.model.*
import com.schedulex.widget.refreshAllWidgets
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

private val dayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

private fun parseWeeks(weeksJson: String): List<Int> {
    return try {
        weeksJson.removeSurrounding("[", "]")
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
    } catch (_: Exception) { emptyList() }
}

private fun isInWeek(timeSlot: TimeSlot, week: Int): Boolean {
    val weeks = parseWeeks(timeSlot.weeks)
    if (week !in weeks) return false
    return when (timeSlot.type) {
        WeekType.ALL -> true
        WeekType.ODD -> week % 2 == 1
        WeekType.EVEN -> week % 2 == 0
    }
}

@Composable
fun HomeScreen(
    onNavigateToAddCourse: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToScreenshotImport: () -> Unit = {},
    onNavigateToPdfImport: () -> Unit = {},
    onNavigateToEditCourse: ((Long) -> Unit)? = null
) {
    val app = LocalContext.current.applicationContext as ScheduleXApp
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dataStore = context.scheduleDataStore

    var scheduleSettings by remember { mutableStateOf(ScheduleSettings()) }
    var currentWeek by remember { mutableIntStateOf(1) }
    var showWeekSelector by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showNewScheduleDialog by remember { mutableStateOf(false) }
    var showScheduleMenu by remember { mutableStateOf(false) }
    var scheduleNames by remember { mutableStateOf(mapOf("default" to "我的课表")) }
    val activeScheduleId = scheduleSettings.activeScheduleId

    LaunchedEffect(Unit) {
        scheduleSettings = loadScheduleSettings(dataStore)
        currentWeek = calculateActualWeek(scheduleSettings)
        scheduleNames = loadScheduleNames(dataStore)
    }

    val courses by app.courseRepository.getCoursesBySchedule(activeScheduleId)
        .collectAsState(initial = emptyList())
    val allTimeSlots by app.courseRepository.getTimeSlotsBySchedule(activeScheduleId)
        .collectAsState(initial = emptyList())

    val courseMap = remember(courses) { courses.associateBy { it.id } }
    val weekTimeSlots = remember(allTimeSlots, currentWeek) {
        allTimeSlots.filter { isInWeek(it, currentWeek) }
    }
    val slotBlocks = remember(weekTimeSlots, courseMap) {
        weekTimeSlots.mapNotNull { slot ->
            val course = courseMap[slot.courseId] ?: return@mapNotNull null
            CourseBlock(course, slot)
        }
    }

    val activeScheduleName = scheduleNames[activeScheduleId] ?: "我的课表"

    // 不用 Scaffold，直接 Column，避免 innerPadding 导致底部留白
    Column(modifier = Modifier.fillMaxSize()) {
        // 瘦顶栏
        Surface(
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Row(
                        modifier = Modifier.clickable { showScheduleMenu = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = activeScheduleName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "切换课表",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showScheduleMenu,
                        onDismissRequest = { showScheduleMenu = false }
                    ) {
                        scheduleNames.forEach { (id, name) ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (id == activeScheduleId) {
                                            Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(name)
                                    }
                                },
                                onClick = {
                                    showScheduleMenu = false
                                    if (id != activeScheduleId) {
                                        scope.launch {
                                            val newSettings = scheduleSettings.copy(activeScheduleId = id)
                                            saveScheduleSettings(dataStore, newSettings)
                                            scheduleSettings = loadScheduleSettings(dataStore)
                                            scheduleNames = loadScheduleNames(dataStore)
                                            refreshAllWidgets(app)
                                        }
                                    }
                                }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("新建课表")
                                }
                            },
                            onClick = {
                                showScheduleMenu = false
                                showNewScheduleDialog = true
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(onClick = { showImportDialog = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.FileDownload, contentDescription = "导入", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onNavigateToAddCourse, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "添加课程", modifier = Modifier.size(20.dp))
                }
            }
        }

        // 内容区：weight(1f) 填满剩余空间
        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
            WeekSelector(
                currentWeek = currentWeek,
                onWeekChange = { currentWeek = it },
                onShowWeekPicker = { showWeekSelector = true }
            )

            if (courses.isEmpty()) {
                EmptyScheduleView(onNavigateToAddCourse)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    ScheduleGrid(
                        blocks = slotBlocks,
                        scheduleSettings = scheduleSettings,
                        onCourseClick = { courseId -> onNavigateToEditCourse?.invoke(courseId) }
                    )
                }
            }
        }
    }

    // 对话框
    if (showWeekSelector) {
        WeekPickerDialog(
            currentWeek = currentWeek,
            onWeekSelected = { currentWeek = it; showWeekSelector = false },
            onDismiss = { showWeekSelector = false }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("选择导入方式") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("请选择导入课表的方式：")
                }
            },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { showImportDialog = false; onNavigateToImport() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("教务系统导入")
                    }
                    TextButton(onClick = { showImportDialog = false; onNavigateToPdfImport() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PDF 导入")
                    }
                    TextButton(onClick = { showImportDialog = false; onNavigateToScreenshotImport() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("截图导入")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("取消") }
            }
        )
    }

    if (showNewScheduleDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewScheduleDialog = false },
            title = { Text("新建课表") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("课表名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newName.trim()
                    if (name.isNotBlank()) {
                        scope.launch {
                            val newId = "schedule_${System.currentTimeMillis()}"
                            createSchedule(dataStore, newId, name)
                            scheduleSettings = loadScheduleSettings(dataStore)
                            scheduleNames = loadScheduleNames(dataStore)
                            showNewScheduleDialog = false
                        }
                    }
                }) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showNewScheduleDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun WeekSelector(
    currentWeek: Int,
    onWeekChange: (Int) -> Unit,
    onShowWeekPicker: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { if (currentWeek > 1) onWeekChange(currentWeek - 1) }, enabled = currentWeek > 1, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "上一周", modifier = Modifier.size(18.dp))
        }
        Text(
            text = "第 $currentWeek 周",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp).clickable { onShowWeekPicker() }
        )
        IconButton(onClick = { onWeekChange(currentWeek + 1) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.ChevronRight, contentDescription = "下一周", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun WeekPickerDialog(currentWeek: Int, onWeekSelected: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择周次") },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                for (week in 1..30) {
                    ListItem(
                        headlineContent = { Text("第 $week 周") },
                        leadingContent = { if (week == currentWeek) Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        modifier = Modifier.clickable { onWeekSelected(week) },
                        colors = if (week == currentWeek) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ListItemDefaults.colors()
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun EmptyScheduleView(onAdd: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "📚", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "还没有课程", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            FilledTonalButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加课程")
            }
        }
    }
}

data class CourseBlock(val course: Course, val slot: TimeSlot)

private const val TOTAL_DAYS = 7

@Composable
private fun ScheduleGrid(blocks: List<CourseBlock>, scheduleSettings: ScheduleSettings, onCourseClick: (Long) -> Unit) {
    val cellWidth = 56.dp
    val periodLabelWidth = 56.dp
    val headerHeight = 28.dp
    val cellHeight = 60.dp
    val totalPeriods = scheduleSettings.totalPeriods

    val blockColors = remember(blocks) {
        blocks.associate { block ->
            val color = try { Color(android.graphics.Color.parseColor(block.course.color)) } catch (_: Exception) { Color(0xFF4FC3F7) }
            block to color
        }
    }

    val hScrollState = rememberScrollState()
    val vScrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxWidth()) {
        // 1. 网格本体
        Column(modifier = Modifier.verticalScroll(vScrollState)) {
            Spacer(modifier = Modifier.height(headerHeight))
            Row(modifier = Modifier.horizontalScroll(hScrollState)) {
                Spacer(modifier = Modifier.width(periodLabelWidth))
                for (d in 1..TOTAL_DAYS) {
                    Column {
                        var skipUntil = 0
                        for (p in 1..totalPeriods) {
                            if (p < skipUntil) continue
                            val block = blocks.find { it.slot.day == d && it.slot.startPeriod == p }
                            if (block != null) {
                                val spanCount = block.slot.endPeriod - block.slot.startPeriod + 1
                                skipUntil = p + spanCount
                                val color = blockColors[block] ?: Color(0xFF4FC3F7)

                                Box(
                                    modifier = Modifier
                                        .width(cellWidth)
                                        .height(cellHeight * spanCount)
                                        .padding(1.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(color.copy(alpha = 0.88f))
                                        .clickable { onCourseClick(block.course.id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = block.course.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                            fontSize = if (spanCount >= 2) 10.sp else 8.sp,
                                            lineHeight = if (spanCount >= 2) 12.sp else 10.sp
                                        )
                                        if (!block.slot.location.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(1.dp))
                                            Text(
                                                text = block.slot.location!!,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.8f),
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis,
                                                fontSize = 7.sp,
                                                textAlign = TextAlign.Center,
                                                lineHeight = 9.sp
                                            )
                                        }
                                        if (!block.course.teacher.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(1.dp))
                                            val teacherText = block.course.teacher!!
                                            val displayText = if (teacherText.contains(" ") && teacherText.length > 4) {
                                                teacherText.replaceFirst(" ", "\n")
                                            } else teacherText
                                            Text(
                                                text = displayText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.7f),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                fontSize = 7.sp,
                                                textAlign = TextAlign.Center,
                                                lineHeight = 9.sp
                                            )
                                        }
                                    }
                                }
                            } else {
                                Box(modifier = Modifier.width(cellWidth).height(cellHeight))
                            }
                        }
                    }
                }
            }
        }

        // 2. 左侧时间列
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .verticalScroll(vScrollState)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Spacer(modifier = Modifier.height(headerHeight))
            for (p in 1..totalPeriods) {
                Box(modifier = Modifier.size(periodLabelWidth, cellHeight), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "$p", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(text = getStartTime(p, scheduleSettings), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 7.sp)
                        Text(text = getEndTime(p, scheduleSettings), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 7.sp)
                    }
                }
            }
        }

        // 3. 日 header
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .horizontalScroll(hScrollState)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Box(modifier = Modifier.size(periodLabelWidth, headerHeight))
            dayLabels.forEach { label ->
                Box(modifier = Modifier.width(cellWidth).height(headerHeight), contentAlignment = Alignment.Center) {
                    Text(text = label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
