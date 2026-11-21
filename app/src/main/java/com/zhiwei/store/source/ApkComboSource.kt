package com.zhiwei.store.source

import com.zhiwei.store.core.Http
import com.zhiwei.store.core.stripHtml
import com.zhiwei.store.model.AppDetail
import com.zhiwei.store.model.AppItem
import com.zhiwei.store.model.DownloadOption
import com.zhiwei.store.model.SourceKind
import java.net.URLEncoder

/**
 * APKCombo 源：支持多版本、多架构选择，下载直链需要解析其下载页。
 */
class ApkComboSource : StoreSource {

    override val id: String = "apkcombo"
    override val name: String = "APKCombo"
    override val kind: SourceKind = SourceKind.THIRD_PARTY
    override val note: String = "第三方 APK 站点，提供历史版本；国内网络建议代理"
    override val homepage: String = "https://apkcombo.app"

    private val base = "https://apkcombo.app"

    override fun search(query: String, page: Int): List<AppItem> {
        if (query.isBlank()) return emptyList()
        val url = "$base/search/${URLEncoder.encode(query, "UTF-8")}${if (page > 1) "?page=$page" else ""}"
        val resp = Http.get(url, timeoutMs = 15000)
        if (!resp.ok) throw IllegalStateException("APKCombo 返回 ${resp.code}")
        val out = ArrayList<AppItem>()
        val seen = HashSet<String>()
        val anchor = Regex(
            """<a[^>]+href="/([^"/]+)/([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)"[^>]*>(.*?)</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        for (m in anchor.findAll(resp.body)) {
            val slug = m.groupValues[1]
            val pkg = m.groupValues[2]
            if (!pkg.contains(".") || pkg.startsWith("www.")) continue
            if (!seen.add(pkg)) continue
            val inner = m.groupValues[3]
            val name = Regex("""<span[^>]*class="[^"]*name[^"]*"[^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
                .find(inner)?.groupValues?.get(1)?.let { stripHtml(it) }
                ?: Regex("""<div[^>]*class="[^"]*name[^"]*"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
                    .find(inner)?.groupValues?.get(1)?.let { stripHtml(it) }
                ?: pkg
            val icon = Regex("<img[^>]+src=\"([^\"]+)\"").find(inner)?.groupValues?.get(1)
            val summary = Regex("""<span[^>]*class="[^"]*description[^"]*"[^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
                .find(inner)?.groupValues?.get(1)?.let { stripHtml(it) } ?: ""
            out.add(
                AppItem(
                    sourceId = id,
                    sourceName = name,
                    packageName = pkg,
                    name = name,
                    summary = summary,
                    iconUrl = icon,
                    detailUrl = "$base/$slug/$pkg",
                    raw = mapOf("slug" to slug, "downloadPage" to "$base/$slug/$pkg/download")
                )
            )
            if (out.size >= 30) break
        }
        return out
    }

    override fun detail(item: AppItem): AppDetail {
        val pkg = item.packageName ?: return AppDetail(item)
        val downloadPage = item.raw["downloadPage"] ?: "$base/${item.raw["slug"] ?: "app"}/$pkg/download"
        val options = ArrayList<DownloadOption>()
        var description = item.summary
        var icon = item.iconUrl
        var version: String? = null
        runCatching {
            val resp = Http.get(downloadPage, timeoutMs = 15000)
            if (resp.ok) {
                val html = resp.body
                // 直接暴露的 APK 直链
                Regex("href=\"(https?://[^\"]*?(?:apkcombo\\.app|dl\\.apkcombo\\.com)[^\"]*?\\.apk[^\"]*)\"")
                    .findAll(html).map { it.groupValues[1] }.distinct().take(6).forEach { link ->
                        val name = link.substringAfterLast('/')
                        options.add(
                            DownloadOption(
                                label = "APKCombo · $name",
                                url = link,
                                versionName = Regex("""(\d+(?:\.\d+){1,3})""").find(name)?.value,
                                arch = when {
                                    name.contains("arm64", true) -> "arm64-v8a"
                                    name.contains("armeabi", true) -> "armeabi-v7a"
                                    name.contains("x86", true) -> "x86"
                                    else -> null
                                }
                            )
                        )
                    }
                Regex("<meta[^>]+property=\"og:description\"[^>]+content=\"([^\"]*)\"")
                    .find(html)?.groupValues?.get(1)?.let { if (it.isNotBlank()) description = stripHtml(it) }
                Regex("<meta[^>]+property=\"og:image\"[^>]+content=\"([^\"]*)\"")
                    .find(html)?.groupValues?.get(1)?.let { icon = it }
                version = Regex("""Version\s*([\d.]+)""").find(html)?.groupValues?.get(1)
            }
        }
        return AppDetail(
            item = item.copy(iconUrl = icon, versionName = version ?: item.versionName, downloadUrl = options.firstOrNull()?.url),
            description = description,
            options = options,
            homepage = "https://apkcombo.app/$pkg",
            sourceUrl = item.detailUrl,
            extra = listOf(
                "包名" to pkg,
                "下载页" to downloadPage,
                "说明" to "APKCombo 为第三方站点，直链可能随页面调整"
            )
        )
    }

    override fun healthCheck(): String {
        val code = Http.ping("$base/", 10000)
        if (code !in 200..399) throw IllegalStateException("APKCombo 不可达（$code）")
        return "站点正常（$code）"
    }
}