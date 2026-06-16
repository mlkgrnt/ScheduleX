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
import com.schedulex.data.repository.PendingSlotInfo
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
    var editingSlotIndex by remember { mutableStateOf<Int?>(null) }
    var nameError by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var existingCourse by remember { mutableStateOf<Course?>(null) }
    var conflictError by remember { mutableStateOf<String?>(null) }

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
                        onClick = {
                            editingSlotIndex = null
                            showSlotDialog = true
                        },
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
                TimeSlotCard(
                    slot = slot,
                    onEdit = {
                        editingSlotIndex = index
                        showSlotDialog = true
                    },
                    onDelete = {
                        pendingSlots = pendingSlots.toMutableList().apply { removeAt(index) }
                    }
                )
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
                            // 冲突检查（排除当前课程自身）
                            for (slot in pendingSlots) {
                                val conflict = app.courseRepository.findConflict(
                                    PendingSlotInfo(slot.day, slot.startPeriod, slot.endPeriod, slot.weeks, slot.weekType),
                                    excludeCourseId = courseId,
                                    scheduleId = existingCourse?.scheduleId ?: "default"
                                )
                                if (conflict != null) {
                                    val dayName = listOf(1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四",
                                        5 to "周五", 6 to "周六", 7 to "周日").firstOrNull { it.first == slot.day }?.second ?: "?"
                                    val periodText = if (slot.startPeriod == slot.endPeriod)
                                        "第${slot.startPeriod}节"
                                    else "第${slot.startPeriod}-${slot.endPeriod}节"
                                    conflictError = "$dayName$periodText 与 $conflict 时间冲突"
                                    isSaving = false
                                    return@launch
                                }
                            }

                            val course = existingCourse?.copy(
                                name = name.trim(),
                                teacher = teacher.trim().ifBlank { null },
                                color = selectedColor
                            ) ?: return@launch

                            app.courseRepository.updateCourse(course)
                            // Replace all time slots
                            app.courseRepository.deleteTimeSlotsForCourse(courseId)
                            val newSlots = pendingSlots.map { it.toTimeSlot(courseId, course.scheduleId) }
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

    // Time slot dialog（添加 or 编辑）
    if (showSlotDialog) {
        AddTimeSlotDialog(
            initialSlot = editingSlotIndex?.let { pendingSlots.getOrNull(it) },
            onDismiss = {
                showSlotDialog = false
                editingSlotIndex = null
            },
            onConfirm = { slot ->
                val idx = editingSlotIndex
                if (idx != null) {
                    // 编辑模式：替换对应位置
                    pendingSlots = pendingSlots.toMutableList().apply { set(idx, slot) }
                } else {
                    // 添加模式
                    pendingSlots = pendingSlots + slot
                }
                showSlotDialog = false
                editingSlotIndex = null
            }
        )
    }

    // 冲突错误弹窗
    conflictError?.let { msg ->
        AlertDialog(
            onDismissRequest = { conflictError = null },
            title = { Text("时间冲突") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { conflictError = null }) {
                    Text("确定")
                }
            }
        )
    }
}
