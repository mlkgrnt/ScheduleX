package com.schedulex.ui.course

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schedulex.ScheduleXApp
import com.schedulex.data.model.Course
import com.schedulex.data.model.TimeSlot
import com.schedulex.data.model.WeekType
import com.schedulex.widget.refreshAllWidgets
import kotlinx.coroutines.launch

private val dayNames = mapOf(
    1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四",
    5 to "周五", 6 to "周六", 7 to "周日"
)

private fun formatScheduleSummary(slots: List<TimeSlot>): String {
    if (slots.isEmpty()) return "无时间安排"
    return slots.joinToString("、") { slot ->
        val day = dayNames[slot.day] ?: "?"
        val periods = if (slot.startPeriod == slot.endPeriod) {
            "第${slot.startPeriod}节"
        } else {
            "第${slot.startPeriod}-${slot.endPeriod}节"
        }
        val typeStr = when (slot.type) {
            WeekType.ODD -> "(单周)"
            WeekType.EVEN -> "(双周)"
            WeekType.ALL -> ""
        }
        "$day$periods$typeStr"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseListScreen(
    onNavigateToEdit: (Long) -> Unit,
    onNavigateToAdd: () -> Unit
) {
    val app = LocalContext.current.applicationContext as ScheduleXApp
    val courses by app.courseRepository.getAllCourses().collectAsState(initial = emptyList())
    val allTimeSlots by app.courseRepository.getAllTimeSlots().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    // Group time slots by courseId
    val slotsByCourse = remember(allTimeSlots) {
        allTimeSlots.groupBy { it.courseId }
    }

    var showDeleteDialog by remember { mutableStateOf<Course?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("课程列表") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToAdd) {
                Icon(Icons.Default.Add, contentDescription = "添加课程")
            }
        }
    ) { innerPadding ->
        if (courses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "📋", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "还没有课程",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "点击右下角 + 添加课程",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(courses, key = { it.id }) { course ->
                    val slots = slotsByCourse[course.id] ?: emptyList()
                    CourseListItem(
                        course = course,
                        slots = slots,
                        onClick = { onNavigateToEdit(course.id) },
                        onDelete = { showDeleteDialog = course }
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { course ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("删除课程") },
            text = { Text("确定要删除「${course.name}」吗？相关的所有时间安排也将被删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            app.courseRepository.deleteCourse(course)
                            refreshAllWidgets(app)
                        }
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun CourseListItem(
    course: Course,
    slots: List<TimeSlot>,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val courseColor = try {
        Color(android.graphics.Color.parseColor(course.color))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color indicator
            Box(
                modifier = Modifier
                    .size(8.dp, 40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(courseColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Course info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (!course.teacher.isNullOrBlank()) {
                    Text(
                        text = course.teacher!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatScheduleSummary(slots),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            // Delete button
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}
