package com.zhiwei.store.core

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * 轻量 HTTP 客户端：不引入 OkHttp，减少构建依赖与体积。
 * 支持 gzip、自定义 UA、超时、POST 表单/JSON、Range 续传。
 */
object Http {

    const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"

    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Safari/537.36"

    class Response(
        val code: Int,
        val body: String,
        val headers: Map<String, List<String>> = emptyMap(),
        val url: String = ""
    ) {
        val ok: Boolean get() = code in 200..299
    }

    fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 20000,
        ua: String = MOBILE_UA
    ): Response = request(url, "GET", headers, null, timeoutMs, ua)

    fun getBytes(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 30000,
        ua: String = MOBILE_UA,
        rangeStart: Long = -1
    ): Pair<Int, ByteArray> {
        val conn = open(url, headers, timeoutMs, "GET", null, ua)
        if (rangeStart >= 0) conn.setRequestProperty("Range", "bytes=$rangeStart-")
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
        conn.disconnect()
        return code to bytes
    }

    fun postJson(
        url: String,
        json: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 20000,
        ua: String = MOBILE_UA
    ): Response = request(
        url, "POST",
        headers + mapOf("Content-Type" to "application/json; charset=utf-8"),
        json.toByteArray(Charsets.UTF_8), timeoutMs, ua
    )

    fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 20000,
        ua: String = MOBILE_UA
    ): Response {
        val body = form.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
            .toByteArray(Charsets.UTF_8)
        return request(
            url, "POST",
            headers + mapOf("Content-Type" to "application/x-www-form-urlencoded; charset=utf-8"),
            body, timeoutMs, ua
        )
    }

    private fun enc(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")

    private fun request(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: ByteArray?,
        timeoutMs: Int,
        ua: String
    ): Response {
        val conn = open(url, headers, timeoutMs, method, body, ua)
        val code = try {
            conn.responseCode
        } catch (e: Exception) {
            conn.disconnect()
            throw e
        }
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.use { readText(it, conn.contentEncoding) } ?: ""
        val hs = HashMap<String, List<String>>()
        conn.headerFields?.forEach { (k, v) -> if (k != null) hs[k] = v }
        val finalUrl = conn.url?.toString() ?: url
        conn.disconnect()
        return Response(code, text, hs, finalUrl)
    }

    private fun open(
        url: String,
        headers: Map<String, String>,
        timeoutMs: Int,
        method: String,
        body: ByteArray?,
        ua: String
    ): HttpURLConnection {
        val conn = (URL(url).openConnection() as HttpURLConnection)
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.instanceFollowRedirects = true
        conn.requestMethod = method
        conn.setRequestProperty("User-Agent", ua)
        conn.setRequestProperty("Accept-Encoding", "gzip")
        conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        headers.forEach { (k, v) ->
            runCatching { conn.setRequestProperty(k, v) }
        }
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Length", body.size.toString())
            conn.outputStream.use { it.write(body) }
        }
        return conn
    }

    private fun readText(input: InputStream, encoding: String?): String {
        val raw = input.readBytes()
        val bytes = if (encoding?.contains("gzip", true) == true) {
            runCatching { GZIPInputStream(raw.inputStream()).readBytes() }.getOrDefault(raw)
        } else raw
        return String(bytes, Charsets.UTF_8)
    }

    /** 流式下载：边读边写，返回最终字节数，用于断点续传与进度回调 */
    fun download(
        url: String,
        out: java.io.OutputStream,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 30000,
        ua: String = MOBILE_UA,
        onProgress: (downloaded: Long, total: Long) -> Boolean
    ): Long {
        val conn = open(url, headers, timeoutMs, "GET", null, ua)
        if (!headers.keys.any { it.equals("Range", true) }) {
            conn.setRequestProperty("Accept-Encoding", "identity")
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw IllegalStateException("HTTP $code")
        }
        val total = conn.contentLengthLong
        var done = 0L
        conn.inputStream.use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                out.write(buf, 0, n)
                done += n
                if (!onProgress(done, total)) break
            }
        }
        out.flush()
        conn.disconnect()
        return done
    }

    /** 只取响应头，判断源是否可达 */
    fun ping(url: String, timeoutMs: Int = 8000, ua: String = MOBILE_UA): Int {
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection)
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.requestMethod = "HEAD"
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", ua)
            val code = conn.responseCode
            conn.disconnect()
            code
        }.getOrDefault(-1)
    }

    fun bytesOf(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        input.copyTo(out, 32 * 1024)
        return out.toByteArray()
    }
}