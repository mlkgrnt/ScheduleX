package com.schedulex.ui.import_

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.schedulex.ScheduleXApp
import com.schedulex.llm.LlmService
import com.schedulex.llmDataStore
import com.schedulex.ui.settings.LlmKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotImportScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPreview: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as ScheduleXApp
    val scope = rememberCoroutineScope()

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var parsedCount by remember { mutableIntStateOf(0) }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            selectedImageUri = it
            errorText = null
            parsedCount = 0
            // Decode bitmap
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                statusText = "图片已选择，点击\"开始解析\"进行识别"
            } catch (e: Exception) {
                errorText = "读取图片失败: ${e.message}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("截图导入") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 说明文字
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = "选择一张课表截图，AI 会自动识别其中的课程信息。\n\n支持从相册选择或拍照。\n\n注意：需要在 LLM 配置中设置支持图片的模型（如 GPT-4o、Qwen-VL 等）。",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // 选择图片按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("从相册选择")
                }
            }

            // 图片预览
            bitmap?.let { bmp ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "课表截图",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            // 开始解析按钮
            val currentBitmap = bitmap
            if (currentBitmap != null && !isProcessing) {
                Button(
                    onClick = {
                        scope.launch {
                            isProcessing = true
                            errorText = null
                            statusText = "正在压缩图片..."

                            try {
                                // Compress image to base64
                                val base64 = withContext(Dispatchers.IO) {
                                    val outputStream = ByteArrayOutputStream()
                                    // Scale down if too large
                                    val maxDim = 1600
                                    val scale = minOf(
                                        maxDim.toFloat() / currentBitmap.width,
                                        maxDim.toFloat() / currentBitmap.height,
                                        1f
                                    )
                                    val scaledBitmap = if (scale < 1f) {
                                        Bitmap.createScaledBitmap(
                                            currentBitmap,
                                            (currentBitmap.width * scale).toInt(),
                                            (currentBitmap.height * scale).toInt(),
                                            true
                                        )
                                    } else {
                                        currentBitmap
                                    }
                                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                                    Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                                }

                                statusText = "正在加载 LLM 配置..."

                                val prefs = context.llmDataStore.data.first()
                                val baseUrl = prefs[LlmKeys.BASE_URL] ?: ""
                                val apiKey = prefs[LlmKeys.API_KEY] ?: ""
                                val model = prefs[LlmKeys.MODEL] ?: ""

                                if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
                                    errorText = "请先在 设置 → LLM 配置 中配置 AI 模型"
                                    isProcessing = false
                                    return@launch
                                }

                                statusText = "正在发送到 AI 解析..."

                                val llmService = LlmService()
                                val result = llmService.parseScheduleFromImage(
                                    imageBase64 = base64,
                                    mimeType = "image/jpeg",
                                    baseUrl = baseUrl,
                                    apiKey = apiKey,
                                    model = model
                                )

                                result.fold(
                                    onSuccess = { courses ->
                                        if (courses.isEmpty()) {
                                            errorText = "未识别到课程，请确认图片是课表截图"
                                        } else {
                                            app.lastParsedCourses = courses
                                            parsedCount = courses.size
                                            statusText = "识别完成！共 ${courses.size} 条课程"
                                            onNavigateToPreview()
                                        }
                                    },
                                    onFailure = { e ->
                                        errorText = "识别失败: ${e.message}"
                                    }
                                )
                            } catch (e: Exception) {
                                errorText = "处理异常: ${e.message}"
                            } finally {
                                isProcessing = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("开始解析")
                }
            }

            // 状态/错误信息
            if (isProcessing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(statusText)
                }
            }

            errorText?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = err,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (statusText.isNotEmpty() && !isProcessing && errorText == null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
