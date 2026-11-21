package com.zhiwei.store.core

import org.json.JSONArray
import org.json.JSONObject

/** org.json 安全取值扩展，避免到处 try/catch 与 null 判断 */
fun jsonObject(text: String?): JSONObject? = runCatching { JSONObject(text ?: "") }.getOrNull()

fun jsonArray(text: String?): JSONArray? = runCatching { JSONArray(text ?: "") }.getOrNull()

fun JSONObject?.s(vararg keys: String): String? {
    if (this == null) return null
    for (k in keys) {
        val v = opt(k)
        if (v != null && v != JSONObject.NULL) {
            val str = v.toString()
            if (str.isNotBlank() && str != "null") return str
        }
    }
    return null
}

fun JSONObject?.l(vararg keys: String): Long {
    if (this == null) return 0L
    for (k in keys) {
        when (val v = opt(k)) {
            is Number -> return v.toLong()
            is String -> v.toLongOrNull()?.let { return it }
        }
    }
    return 0L
}

fun JSONObject?.i(vararg keys: String): Int = l(*keys).toInt()

fun JSONObject?.d(vararg keys: String): Double {
    if (this == null) return 0.0
    for (k in keys) {
        when (val v = opt(k)) {
            is Number -> return v.toDouble()
            is String -> v.toDoubleOrNull()?.let { return it }
        }
    }
    return 0.0
}

fun JSONObject?.o(vararg keys: String): JSONObject? {
    if (this == null) return null
    for (k in keys) {
        val v = opt(k)
        if (v is JSONObject) return v
    }
    return null
}

fun JSONObject?.a(vararg keys: String): JSONArray? {
    if (this == null) return null
    for (k in keys) {
        val v = opt(k)
        if (v is JSONArray) return v
    }
    return null
}

fun JSONArray?.o(index: Int): JSONObject? {
    if (this == null || index < 0 || index >= length()) return null
    return opt(index) as? JSONObject
}

fun JSONArray?.strList(): List<String> {
    if (this == null) return emptyList()
    val out = ArrayList<String>(length())
    for (i in 0 until length()) {
        val v = opt(i)
        if (v != null && v != JSONObject.NULL) out.add(v.toString())
    }
    return out
}

/** 深度遍历查找指定 key 的第一个 JSONArray（第三方接口结构不稳定时兜底） */
fun findArrayDeep(root: JSONObject?, key: String, depth: Int = 4): JSONArray? {
    if (root == null || depth < 0) return null
    (root.opt(key) as? JSONArray)?.let { return it }
    val keys = root.keys()
    while (keys.hasNext()) {
        when (val v = root.opt(keys.next())) {
            is JSONObject -> findArrayDeep(v, key, depth - 1)?.let { return it }
            is JSONArray -> for (i in 0 until v.length()) {
                val e = v.opt(i)
                if (e is JSONObject) findArrayDeep(e, key, depth - 1)?.let { return it }
            }
        }
    }
    return null
}

/** 去掉 HTML 标签与实体，用于把索引里的富文本简介转纯文本 */
fun stripHtml(input: String?): String {
    if (input.isNullOrBlank()) return ""
    var s = input
        .replace(Regex("(?s)<(script|style)[^>]*>.*?</\\1>"), " ")
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
    val entities = mapOf(
        "&nbsp;" to " ", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"",
        "&#39;" to "'", "&apos;" to "'", "&hellip;" to "…", "&mdash;" to "—", "&ndash;" to "–"
    )
    entities.forEach { (k, v) -> s = s.replace(k, v) }
    s = s.replace(Regex("&#(\\d+);")) { m ->
        val code = m.groupValues[1].toIntOrNull() ?: 0
        if (code > 0) String(Character.toChars(code)) else ""
    }
    return s.replace(Regex("\n{3,}"), "\n\n").trim()
}

fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "—"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / 1048576.0)
    else -> String.format("%.2f GB", bytes / 1073741824.0)
}

fun formatDate(epochMs: Long): String {
    if (epochMs <= 0) return "—"
    return runCatching {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
        fmt.format(java.util.Date(epochMs))
    }.getOrDefault("—")
}

/** 相对时间：3 天前 / 2 小时前 */
fun relativeTime(epochMs: Long): String {
    if (epochMs <= 0) return "—"
    val diff = System.currentTimeMillis() - epochMs
    if (diff < 0) return formatDate(epochMs)
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        diff < minute -> "刚刚"
        diff < hour -> "${diff / minute} 分钟前"
        diff < day -> "${diff / hour} 小时前"
        diff < 30 * day -> "${diff / day} 天前"
        diff < 365 * day -> "${diff / (30 * day)} 个月前"
        else -> formatDate(epochMs)
    }
}