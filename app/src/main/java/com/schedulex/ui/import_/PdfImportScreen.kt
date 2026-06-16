package com.schedulex.ui.import_

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
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

/**
 * PDF 导入课表
 * 流程：选 PDF → PdfRenderer 逐页转 Bitmap → 视觉 LLM 解析 → 预览确认
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfImportScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPreview: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as ScheduleXApp
    val scope = rememberCoroutineScope()

    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var pageBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        selectedFileName = uri.lastPathSegment ?: "未知文件"
        isProcessing = true
        errorText = null
        pageBitmaps = emptyList()
        statusText = "正在渲染 PDF..."

        scope.launch {
            try {
                // 1. PdfRenderer 渲染每页为 Bitmap
                val bitmaps = withContext(Dispatchers.IO) {
                    renderPdfToBitmaps(context, uri)
                }

                if (bitmaps.isEmpty()) {
                    errorText = "PDF 无法渲染或页数为 0"
                    isProcessing = false
                    return@launch
                }

                pageBitmaps = bitmaps
                statusText = "已渲染 ${bitmaps.size} 页，正在压缩..."

                // 2. 每页压缩为 base64
                val base64List = withContext(Dispatchers.IO) {
                    bitmaps.map { bmp ->
                        val maxDim = 1600
                        val scale = minOf(
                            maxDim.toFloat() / bmp.width,
                            maxDim.toFloat() / bmp.height,
                            1f
                        )
                        val scaled = if (scale < 1f) {
                            Bitmap.createScaledBitmap(
                                bmp,
                                (bmp.width * scale).toInt(),
                                (bmp.height * scale).toInt(),
                                true
                            )
                        } else bmp

                        val out = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                    }
                }

                statusText = "正在加载 LLM 配置..."

                // 3. 获取 LLM 配置
                val prefs = context.llmDataStore.data.first()
                val baseUrl = prefs[LlmKeys.BASE_URL] ?: ""
                val apiKey = prefs[LlmKeys.API_KEY] ?: ""
                val model = prefs[LlmKeys.MODEL] ?: ""

                if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
                    errorText = "请先在 设置 → LLM 配置 中配置 AI 模型"
                    isProcessing = false
                    return@launch
                }

                val llmService = LlmService()
                val allCourses = mutableListOf<com.schedulex.llm.ParsedCourse>()

                // 4. 逐页发给视觉 LLM
                for ((index, base64) in base64List.withIndex()) {
                    statusText = "正在解析第 ${index + 1}/${base64List.size} 页..."

                    val result = llmService.parseScheduleFromImage(
                        imageBase64 = base64,
                        mimeType = "image/jpeg",
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        model = model
                    )

                    result.fold(
                        onSuccess = { courses ->
                            allCourses.addAll(courses)
                        },
                        onFailure = { e ->
                            // 单页失败不中断，继续下一页
                            if (base64List.size == 1) {
                                errorText = "AI 解析失败: ${e.message}"
                                isProcessing = false
                                return@launch
                            }
                        }
                    )
                }

                if (allCourses.isEmpty()) {
                    errorText = "未识别到课程，请确认 PDF 包含课表"
                } else {
                    // 去重（同名+同天+同节次）
                    val unique = allCourses.distinctBy { "${it.name}_${it.day}_${it.startPeriod}_${it.endPeriod}" }
                    app.lastParsedCourses = unique
                    statusText = "解析完成，共 ${unique.size} 条课程"
                    onNavigateToPreview()
                }
                isProcessing = false

            } catch (e: Exception) {
                errorText = "处理失败: ${e.message}"
                isProcessing = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF 导入") },
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 说明卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📋 使用说明",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. 在浏览器中打开课表页面，打印/另存为 .pdf\n" +
                               "2. 点击下方按钮选择 PDF 文件\n" +
                               "3. 系统会将 PDF 渲染为图片，用 AI 识别课表\n" +
                               "4. 识别完成后预览并确认导入\n\n" +
                               "注意：需要在 LLM 配置中设置支持图片的模型",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 选择文件按钮
            Button(
                onClick = {
                    pdfPickerLauncher.launch(arrayOf("application/pdf"))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isProcessing
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("选择 PDF 文件")
            }

            // 已选择文件名
            selectedFileName?.let {
                Text(
                    text = "已选择: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // PDF 页面预览
            if (pageBitmaps.isNotEmpty()) {
                Text(
                    text = "PDF 预览 (${pageBitmaps.size} 页)",
                    style = MaterialTheme.typography.titleSmall
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(pageBitmaps) { index, bmp ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "第 ${index + 1} 页",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "第 ${index + 1} 页",
                                    modifier = Modifier
                                        .width(200.dp)
                                        .heightIn(max = 300.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }
            }

            // 加载状态
            if (isProcessing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(text = statusText, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // 错误信息
            errorText?.let { err ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
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

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 用 PdfRenderer 将 PDF 每页渲染为 Bitmap
 */
private fun renderPdfToBitmaps(context: android.content.Context, uri: Uri): List<Bitmap> {
    val bitmaps = mutableListOf<Bitmap>()
    val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()

    try {
        val renderer = PdfRenderer(pfd)
        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            // 渲染为 ARGB_8888，宽度 1200px 足够清晰
            val targetWidth = 1200
            val scale = targetWidth.toFloat() / page.width
            val targetHeight = (page.height * scale).toInt()

            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val matrix = android.graphics.Matrix().apply {
                postScale(scale, scale)
            }
            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmaps.add(bitmap)
            page.close()
        }
        renderer.close()
    } catch (e: Exception) {
        android.util.Log.e("PdfImport", "PDF render error", e)
    } finally {
        pfd.close()
    }

    return bitmaps
}
