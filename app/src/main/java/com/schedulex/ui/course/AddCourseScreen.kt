package com.schedulex.ui.course

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.schedulex.ScheduleXApp
import com.schedulex.data.model.Course
import com.schedulex.data.model.TimeSlot
import com.schedulex.data.model.WeekType
import com.schedulex.data.repository.PendingSlotInfo
import com.schedulex.ui.components.ColorPicker
import com.schedulex.ui.components.courseColors
import com.schedulex.widget.refreshAllWidgets
import kotlinx.coroutines.launch

private val dayOptions = listOf(
    1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四",
    5 to "周五", 6 to "周六", 7 to "周日"
)

private fun parseWeeksInput(input: String): List<Int> {
    val result = mutableSetOf<Int>()
    input.split(",").forEach { part ->
        val trimmed = part.trim()
        if (trimmed.contains("-")) {
            val range = trimmed.split("-")
            if (range.size == 2) {
                val start = range[0].trim().toIntOrNull()
                val end = range[1].trim().toIntOrNull()
                if (start != null && end != null && start <= end) {
                    result.addAll(start..end)
                }
            }
        } else {
            trimmed.toIntOrNull()?.let { result.add(it) }
        }
    }
    return result.sorted()
}

data class PendingTimeSlot(
    val day: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: List<Int>,
    val weekType: WeekType,
    val location: String
) {
    fun toTimeSlot(courseId: Long): TimeSlot = TimeSlot(
        courseId = courseId,
        day = day,
        startPeriod = startPeriod,
        endPeriod = endPeriod,
        weeks = weeks.toString(),
        type = weekType,
        location = location.ifBlank { null }
    )

    fun displayText(): String {
        val dayName = dayOptions.firstOrNull { it.first == day }?.second ?: "?"
        val periodText = if (startPeriod == endPeriod) "第${startPeriod}节" else "第${startPeriod}-${endPeriod}节"
        val weekText = when (weekType) {
            WeekType.ODD -> "单周"
            WeekType.EVEN -> "双周"
            WeekType.ALL -> ""
        }
        val weeksDisplay = if (weeks.size <= 5) {
            weeks.joinToString(",")
        } else {
            "${weeks.first()}-${weeks.last()}(${weeks.size}周)"
        }
        return "$dayName $periodText | $weeksDisplay$weekText"
    }
}

/**
 * 时间安排卡片：点击编辑，右侧删除按钮
 */
@Composable
fun TimeSlotCard(
    slot: PendingTimeSlot,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth()
    ) {
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
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCourseScreen(
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
    var conflictError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加课程") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
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
                            // 冲突检查
                            for (slot in pendingSlots) {
                                val conflict = app.courseRepository.findConflict(
                                    PendingSlotInfo(slot.day, slot.startPeriod, slot.endPeriod, slot.weeks, slot.weekType)
                                )
                                if (conflict != null) {
                                    val dayName = dayOptions.firstOrNull { it.first == slot.day }?.second ?: "?"
                                    val periodText = if (slot.startPeriod == slot.endPeriod)
                                        "第${slot.startPeriod}节"
                                    else "第${slot.startPeriod}-${slot.endPeriod}节"
                                    conflictError = "$dayName$periodText 与 $conflict 时间冲突"
                                    isSaving = false
                                    return@launch
                                }
                            }

                            val course = Course(
                                name = name.trim(),
                                teacher = teacher.trim().ifBlank { null },
                                color = selectedColor
                            )
                            val timeSlots = pendingSlots.map { it.toTimeSlot(0) }
                            app.courseRepository.insertCourseWithTimeSlots(course, timeSlots)
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
                    Text("保存")
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

/**
 * 添加/编辑时间安排对话框
 * initialSlot 为 null 时是添加模式，非 null 时是编辑模式（预填数据）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTimeSlotDialog(
    initialSlot: PendingTimeSlot? = null,
    onDismiss: () -> Unit,
    onConfirm: (PendingTimeSlot) -> Unit
) {
    var selectedDay by remember { mutableIntStateOf(initialSlot?.day ?: 1) }
    var startPeriod by remember { mutableStateOf(initialSlot?.startPeriod?.toString() ?: "1") }
    var endPeriod by remember { mutableStateOf(initialSlot?.endPeriod?.toString() ?: "2") }
    var weeksInput by remember {
        mutableStateOf(
            initialSlot?.let { slot ->
                if (slot.weeks.size <= 5) slot.weeks.joinToString(",")
                else "${slot.weeks.first()}-${slot.weeks.last()}"
            } ?: "1-16"
        )
    }
    var location by remember { mutableStateOf(initialSlot?.location ?: "") }
    var weekType by remember { mutableStateOf(initialSlot?.weekType ?: WeekType.ALL) }
    var expandedDay by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val isEdit = initialSlot != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "编辑时间安排" else "添加时间安排") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Day selector
                ExposedDropdownMenuBox(
                    expanded = expandedDay,
                    onExpandedChange = { expandedDay = it }
                ) {
                    OutlinedTextField(
                        value = dayOptions.firstOrNull { it.first == selectedDay }?.second ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("星期") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedDay) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedDay,
                        onDismissRequest = { expandedDay = false }
                    ) {
                        dayOptions.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedDay = value
                                    expandedDay = false
                                }
                            )
                        }
                    }
                }

                // Period range
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = startPeriod,
                        onValueChange = { startPeriod = it.filter { c -> c.isDigit() } },
                        label = { Text("开始节") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endPeriod,
                        onValueChange = { endPeriod = it.filter { c -> c.isDigit() } },
                        label = { Text("结束节") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Weeks input
                OutlinedTextField(
                    value = weeksInput,
                    onValueChange = { weeksInput = it },
                    label = { Text("周数") },
                    placeholder = { Text("如: 1-16 或 1,3,5,7") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Week type
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    WeekType.entries.forEach { type ->
                        FilterChip(
                            selected = weekType == type,
                            onClick = { weekType = type },
                            label = {
                                Text(
                                    when (type) {
                                        WeekType.ALL -> "全部"
                                        WeekType.ODD -> "单周"
                                        WeekType.EVEN -> "双周"
                                    }
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Location
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("上课地点 (可选)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Error
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val start = startPeriod.toIntOrNull()
                    val end = endPeriod.toIntOrNull()
                    val weeks = parseWeeksInput(weeksInput)

                    when {
                        start == null || end == null -> error = "请输入有效的节次"
                        start < 1 || end < 1 || start > 20 || end > 20 -> error = "节次范围: 1-20"
                        start > end -> error = "开始节不能大于结束节"
                        weeks.isEmpty() -> error = "请输入有效的周数"
                        else -> {
                            onConfirm(
                                PendingTimeSlot(
                                    day = selectedDay,
                                    startPeriod = start,
                                    endPeriod = end,
                                    weeks = weeks,
                                    weekType = weekType,
                                    location = location
                                )
                            )
                        }
                    }
                }
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
