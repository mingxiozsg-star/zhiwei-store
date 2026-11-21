package com.zhiwei.store.source

import com.zhiwei.store.core.Http
import com.zhiwei.store.core.stripHtml
import com.zhiwei.store.model.AppDetail
import com.zhiwei.store.model.AppItem
import com.zhiwei.store.model.DownloadOption
import com.zhiwei.store.model.SourceKind
import java.net.URLEncoder

/**
 * APKPure 源：网页搜索 + 官方直链下载端点（d.apkpure.com）。
 * 免账号即可拿到 APK；页面结构若有调整，搜索可退化为按包名直接下载。
 */
class ApkPureSource : StoreSource {

    override val id: String = "apkpure"
    override val name: String = "APKPure"
    override val kind: SourceKind = SourceKind.THIRD_PARTY
    override val note: String = "第三方商店，收录 Google Play 应用的历史版本；国内网络建议代理"
    override val homepage: String = "https://apkpure.com"

    private val base = "https://apkpure.com"

    override fun search(query: String, page: Int): List<AppItem> {
        if (query.isBlank()) return emptyList()
        val url = "$base/cn/search?q=${URLEncoder.encode(query, "UTF-8")}"
        val resp = Http.get(url, timeoutMs = 15000)
        if (!resp.ok) throw IllegalStateException("APKPure 返回 ${resp.code}")
        return parseSearch(resp.body)
    }

    private fun parseSearch(html: String): List<AppItem> {
        val out = ArrayList<AppItem>()
        val seen = HashSet<String>()
        // 匹配 <a ... href="/<slug>/<package>"> ... </a>
        val anchor = Regex(
            """<a[^>]+href="/([^"/]+)/([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)"[^>]*>(.*?)</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        for (m in anchor.findAll(html)) {
            val slug = m.groupValues[1]
            val pkg = m.groupValues[2]
            if (!pkg.contains(".") || pkg.startsWith("www.")) continue
            if (!seen.add(pkg)) continue
            val inner = m.groupValues[3]
            val name = Regex("""<p[^>]*class="[^"]*p1[^"]*"[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
                .find(inner)?.groupValues?.get(1)?.let { stripHtml(it) }
                ?: Regex("""<span[^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
                    .find(inner)?.groupValues?.get(1)?.let { stripHtml(it) }
                ?: pkg
            val icon = Regex("<img[^>]+src=\"([^\"]+)\"")
                .find(inner)?.groupValues?.get(1)
            val summary = Regex("""<p[^>]*class="[^"]*p2[^"]*"[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
                .find(inner)?.groupValues?.get(1)?.let { stripHtml(it) } ?: ""
            out.add(
                AppItem(
                    sourceId = id,
                    sourceName = name,
                    packageName = pkg,
                    name = name,
                    summary = summary,
                    iconUrl = icon,
                    downloadUrl = directLink(pkg),
                    detailUrl = "$base/cn/$slug/$pkg",
                    raw = mapOf("slug" to slug)
                )
            )
            if (out.size >= 30) break
        }
        return out
    }

    private fun directLink(pkg: String): String = "https://d.apkpure.com/b/APK/$pkg?version=latest"

    override fun detail(item: AppItem): AppDetail {
        val pkg = item.packageName ?: return AppDetail(item)
        var description = item.summary
        var icon = item.iconUrl
        var version: String? = item.versionName
        var homepage: String? = item.detailUrl
        var screenshots = emptyList<String>()
        runCatching {
            val resp = Http.get(item.detailUrl ?: "$base/cn/x/$pkg", timeoutMs = 15000)
            if (resp.ok) {
                val html = resp.body
                Regex("<meta[^>]+property=\"og:description\"[^>]+content=\"([^\"]*)\"")
                    .find(html)?.groupValues?.get(1)?.let { if (it.isNotBlank()) description = stripHtml(it) }
                Regex("<meta[^>]+property=\"og:image\"[^>]+content=\"([^\"]*)\"")
                    .find(html)?.groupValues?.get(1)?.let { icon = it }
                Regex("""<title>(.*?)</title>""", RegexOption.DOT_MATCHES_ALL)
                    .find(html)?.groupValues?.get(1)?.let { title ->
                        val v = Regex("""(\d+(?:\.\d+){1,3})""").find(title)?.value
                        if (v != null) version = v
                    }
                screenshots = Regex("<img[^>]+src=\"(https://image\\.apkpure\\.com/[^\"]+)\"")
                    .findAll(html).map { it.groupValues[1] }.distinct().take(6).toList()
            }
        }
        val options = listOf(
            DownloadOption(
                label = "APKPure 官方直链 · ${version ?: "最新版"}",
                url = directLink(pkg),
                versionName = version
            )
        )
        return AppDetail(
            item = item.copy(
                iconUrl = icon,
                versionName = version,
                downloadUrl = options.first().url
            ),
            description = description,
            screenshots = screenshots,
            options = options,
            homepage = homepage,
            sourceUrl = item.detailUrl,
            extra = listOf(
                "包名" to pkg,
                "说明" to "APKPure 为第三方镜像站，供参考；如需原版请优先走官方商店"
            )
        )
    }

    override fun healthCheck(): String {
        val code = Http.ping("$base/cn/", 10000)
        if (code !in 200..399) throw IllegalStateException("APKPure 不可达（$code）")
        return "站点正常（$code）"
    }
}