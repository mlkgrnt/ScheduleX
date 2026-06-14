package com.schedulex.llm

import android.webkit.CookieManager
import android.webkit.WebView
import java.net.HttpURLConnection
import java.net.URL

/**
 * Extracts schedule HTML by making a direct HTTP request with WebView cookies.
 * This bypasses iframe/cross-origin issues by using native HTTP instead of JS.
 */
object WebViewCookieExtractor {

    // 强智教务系统课表页面路径
    private val ZHENGZHI_SCHEDULE_PATHS = listOf(
        "/jsxsd/xskb/xskb_list.do",
        "/jsxsd/xskb/list.do",
        "/jsxsd/kbcx/kbcx_list.do",
        "/jsxsd/kbcx/kbcx_list_xs.do",
    )

    // 判断URL是否是框架页面（不含实际课表数据）
    private fun isFramePage(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("/jsxsd/framework") ||
                lower.contains("/jsxsd/fra") ||
                (lower.contains("/jsxsd/") && !lower.contains("xskb") && !lower.contains("kbcx") && !lower.contains("kblist"))
    }

    // 从URL提取base URL（scheme + host）
    private fun getBaseUrl(url: String): String {
        val match = Regex("^(https?://[^/]+)").find(url)
        return match?.groupValues?.get(1) ?: ""
    }

    /**
     * Fetch the HTML at [url] using cookies from the WebView's CookieManager.
     * Returns the response body as a string, or null on failure.
     *
     * 如果当前URL是框架页面，自动尝试获取实际的课表页面。
     */
    fun fetchWithCookies(url: String): String? {
        return try {
            val cookieManager = CookieManager.getInstance()
            val cookies = cookieManager.getCookie(url) ?: ""

            android.util.Log.d("CookieExtract", "Fetching: $url")
            android.util.Log.d("CookieExtract", "Cookies: ${cookies.take(200)}")

            // 如果是框架页，自动尝试课表页面
            if (isFramePage(url)) {
                android.util.Log.d("CookieExtract", "Detected frame page, trying schedule URLs...")
                val base = getBaseUrl(url)
                for (path in ZHENGZHI_SCHEDULE_PATHS) {
                    val scheduleUrl = "$base$path"
                    android.util.Log.d("CookieExtract", "Trying: $scheduleUrl")
                    val result = httpGet(scheduleUrl, cookies)
                    if (result != null && result.length > 5000 &&
                        (result.contains("kbcontent") || result.contains("kbtable") || result.contains("课程") || result.contains("节"))
                    ) {
                        android.util.Log.d("CookieExtract", "Got schedule from $path: ${result.length} chars")
                        return result
                    }
                }
                android.util.Log.d("CookieExtract", "No schedule page found via frame redirect")
            }

            // 直接获取当前URL
            val body = httpGet(url, cookies)
            if (body != null) body
            else {
                android.util.Log.e("CookieExtract", "Direct fetch failed")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("CookieExtract", "Failed: ${e.message}", e)
            null
        }
    }

    private fun httpGet(url: String, cookies: String): String? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                setRequestProperty("Cookie", cookies)
                setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                instanceFollowRedirects = true
                connectTimeout = 15000
                readTimeout = 15000
            }

            val responseCode = connection.responseCode
            val finalUrl = connection.url.toString()
            android.util.Log.d("CookieExtract", "HTTP $responseCode from $url -> $finalUrl")

            if (responseCode == 200) {
                val body = connection.inputStream.bufferedReader().readText()
                android.util.Log.d("CookieExtract", "Body length: ${body.length}")
                // Sync any new cookies back to CookieManager
                try {
                    val cookieManager = CookieManager.getInstance()
                    connection.headerFields["Set-Cookie"]?.forEach { cookie ->
                        cookieManager.setCookie(url, cookie.split(";").first().trim())
                    }
                } catch (_: Exception) {}
                body
            } else {
                android.util.Log.e("CookieExtract", "HTTP $responseCode for $url")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("CookieExtract", "httpGet failed for $url: ${e.message}")
            null
        }
    }
}
