package com.zhiwei.store.source

import com.zhiwei.store.core.Http
import com.zhiwei.store.core.stripHtml
import com.zhiwei.store.model.AppDetail
import com.zhiwei.store.model.AppItem
import com.zhiwei.store.model.DownloadOption
import com.zhiwei.store.model.SourceKind
import java.net.URLEncoder

/**
 * 豌豆荚：国内可直接访问的聚合商店，搜索页是服务端渲染 HTML，
 * 每个应用都用 data-app-* 属性描述，解析稳定，作为默认主源。
 * 下载：详情页不暴露明文 apk，给出网页入口，由浏览器完成下载安装。
 */
class WandoujiaSource : StoreSource {

    override val id: String = "wandoujia"
    override val name: String = "豌豆荚"
    override val kind: SourceKind = SourceKind.THIRD_PARTY
    override val note: String = "国内直连，搜索稳定；下载跳转网页完成"
    override val homepage: String = "https://m.wandoujia.com"

    private fun enc(q: String) = URLEncoder.encode(q, "UTF-8")

    override fun search(query: String, page: Int): List<AppItem> {
        if (query.isBlank()) return emptyList()
        val resp = Http.get("https://m.wandoujia.com/search?key=${enc(query)}", timeoutMs = 8000)
        if (!resp.ok) throw IllegalStateException("豌豆荚返回 ${resp.code}")
        return parse(resp.body)
    }

    private fun parse(html: String): List<AppItem> {
        val out = ArrayList<AppItem>()
        val blocks = html.split("<a ").drop(1)
        for (block in blocks) {
            val tag = block.substringBefore('>')
            val pkg = attr(tag, "data-app-pname") ?: continue
            val appName = attr(tag, "data-app-name") ?: pkg
            val icon = attr(tag, "data-app-icon")
            val vname = attr(tag, "data-app-vname")
            val vcode = attr(tag, "data-app-vcode")?.toLongOrNull() ?: 0L
            val appId = attr(tag, "data-app-id") ?: attr(tag, "data-app-vid").orEmpty()
            val comment = Regex("class=\"comment\">(.*?)</div>", RegexOption.DOT_MATCHES_ALL)
                .find(block)?.groupValues?.get(1)?.let { stripHtml(it).trim() }.orEmpty()
            val installs = Regex("<span>([^<]{1,24}人安装)</span>")
                .find(block)?.groupValues?.get(1).orEmpty()
            out.add(
                AppItem(
                    sourceId = id,
                    sourceName = "豌豆荚",
                    packageName = pkg,
                    name = appName,
                    summary = comment.take(120),
                    iconUrl = icon,
                    versionName = vname,
                    versionCode = vcode,
                    downloads = parseInstalls(installs),
                    downloadUrl = "https://m.wandoujia.com/apps/$pkg",
                    detailUrl = "https://m.wandoujia.com/apps/$pkg",
                    tags = buildList {
                        add("豌豆荚")
                        if (installs.isNotBlank()) add(installs)
                    },
                    raw = mapOf("appId" to appId, "installs" to installs)
                )
            )
            if (out.size >= 30) break
        }
        return out
    }

    /** 把「2.4亿人安装」转成数值，用于排序与列表展示 */
    private fun parseInstalls(text: String): Long {
        val m = Regex("([0-9.]+)(亿|万)?").find(text) ?: return 0L
        val n = m.groupValues[1].toDoubleOrNull() ?: return 0L
        return when (m.groupValues[2]) {
            "亿" -> (n * 100_000_000).toLong()
            "万" -> (n * 10_000).toLong()
            else -> n.toLong()
        }
    }

    private fun attr(tag: String, key: String): String? =
        Regex(key + "=\"([^\"]*)\"").find(tag)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    override fun detail(item: AppItem): AppDetail {
        val pkg = item.packageName ?: return AppDetail(item)
        val page = "https://m.wandoujia.com/apps/$pkg"
        return AppDetail(
            item = item,
            description = item.summary.ifBlank { "来自豌豆荚的应用：$pkg" },
            options = listOf(
                DownloadOption(
                    label = "打开豌豆荚下载页",
                    url = page,
                    versionName = item.versionName,
                    versionCode = item.versionCode
                )
            ),
            homepage = page,
            sourceUrl = page,
            extra = listOf(
                "包名" to pkg,
                "提示" to "该源在浏览器中完成下载，安装包由豌豆荚提供"
            )
        )
    }

    override fun healthCheck(): String {
        val resp = runCatching { Http.get("https://m.wandoujia.com/search?key=wechat", timeoutMs = 10000) }
            .getOrNull() ?: throw IllegalStateException("连接超时")
        if (!resp.ok) throw IllegalStateException("返回 ${resp.code}")
        val n = Regex("data-app-pname=\"").findAll(resp.body).count()
        if (n == 0) throw IllegalStateException("页面结构变化，未解析到应用")
        return "正常（$n 条）"
    }
}