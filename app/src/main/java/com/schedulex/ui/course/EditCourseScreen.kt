package com.schedulex.ui.course

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.schedulex.ScheduleXApp
import com.schedulex.data.model.Course
import com.schedulex.data.model.TimeSlot
import com.schedulex.data.model.WeekType
import com.schedulex.ui.components.ColorPicker
import com.schedulex.ui.components.courseColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.schedulex.widget.refreshAllWidgets

private fun parseWeeksFromJson(json: String): List<Int> {
    return try {
        json.removeSurrounding("[", "]")
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
    } catch (_: Exception) {
        emptyList()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCourseScreen(
    courseId: Long,
    onNavigateBack: () -> Unit
) {
    val app = LocalContext.current.applicationContext as ScheduleXApp
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(courseColors[0]) }
    var pendingSlots by remember { mutableStateOf(listOf<PendingTimeSlot>()) }
    var showSlotDialog by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var existingCourse by remember { mutableStateOf<Course?>(null) }

    // Load existing course data
    LaunchedEffect(courseId) {
        val course = app.courseRepository.getCourseById(courseId)
        if (course != null) {
            existingCourse = course
            name = course.name
            teacher = course.teacher ?: ""
            selectedColor = course.color.ifBlank { courseColors[0] }

            val slots = app.courseRepository.getTimeSlotsForCourse(courseId).first()
            pendingSlots = slots.map { slot ->
                PendingTimeSlot(
                    day = slot.day,
                    startPeriod = slot.startPeriod,
                    endPeriod = slot.endPeriod,
                    weeks = parseWeeksFromJson(slot.weeks),
                    weekType = slot.type,
                    location = slot.location ?: ""
                )
            }
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑课程") },
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
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Course name
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) nameError = false
                    },
                    label = { Text("课程名称 *") },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text("请输入课程名称") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Teacher
            item {
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("教师") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Color picker
            item {
                Text(
                    text = "课程颜色",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                ColorPicker(
                    selectedColor = selectedColor,
                    onColorSelected = { selectedColor = it }
                )
            }

            // Time slots section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "时间安排",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    FilledTonalButton(
                        onClick = { showSlotDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("添加时间", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (pendingSlots.isEmpty()) {
                item {
                    Text(
                        text = "暂无时间安排，请点击上方按钮添加",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            itemsIndexed(pendingSlots) { index, slot ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = slot.displayText(),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (slot.location.isNotBlank()) {
                                Text(
                                    text = "📍 ${slot.location}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                pendingSlots = pendingSlots.toMutableList().apply { removeAt(index) }
                            }
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Save button
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            nameError = true
                            return@Button
                        }
                        isSaving = true
                        scope.launch {
                            val course = existingCourse?.copy(
                                name = name.trim(),
                                teacher = teacher.trim().ifBlank { null },
                                color = selectedColor
                            ) ?: return@launch

                            app.courseRepository.updateCourse(course)
                            // Replace all time slots
                            app.courseRepository.deleteTimeSlotsForCourse(courseId)
                            val newSlots = pendingSlots.map { it.toTimeSlot(courseId) }
                            app.courseRepository.insertTimeSlots(newSlots)
                            refreshAllWidgets(app)

                            isSaving = false
                            onNavigateBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("保存修改")
                }
            }
        }
    }

    // Time slot dialog
    if (showSlotDialog) {
        AddTimeSlotDialog(
            onDismiss = { showSlotDialog = false },
            onConfirm = { slot ->
                pendingSlots = pendingSlots + slot
                showSlotDialog = false
            }
        )
    }
}
