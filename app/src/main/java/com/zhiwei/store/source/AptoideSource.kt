package com.zhiwei.store.source

import com.zhiwei.store.core.Http
import com.zhiwei.store.core.a
import com.zhiwei.store.core.d
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
 * Aptoide 聚合源：一个接口就能拿到 Aptoide 生态内多家第三方商店的同名应用，
 * 天然满足「多商店汇聚」；返回结果带 store 字段，可看到应用来自哪家店。
 */
class AptoideSource : StoreSource {

    override val id: String = "aptoide"
    override val name: String = "Aptoide 商店聚合"
    override val kind: SourceKind = SourceKind.THIRD_PARTY
    override val note: String = "聚合 Aptoide 生态多家第三方商店，同一应用可对比不同店铺版本；国内网络建议代理"
    override val homepage: String = "https://www.aptoide.com"

    private val hosts = listOf(
        "https://ws75.aptoide.com",
        "https://ws2.aptoide.com",
        "https://ws-backend.aptoide.com"
    )

    private fun api(path: String): Http.Response {
        var last: Http.Response? = null
        for (h in hosts) {
            val resp = runCatching { Http.get(h + path, timeoutMs = 15000) }.getOrNull() ?: continue
            if (resp.ok) return resp
            last = resp
        }
        throw IllegalStateException("Aptoide 接口不可达（${last?.code ?: "网络错误"}）")
    }

    private fun enc(q: String) = URLEncoder.encode(q, "UTF-8")

    override fun search(query: String, page: Int): List<AppItem> {
        if (query.isBlank()) return emptyList()
        val offset = (page - 1) * 30
        val resp = api("/api/7/apps/search?query=${enc(query)}&limit=30&offset=$offset")
        val list = JSONObject(resp.body).o("nodes")?.o("meta")?.o("data")?.a("list") ?: return emptyList()
        val out = ArrayList<AppItem>()
        for (i in 0 until list.length()) {
            val app = list.o(i) ?: continue
            out.add(app.toItem())
        }
        return out
    }

    override fun featured(): List<AppItem> {
        val resp = api("/api/7/apps/search?query=popular&limit=15&offset=0")
        val list = JSONObject(resp.body).o("nodes")?.o("meta")?.o("data")?.a("list") ?: return emptyList()
        val out = ArrayList<AppItem>()
        for (i in 0 until list.length()) out.add(list.o(i)?.toItem() ?: continue)
        return out
    }

    private fun JSONObject.toItem(): AppItem {
        val file = o("file")
        val store = o("store")
        val stats = o("stats")
        val pkg = s("package") ?: s("package_name") ?: ""
        return AppItem(
            sourceId = id,
            sourceName = name,
            packageName = pkg.takeIf { it.isNotBlank() },
            name = s("name") ?: pkg,
            summary = stripHtml(s("description") ?: s("meta") ?: "").take(200),
            iconUrl = s("icon"),
            versionName = file?.s("vername"),
            versionCode = file?.l("vercode") ?: 0L,
            sizeBytes = file?.l("filesize") ?: 0L,
            updatedAt = 0L,
            developer = store?.s("name") ?: s("developer_name"),
            rating = stats?.d("prating") ?: 0.0,
            downloadUrl = file?.s("path"),
            detailUrl = "https://www.aptoide.com/app/$pkg",
            downloads = stats?.l("pdownloads") ?: 0L,
            tags = listOfNotNull(store?.s("name")?.let { "店铺：$it" }),
            raw = mapOf(
                "store" to (store?.s("name") ?: "Aptoide"),
                "added" to (s("added") ?: ""),
                "modified" to (s("modified") ?: "")
            )
        )
    }

    override fun detail(item: AppItem): AppDetail {
        val pkg = item.packageName ?: return AppDetail(item)
        var description = item.summary
        val screenshots = ArrayList<String>()
        var options = listOfNotNull(
            item.downloadUrl?.let {
                DownloadOption(
                    label = "Aptoide · ${item.versionName ?: "最新版"}（${item.raw["store"] ?: "Aptoide"}）",
                    url = it,
                    versionName = item.versionName,
                    versionCode = item.versionCode,
                    sizeBytes = item.sizeBytes
                )
            }
        )
        runCatching {
            val resp = api("/api/7/app/getMeta?package_name=${enc(pkg)}")
            val data = JSONObject(resp.body).o("nodes")?.o("meta")?.o("data")
            data?.let { d ->
                val desc = d.s("description")
                if (!desc.isNullOrBlank()) description = stripHtml(desc)
                d.o("media")?.a("screenshots")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        arr.o(i)?.s("url")?.let { screenshots.add(it) }
                    }
                }
                val file = d.o("file")
                val path = file?.s("path")
                if (options.isEmpty() && path != null) {
                    options = listOf(
                        DownloadOption(
                            label = "Aptoide · ${file.s("vername") ?: "最新版"}",
                            url = path,
                            versionName = file.s("vername"),
                            versionCode = file.l("vercode"),
                            sizeBytes = file.l("filesize")
                        )
                    )
                }
            }
        }
        return AppDetail(
            item = item.copy(downloadUrl = options.firstOrNull()?.url),
            description = description,
            screenshots = screenshots,
            options = options,
            homepage = "https://www.aptoide.com/app/$pkg",
            extra = listOf(
                "店铺" to (item.raw["store"] ?: "Aptoide"),
                "包名" to pkg,
                "说明" to "Aptoide 为第三方应用市场聚合，APK 由各店铺上传，安装前请自行确认来源"
            )
        )
    }

    override fun healthCheck(): String {
        val resp = api("/api/7/apps/search?query=wechat&limit=1")
        val n = JSONObject(resp.body).o("nodes")?.o("meta")?.o("data")?.a("list")?.length() ?: 0
        return "接口正常（样本返回 $n 条）"
    }
}