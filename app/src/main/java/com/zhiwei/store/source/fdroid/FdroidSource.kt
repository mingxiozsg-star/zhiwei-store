package com.zhiwei.store.source.fdroid

import com.zhiwei.store.core.Http
import com.zhiwei.store.model.AppDetail
import com.zhiwei.store.model.AppItem
import com.zhiwei.store.model.DownloadOption
import com.zhiwei.store.model.SourceKind
import com.zhiwei.store.source.StoreSource
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

/**
 * F-Droid 系源（F-Droid / IzzyOnDroid / 自定义索引）。
 * 索引体积大（约 18MB），本地 SQLite 缓存 + 24 小时增量同步。
 */
class FdroidSource(
    private val cacheDir: File,
    private val mirrorProvider: () -> String,
    private val db: FdroidIndexDb,
    override val id: String = "fdroid",
    override val name: String = "F-Droid",
    override val note: String = "开源安卓应用仓库，全部可查源码；国内自动走清华/中科大镜像",
    private val altMirrors: List<String> = com.zhiwei.store.data.StorePrefs.FDROID_MIRRORS.map { it.second },
    private val cacheTag: String = "fdroid",
    /** 是否自动测速挑最快镜像；关闭时只用用户在设置里选定的节点 */
    private val autoPick: () -> Boolean = { true }
) : StoreSource {
    override val kind: SourceKind = SourceKind.OFFICIAL
    override val homepage: String = "https://f-droid.org"

    private fun mirror(): String = mirrorProvider().trimEnd('/')

    /** 本地索引里的应用数量 */
    fun count(): Int = db.count()

    /** 最近更新的应用（首页用） */
    fun recent(limit: Int = 24): List<AppItem> =
        if (db.count() == 0) emptyList() else db.recentUpdated(limit).map { it.toItem(mirror()) }

    override fun search(query: String, page: Int): List<AppItem> {
        if (db.count() == 0) return emptyList()
        return db.search(query, 60).map { it.toItem(mirror()) }
    }

    override fun featured(): List<AppItem> {
        if (db.count() == 0) return emptyList()
        return db.recentUpdated(24).map { it.toItem(mirror()) }
    }

    fun newest(limit: Int = 30): List<AppItem> =
        if (db.count() == 0) emptyList() else db.newest(limit).map { it.toItem(mirror()) }

    override fun detail(item: AppItem): AppDetail {
        val e = db.byPackage(item.packageName ?: "") ?: return AppDetail(item)
        val base = mirror()
        val apkUrl = if (e.apkName.isNotBlank()) "$base/${e.apkName}" else item.downloadUrl
        return AppDetail(
            item = item.copy(
                summary = e.displaySummary(),
                versionName = e.versionName,
                versionCode = e.versionCode,
                sizeBytes = e.size,
                updatedAt = e.lastUpdated,
                iconUrl = e.icon.takeIf { it.isNotBlank() }?.let { "$base/$it" },
                developer = e.authorName.takeIf { it.isNotBlank() },
                downloadUrl = apkUrl
            ),
            description = e.description,
            options = listOfNotNull(
                apkUrl?.let {
                    DownloadOption(
                        label = "${name} 最新版${if (e.versionName.isNotBlank()) " · ${e.versionName}" else ""}",
                        url = it,
                        versionName = e.versionName,
                        versionCode = e.versionCode,
                        sizeBytes = e.size
                    )
                }
            ),
            homepage = e.website.takeIf { it.isNotBlank() },
            license = e.license.takeIf { it.isNotBlank() },
            sourceUrl = e.sourceCode.takeIf { it.isNotBlank() },
            extra = buildList {
                if (e.categories.isNotEmpty()) add("分类" to e.categories.joinToString(" / "))
                if (e.antiFeatures.isNotEmpty()) add("已知限制" to e.antiFeatures.joinToString(" / "))
                if (e.hash.isNotBlank()) add("SHA-256" to e.hash)
                add("索引" to "$base/index-v1.jar")
            }
        )
    }

    /** 同步索引；返回写入的应用条数 */
    fun sync(onStage: (String) -> Unit = {}): Int {
        onStage("挑选最快的镜像节点…")
        val base = pickMirror()
        val jar = File(cacheDir, "$cacheTag-index-v1.jar")
        onStage("下载索引（约 18MB）…")
        jar.parentFile?.mkdirs()
        val tmp = File(cacheDir, "$cacheTag-index-v1.jar.tmp")
        java.io.FileOutputStream(tmp).use { out ->
            Http.download(
                url = "$base/index-v1.jar",
                out = out,
                timeoutMs = 60000
            ) { _, _ -> true }
        }
        if (jar.exists()) jar.delete()
        if (!tmp.renameTo(jar)) {
            tmp.copyTo(jar, overwrite = true)
            tmp.delete()
        }

        onStage("解析索引并写入本地库…")
        val entries = ArrayList<FdroidIndexParser.Entry>(5000)
        ZipInputStream(FileInputStream(jar).buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name.endsWith("index-v1.json")) {
                    FdroidIndexParser.parse(zip) { entries.add(it) }
                    break
                }
            }
        }
        if (entries.isEmpty()) throw IllegalStateException("索引解析为空，镜像可能不完整")

        onStage("写入 ${entries.size} 个应用…")
        val written = db.replaceAll(entries)
        runCatching { jar.delete() }
        return written
    }

    /** 依次探测镜像，返回第一个可用节点；关闭自动挑选时直接用配置值 */
    fun pickMirror(): String {
        val configured = mirror()
        if (!autoPick()) return configured
        val candidates = (listOf(configured) + altMirrors).distinct()
        for (m in candidates) {
            val code = Http.ping("$m/index-v1.jar", 6000)
            if (code in 200..399) return m.trimEnd('/')
        }
        return configured
    }

    override fun healthCheck(): String {
        val base = mirror()
        val count = db.count()
        if (count > 0) return "本地索引 $count 个应用 · 镜像 $base"
        val code = Http.ping("$base/index-v1.jar", 8000)
        return if (code in 200..399) "镜像可用（$code），索引尚未同步" else "镜像不可达（$code），请切换镜像"
    }

    private fun FdroidIndexParser.Entry.toItem(base: String): AppItem = AppItem(
        sourceId = id,
        sourceName = name,
        packageName = packageName,
        name = displayName().ifBlank { packageName },
        summary = displaySummary(),
        iconUrl = icon.takeIf { it.isNotBlank() }?.let { "$base/$it" },
        versionName = versionName.takeIf { it.isNotBlank() },
        versionCode = versionCode,
        sizeBytes = size,
        updatedAt = lastUpdated,
        developer = authorName.takeIf { it.isNotBlank() },
        downloadUrl = apkName.takeIf { it.isNotBlank() }?.let { "$base/$it" },
        detailUrl = website.takeIf { it.isNotBlank() } ?: sourceCode.takeIf { it.isNotBlank() },
        category = categories.firstOrNull(),
        tags = buildList {
            add("开源")
            categories.take(2).forEach { add(it) }
        },
        raw = mapOf(
            "mirror" to base,
            "license" to license,
            "sourceCode" to sourceCode,
            "hash" to hash
        )
    )
}