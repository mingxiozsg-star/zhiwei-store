package com.zhiwei.store.source

import com.zhiwei.store.core.Http
import com.zhiwei.store.core.a
import com.zhiwei.store.core.l
import com.zhiwei.store.core.o
import com.zhiwei.store.core.s
import com.zhiwei.store.core.stripHtml
import com.zhiwei.store.model.AppDetail
import com.zhiwei.store.model.AppItem
import com.zhiwei.store.model.DownloadOption
import com.zhiwei.store.model.SourceKind
import org.json.JSONObject
import java.net.URLEncoder

/**
 * 腾讯应用宝：国内官方商店。
 * 搜索走其公开搜索接口，下载直链从详情页解析；接口随版本调整时可能失效。
 */
class TencentSource : StoreSource {

    override val id: String = "myapp"
    override val name: String = "应用宝（腾讯）"
    override val kind: SourceKind = SourceKind.OFFICIAL
    override val note: String = "腾讯官方商店，国内直连；搜索接口为页面同源接口，稳定性一般"
    override val homepage: String = "https://sj.qq.com"

    private fun enc(q: String) = URLEncoder.encode(q, "UTF-8")

    override fun search(query: String, page: Int): List<AppItem> {
        if (query.isBlank()) return emptyList()
        val urls = listOf(
            "https://sj.qq.com/myapp/searchAjax.htm?kw=${enc(query)}&pns=1",
            "https://android.myapp.com/myapp/searchAjax.htm?kw=${enc(query)}&pns=1"
        )
        var body: String? = null
        var lastCode = -1
        for (u in urls) {
            val resp = runCatching { Http.get(u, timeoutMs = 15000) }.getOrNull() ?: continue
            lastCode = resp.code
            if (resp.ok && resp.body.contains("{")) {
                body = resp.body
                break
            }
        }
        if (body == null) throw IllegalStateException("应用宝接口返回 $lastCode")
        val root = JSONObject(body)
        val items = root.o("obj")?.a("items")
            ?: root.o("obj")?.a("list")
            ?: root.a("items")
            ?: return emptyList()
        val out = ArrayList<AppItem>()
        for (i in 0 until items.length()) {
            val it = items.o(i) ?: continue
            val pkg = it.s("pkgName", "packageName", "pkg") ?: continue
            out.add(
                AppItem(
                    sourceId = id,
                    sourceName = name,
                    packageName = pkg,
                    name = it.s("name", "appName") ?: pkg,
                    summary = stripHtml(it.s("desc", "introduction") ?: "").take(200),
                    iconUrl = it.s("iconUrl", "icon"),
                    versionName = it.s("versionName", "version"),
                    sizeBytes = it.l("size", "fileSize"),
                    developer = it.s("author", "developer"),
                    rating = it.s("rating")?.toDoubleOrNull() ?: 0.0,
                    downloadUrl = it.s("apkUrl"),
                    detailUrl = "https://sj.qq.com/appdetail/$pkg",
                    tags = listOf("应用宝"),
                    raw = mapOf("pkg" to pkg, "appId" to (it.s("appId") ?: ""))
                )
            )
        }
        return out
    }

    override fun detail(item: AppItem): AppDetail {
        val pkg = item.packageName ?: return AppDetail(item)
        val options = ArrayList<DownloadOption>()
        var description = item.summary
        var version = item.versionName
        var icon = item.iconUrl
        item.downloadUrl?.let {
            options.add(DownloadOption(label = "应用宝直链 · ${item.versionName ?: "最新版"}", url = it, versionName = item.versionName))
        }
        if (options.isEmpty()) {
            runCatching {
                val resp = Http.get("https://android.myapp.com/myapp/detail.htm?apkName=${enc(pkg)}", timeoutMs = 15000)
                if (resp.ok) {
                    val html = resp.body
                    Regex("\"(https?://[^\"]*?\\.apk[^\"]*)\"")
                        .findAll(html).map { it.groupValues[1] }.distinct().take(3).forEach { link ->
                            options.add(DownloadOption(label = "应用宝直链", url = link, versionName = version))
                        }
                    Regex("<meta[^>]+name=\"description\"[^>]+content=\"([^\"]*)\"")
                        .find(html)?.groupValues?.get(1)?.let { if (it.isNotBlank()) description = stripHtml(it) }
                    Regex("""iconUrl\s*[:=]\s*['"]([^'"]+)['"]""")
                        .find(html)?.groupValues?.get(1)?.let { icon = it }
                    Regex("""versionName\s*[:=]\s*['"]([^'"]+)['"]""")
                        .find(html)?.groupValues?.get(1)?.let { version = it }
                }
            }
        }
        return AppDetail(
            item = item.copy(iconUrl = icon, versionName = version, downloadUrl = options.firstOrNull()?.url),
            description = description,
            options = options,
            homepage = "https://sj.qq.com/appdetail/$pkg",
            sourceUrl = item.detailUrl,
            extra = listOf(
                "包名" to pkg,
                "说明" to "应用宝为腾讯官方商店，签名由开发者提交，与官网包可能一致"
            )
        )
    }

    override fun healthCheck(): String {
        val code = Http.ping("https://sj.qq.com/", 10000)
        if (code !in 200..399) throw IllegalStateException("站点不可达（$code）")
        val resp = runCatching { Http.get("https://sj.qq.com/myapp/searchAjax.htm?kw=wechat&pns=1", timeoutMs = 12000) }.getOrNull()
        return if (resp != null && resp.ok) "接口正常（${resp.code}）" else "站点可达，搜索接口返回 ${resp?.code ?: "超时"}"
    }
}