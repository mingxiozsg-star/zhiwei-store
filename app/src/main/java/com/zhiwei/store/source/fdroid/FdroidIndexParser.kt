package com.zhiwei.store.source.fdroid

import com.zhiwei.store.core.JsonStreamReader
import com.zhiwei.store.core.stripHtml
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * F-Droid / IzzyOnDroid 通用索引解析器（index-v1.json）。
 * 结构：{ "repo": {...}, "packages": { "<包名>": [ {版本...}, ... ] }, "apps": [...] }
 */
object FdroidIndexParser {

    class Entry {
        var packageName: String = ""
        var name: String = ""
        var summary: String = ""
        var description: String = ""
        var icon: String = ""
        var versionName: String = ""
        var versionCode: Long = 0
        var apkName: String = ""
        var hash: String = ""
        var size: Long = 0
        var lastUpdated: Long = 0
        var added: Long = 0
        var license: String = ""
        var sourceCode: String = ""
        var website: String = ""
        var issueTracker: String = ""
        var authorName: String = ""
        var categories: List<String> = emptyList()
        var antiFeatures: List<String> = emptyList()
        var nameZh: String? = null
        var summaryZh: String? = null

        fun displayName(): String = nameZh?.takeIf { it.isNotBlank() } ?: name
        fun displaySummary(): String = summaryZh?.takeIf { it.isNotBlank() } ?: summary
    }

    /** 读取索引流，回调每个包的最新版本 */
    fun parse(input: InputStream, onApp: (Entry) -> Unit) {
        val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8), 1 shl 16)
        val j = JsonStreamReader(reader)
        j.beginObject()
        while (true) {
            j.optionalComma()
            if (!j.hasNextEntry()) break
            val key = j.readName()
            if (key == "packages") {
                parsePackages(j, onApp)
            } else {
                j.skipValue()
            }
        }
        j.endObject()
        runCatching { reader.close() }
    }

    private fun parsePackages(j: JsonStreamReader, onApp: (Entry) -> Unit) {
        j.beginObject()
        while (true) {
            j.optionalComma()
            if (!j.hasNextEntry()) break
            val pkg = j.readName()
            val versions = ArrayList<Entry>(3)
            j.beginArray()
            while (true) {
                j.optionalComma()
                if (!j.hasNextArrayItem()) break
                versions.add(parseEntry(j, pkg))
            }
            j.endArray()
            // 同一包多条版本记录，取 versionCode 最大者作为最新版
            val latest = versions.maxByOrNull { it.versionCode } ?: versions.lastOrNull()
            if (latest != null) onApp(latest)
        }
        j.endObject()
    }

    private fun parseEntry(j: JsonStreamReader, pkg: String): Entry {
        val e = Entry()
        e.packageName = pkg
        j.beginObject()
        while (true) {
            j.optionalComma()
            if (!j.hasNextEntry()) break
            when (val k = j.readName()) {
                "name" -> e.name = j.readValueOrNull() ?: e.name
                "summary" -> e.summary = j.readValueOrNull() ?: e.summary
                "description" -> e.description = (j.readValueOrNull() ?: "").let { stripHtml(it) }
                "icon" -> e.icon = j.readValueOrNull() ?: e.icon
                "versionName" -> e.versionName = j.readValueOrNull() ?: e.versionName
                "versionCode" -> e.versionCode = j.readValueOrNull()?.toDoubleOrNull()?.toLong() ?: e.versionCode
                "apkName" -> e.apkName = j.readValueOrNull() ?: e.apkName
                "hash" -> e.hash = j.readValueOrNull() ?: e.hash
                "size" -> e.size = j.readValueOrNull()?.toLongOrNull() ?: e.size
                "lastUpdated" -> e.lastUpdated = j.readValueOrNull()?.toLongOrNull() ?: e.lastUpdated
                "added" -> e.added = j.readValueOrNull()?.toLongOrNull() ?: e.added
                "license" -> e.license = j.readValueOrNull() ?: e.license
                "sourceCode" -> e.sourceCode = j.readValueOrNull() ?: e.sourceCode
                "website" -> e.website = j.readValueOrNull() ?: e.website
                "issueTracker" -> e.issueTracker = j.readValueOrNull() ?: e.issueTracker
                "authorName" -> e.authorName = j.readValueOrNull() ?: e.authorName
                "categories" -> e.categories = readStringArray(j)
                "antiFeatures" -> e.antiFeatures = readStringArray(j)
                "localized" -> parseLocalized(j, e)
                else -> {
                    if (k == "packageName") {
                        j.readValueOrNull()?.let { e.packageName = it }
                    } else {
                        j.skipValue()
                    }
                }
            }
        }
        j.endObject()
        return e
    }

    private fun parseLocalized(j: JsonStreamReader, e: Entry) {
        j.beginObject()
        while (true) {
            j.optionalComma()
            if (!j.hasNextEntry()) break
            val lang = j.readName()
            val isZh = lang.lowercase().startsWith("zh")
            j.beginObject()
            while (true) {
                j.optionalComma()
                if (!j.hasNextEntry()) break
                when (val k = j.readName()) {
                    "name" -> {
                        val v = j.readValueOrNull()
                        if (isZh) e.nameZh = v
                    }
                    "summary" -> {
                        val v = j.readValueOrNull()
                        if (isZh) e.summaryZh = v
                    }
                    else -> j.skipValue()
                }
            }
            j.endObject()
        }
        j.endObject()
    }

    private fun readStringArray(j: JsonStreamReader): List<String> {
        if (j.peek() == 'n'.code) {
            j.readValueOrNull()
            return emptyList()
        }
        if (j.peek() != '['.code) {
            j.skipValue()
            return emptyList()
        }
        val out = ArrayList<String>()
        j.beginArray()
        while (true) {
            j.optionalComma()
            if (!j.hasNextArrayItem()) break
            val v = j.readValueOrNull()
            if (v != null && !v.startsWith("[")) out.add(v)
        }
        j.endArray()
        return out
    }
}