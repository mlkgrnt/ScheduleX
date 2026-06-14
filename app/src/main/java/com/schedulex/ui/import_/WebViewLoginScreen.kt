package com.schedulex.ui.import_

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.schedulex.ScheduleXApp
import com.schedulex.llm.HtmlExtractor
import com.schedulex.llm.ScheduleHtmlCleaner
import com.schedulex.llm.WebViewCookieExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewLoginScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPreview: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as ScheduleXApp
    val viewModel = remember { ImportViewModel(app.courseRepository, app.llmService, context) }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isExtracting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var dialogUrl by remember { mutableStateOf("") }
    var pendingUrl by remember { mutableStateOf<String?>(null) }
    // Track the academic system domain (not SSO domain)
    var academicDomain by remember { mutableStateOf<String?>(null) }
    // Prevent redirect loops
    var hasAttemptedScheduleRedirect by remember { mutableStateOf(false) }

    // Auto-load: empty by default, user enters their own URL
    LaunchedEffect(webView) {
        val wv = webView
        if (wv != null && currentUrl.isBlank()) {
            // Start with empty page, user enters their academic system URL
        }
    }

    // Load pending URL when WebView is ready
    LaunchedEffect(pendingUrl, webView) {
        val url = pendingUrl
        val wv = webView
        if (url != null && wv != null) {
            pendingUrl = null
            // Normalize URL for domain extraction
            val normalizedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
            try {
                val uri = java.net.URI(normalizedUrl)
                val host = uri.host ?: ""
                android.util.Log.d("WebViewLogin", "URL: $url, normalized: $normalizedUrl, host: $host")
                // If it's a CAS/SSO URL, extract the service parameter to get academic domain
                if (host.contains("sso.") || host.contains("cas.") || normalizedUrl.contains("/login")) {
                    val serviceMatch = Regex("[?&]service=([^&]+)").find(normalizedUrl)
                    if (serviceMatch != null) {
                        val serviceUrl = java.net.URLDecoder.decode(serviceMatch.groupValues[1], "UTF-8")
                        val serviceHost = try { java.net.URI(serviceUrl).host } catch (_: Exception) { null }
                        if (serviceHost != null) {
                            academicDomain = serviceHost
                            android.util.Log.d("WebViewLogin", "Extracted academic domain from CAS service: $academicDomain")
                        }
                    }
                } else if (host.isNotBlank()) {
                    academicDomain = host
                    android.util.Log.d("WebViewLogin", "Set academic domain: $academicDomain")
                }
            } catch (e: Exception) {
                android.util.Log.e("WebViewLogin", "Failed to parse URL: $url", e)
            }
            hasAttemptedScheduleRedirect = false
            navigateTo(url, wv)
        }
    }

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.courses) {
        if (uiState.courses.isNotEmpty()) {
            onNavigateToPreview()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            errorMessage = it
            isExtracting = false
        }
    }

    // URL input dialog
    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("输入教务系统网址") },
            text = {
                OutlinedTextField(
                    value = dialogUrl,
                    onValueChange = { dialogUrl = it },
                    label = { Text("网址") },
                    placeholder = { Text("jw.example.edu.cn") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            pendingUrl = dialogUrl
                            showUrlDialog = false
                            keyboardController?.hide()
                        }
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingUrl = dialogUrl
                    showUrlDialog = false
                    keyboardController?.hide()
                }) {
                    Text("前往")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextButton(
                        onClick = {
                            dialogUrl = currentUrl.ifBlank { "" }
                            showUrlDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (currentUrl.isNotBlank()) currentUrl else "点击输入网址",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        bottomBar = {
            // Bottom action bar — fixed at bottom via Scaffold
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (currentUrl.isBlank()) "请输入网址并登录"
                        else "登录后找到课表页面，点击提取",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            isExtracting = true
                            errorMessage = null
                            android.util.Log.d("HtmlExtract", "Starting extraction from: $currentUrl")

                            // 先用JS提取干净的课表数据（去掉导航、脚本等无关内容）
                            android.util.Log.d("HtmlExtract", "Extracting clean schedule data with JS...")
                            webView?.evaluateJavascript(ScheduleHtmlCleaner.CLEAN_EXTRACT_JS) { jsResult ->
                                android.util.Log.d("HtmlExtract", "JS clean result: ${jsResult?.take(500)}")
                                
                                if (jsResult.isNullOrBlank() || jsResult == "null" || jsResult == "\"null\"") {
                                    // JS提取失败，回退到HTTP提取
                                    android.util.Log.d("HtmlExtract", "JS failed, trying HTTP extraction")
                                    scope.launch {
                                        val html = withContext(Dispatchers.IO) {
                                            WebViewCookieExtractor.fetchWithCookies(currentUrl)
                                        }
                                        if (html != null) {
                                            viewModel.parseWithLlm(html)
                                        } else {
                                            errorMessage = "未能获取课表内容，请确认已登录并在课表页面"
                                            isExtracting = false
                                        }
                                    }
                                    return@evaluateJavascript
                                }
                                
                                // JS提取成功，发送干净数据给LLM
                                val cleanData = jsResult.removeSurrounding("\"")
                                    .replace("\\u003C", "<")
                                    .replace("\\\"", "\"")
                                    .replace("\\n", "\n")
                                    .replace("\\/", "/")
                                
                                android.util.Log.d("HtmlExtract", "Clean data size: ${cleanData.length} chars")
                                scope.launch { viewModel.parseJsOutput(cleanData) }
                            }
                        },
                        enabled = !isExtracting && !isLoading && currentUrl.isNotBlank()
                    ) {
                        Text("提取课表")
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        settings.allowContentAccess = true
                        settings.allowFileAccess = true
                        settings.databaseEnabled = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.blockNetworkImage = false
                        settings.blockNetworkLoads = false

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                currentUrl = url ?: ""
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                currentUrl = url ?: ""

                                // Auto-redirect to schedule page after login
                                if (url != null && academicDomain != null) {
                                    val currentHost = try { java.net.URI(url).host } catch (_: Exception) { null }
                                    
                                    val isSSOPage = url.contains("sso.") || url.contains("cas.") || 
                                                    url.contains("/login") || url.contains("/sso/")
                                    
                                    val isScheduleUrl = url.contains("xskb_list") || url.contains("kblist") || 
                                                        url.contains("kbcx_list") || url.contains("xkjg_list")
                                    
                                    val isOnAcademicSystem = currentHost != null && 
                                        (currentHost == academicDomain || currentHost.endsWith(".$academicDomain"))

                                    // 已经在课表页，标记成功
                                    if (isOnAcademicSystem && isScheduleUrl) {
                                        hasAttemptedScheduleRedirect = true
                                    }

                                    // 在教务主页且还没成功跳转到课表页 → 尝试跳转
                                    if (isOnAcademicSystem && !isSSOPage && !isScheduleUrl && !hasAttemptedScheduleRedirect) {
                                        // Smart detection: find schedule link in page
                                        val findScheduleJs = """
                                            (function() {
                                                var links = document.querySelectorAll('a[href]');
                                                var textKeywords = ['课表', '课程表', '我的课表', '学生课表'];
                                                var hrefKeywords = ['xskb', 'kblist', 'kbcx_list', 'xkjg_list'];
                                                
                                                for (var i = 0; i < links.length; i++) {
                                                    var href = links[i].href || '';
                                                    var text = (links[i].textContent || '').trim();
                                                    for (var j = 0; j < textKeywords.length; j++) {
                                                        if (text.indexOf(textKeywords[j]) >= 0 && href.indexOf('http') >= 0) {
                                                            return href;
                                                        }
                                                    }
                                                    for (var j = 0; j < hrefKeywords.length; j++) {
                                                        if (href.toLowerCase().indexOf(hrefKeywords[j]) >= 0) {
                                                            return href;
                                                        }
                                                    }
                                                }
                                                
                                                var path = window.location.pathname;
                                                var host = window.location.host;
                                                
                                                if (path.indexOf('/jsxsd') >= 0) {
                                                    return 'http://' + host + '/jsxsd/xskb/xskb_list.do';
                                                }
                                                if (path.indexOf('/jwglxt') >= 0) {
                                                    return 'http://' + host + '/jwglxt/kbcx/kbcx_list.do';
                                                }
                                                if (path.indexOf('/student') >= 0) {
                                                    return 'http://' + host + '/student/xkjg/xkjg_list.do';
                                                }
                                                if (path.indexOf('/jwgl') >= 0 || path.indexOf('/jwzhgl') >= 0) {
                                                    return 'http://' + host + '/jsxsd/xskb/xskb_list.do';
                                                }
                                                
                                                return null;
                                            })()
                                        """.trimIndent()

                                        // 延迟500ms等页面渲染完再执行JS
                                        view?.postDelayed({
                                            view.evaluateJavascript(findScheduleJs) { result ->
                                                val scheduleUrl = result?.removeSurrounding("\"")?.takeIf { it != "null" && it.isNotBlank() }
                                                if (scheduleUrl != null) {
                                                    view.postDelayed({ 
                                                        navigateTo(scheduleUrl, view)
                                                        view.postDelayed({
                                                            view.stopLoading()
                                                            isLoading = false
                                                        }, 3000)
                                                    }, 300)
                                                } else {
                                                    // Fallback: try common paths
                                                    val host = academicDomain
                                                    val fallbackPaths = listOf(
                                                        "http://$host/jsxsd/xskb/xskb_list.do",
                                                        "http://$host/jwglxt/kbcx/kbcx_list.do",
                                                        "http://$host/student/xkjg/xkjg_list.do"
                                                    )
                                                    view.postDelayed({ 
                                                        navigateTo(fallbackPaths.first(), view)
                                                        view.postDelayed({
                                                            view.stopLoading()
                                                            isLoading = false
                                                        }, 3000)
                                                    }, 300)
                                                }
                                            }
                                        }, 500)
                                    }
                                }
                            }
                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                if (request?.isForMainFrame == true) {
                                    errorMessage = "页面加载失败: ${error?.description}"
                                }
                            }
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                return false
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {}
                        }

                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { wv ->
                    wv.layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
            )

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }

            if (isExtracting) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text("正在解析课表...")
                    }
                }
            }
        }
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("提示") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("确定")
                }
            }
        )
    }
}

private fun navigateTo(input: String, webView: WebView?) {
    if (input.isBlank()) return
    var url = input.trim()
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
        url = "https://$url"
    }
    webView?.loadUrl(url)
}
