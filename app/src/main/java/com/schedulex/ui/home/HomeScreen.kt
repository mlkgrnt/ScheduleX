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
import com.schedulex.data.model.Course
import com.schedulex.data.model.ScheduleSettings
import com.schedulex.data.model.TimeSlot
import com.schedulex.data.model.WeekType
import com.schedulex.data.model.scheduleDataStore
import com.schedulex.data.model.loadScheduleSettings
import com.schedulex.data.model.calculateActualWeek
import com.schedulex.data.model.getStartTime
import com.schedulex.data.model.getEndTime
import kotlinx.coroutines.flow.first

private val dayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

private fun parseWeeks(weeksJson: String): List<Int> {
    return try {
        weeksJson.removeSurrounding("[", "]")
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
    } catch (_: Exception) {
        emptyList()
    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAddCourse: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToScreenshotImport: () -> Unit = {},
    onNavigateToEditCourse: ((Long) -> Unit)? = null
) {
    val app = LocalContext.current.applicationContext as ScheduleXApp
    val context = LocalContext.current
    val courses by app.courseRepository.getAllCourses().collectAsState(initial = emptyList())
    val allTimeSlots by app.courseRepository.getAllTimeSlots().collectAsState(initial = emptyList())

    var currentWeek by remember { mutableIntStateOf(1) }
    var showWeekSelector by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    
    // Load schedule settings
    var scheduleSettings by remember { mutableStateOf(ScheduleSettings()) }
    LaunchedEffect(Unit) {
        scheduleSettings = loadScheduleSettings(context.scheduleDataStore)
        currentWeek = calculateActualWeek(scheduleSettings)
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ScheduleX") },
                actions = {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "导入")
                    }
                    IconButton(onClick = onNavigateToAddCourse) {
                        Icon(Icons.Default.Add, contentDescription = "添加课程")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Week selector - fixed at top
            WeekSelector(
                currentWeek = currentWeek,
                onWeekChange = { currentWeek = it },
                onShowWeekPicker = { showWeekSelector = true }
            )

            if (courses.isEmpty()) {
                EmptyScheduleView(onNavigateToAddCourse)
            } else {
                // Schedule grid - scrollable both directions
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    ScheduleGrid(
                        blocks = slotBlocks,
                        scheduleSettings = scheduleSettings,
                        onCourseClick = { courseId ->
                            onNavigateToEditCourse?.invoke(courseId)
                        }
                    )
                }
            }
        }
    }
    
    // Week picker dialog
    if (showWeekSelector) {
        WeekPickerDialog(
            currentWeek = currentWeek,
            onWeekSelected = { 
                currentWeek = it
                showWeekSelector = false
            },
            onDismiss = { showWeekSelector = false }
        )
    }
    
    // Import choice dialog
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = {
                            showImportDialog = false
                            onNavigateToImport()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("教务系统导入")
                    }
                    TextButton(
                        onClick = {
                            showImportDialog = false
                            onNavigateToScreenshotImport()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("截图导入")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("取消")
                }
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { if (currentWeek > 1) onWeekChange(currentWeek - 1) },
            enabled = currentWeek > 1
        ) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "上一周")
        }
        Text(
            text = "第 $currentWeek 周",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .clickable { onShowWeekPicker() }
        )
        IconButton(onClick = { onWeekChange(currentWeek + 1) }) {
            Icon(Icons.Default.ChevronRight, contentDescription = "下一周")
        }
    }
}

@Composable
private fun WeekPickerDialog(
    currentWeek: Int,
    onWeekSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择周次") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                for (week in 1..30) {
                    ListItem(
                        headlineContent = { 
                            Text("第 $week 周") 
                        },
                        leadingContent = {
                            if (week == currentWeek) {
                                Icon(Icons.Default.ChevronRight, contentDescription = null)
                            }
                        },
                        modifier = Modifier.clickable { onWeekSelected(week) },
                        colors = if (week == currentWeek) {
                            ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        } else {
                            ListItemDefaults.colors()
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun EmptyScheduleView(onAdd: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "📚", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "还没有课程",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            FilledTonalButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加课程")
            }
        }
    }
}

data class CourseBlock(
    val course: Course,
    val slot: TimeSlot
)

private const val TOTAL_DAYS = 7

@Composable
private fun ScheduleGrid(
    blocks: List<CourseBlock>,
    scheduleSettings: ScheduleSettings,
    onCourseClick: (Long) -> Unit
) {
    val cellWidth = 56.dp
    val periodLabelWidth = 56.dp
    val headerHeight = 32.dp
    val cellHeight = 60.dp
    val totalPeriods = scheduleSettings.totalPeriods

    // Resolve course colors
    val blockColors = remember(blocks) {
        blocks.associate { block ->
            val color = try {
                Color(android.graphics.Color.parseColor(block.course.color))
            } catch (_: Exception) {
                Color(0xFF4FC3F7)
            }
            block to color
        }
    }

    val hScrollState = rememberScrollState()

    Column {
        // Day header row - fixed at top
        Row(modifier = Modifier.horizontalScroll(hScrollState)) {
            // Corner cell
            Box(
                modifier = Modifier.size(periodLabelWidth, headerHeight),
                contentAlignment = Alignment.Center
            ) {
                Text("节", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
            dayLabels.forEach { label ->
                Box(
                    modifier = Modifier.width(cellWidth).height(headerHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Grid body
        Row(modifier = Modifier.horizontalScroll(hScrollState)) {
            // Period labels column
            Column {
                for (p in 1..totalPeriods) {
                    Box(
                        modifier = Modifier.size(periodLabelWidth, cellHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$p",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = getStartTime(p, scheduleSettings),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 7.sp
                            )
                            Text(
                                text = getEndTime(p, scheduleSettings),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                fontSize = 7.sp
                            )
                        }
                    }
                }
            }
            
            // Grid cells with course blocks
            for (d in 1..TOTAL_DAYS) {
                Column {
                    var skipUntil = 0  // 跳过被多节课程块覆盖的空行
                    for (p in 1..totalPeriods) {
                        if (p < skipUntil) continue  // 被前面的课程块覆盖，跳过
                        val block = blocks.find { it.slot.day == d && it.slot.startPeriod == p }
                        if (block != null) {
                            val spanCount = block.slot.endPeriod - block.slot.startPeriod + 1
                            skipUntil = p + spanCount  // 标记后续需要跳过的节
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
                                        maxLines = 2,
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
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 7.sp,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 9.sp
                                        )
                                    }
                                    if (!block.course.teacher.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(1.dp))
                                        Text(
                                            text = block.course.teacher!!,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 7.sp,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 9.sp
                                        )
                                    }
                                }
                            }
                        } else {
                            // Empty cell
                            Box(
                                modifier = Modifier
                                    .size(cellWidth, cellHeight)
                                    .padding(0.5.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}
