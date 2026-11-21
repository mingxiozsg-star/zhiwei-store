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
 * 酷安（Coolapk）：国内第三方应用社区。
 * 官方接口需要 App Token，用户可在设置页填入；未配置时明确提示而不是静默失败。
 */
class CoolapkSource(private val tokenProvider: () -> String) : StoreSource {

    override val id: String = "coolapk"
    override val name: String = "酷安"
    override val kind: SourceKind = SourceKind.THIRD_PARTY
    override val note: String = "国内第三方应用社区，需在设置里填入 Access Token 才能检索"
    override val homepage: String = "https://www.coolapk.com"

    private fun headers(): Map<String, String> {
        val token = tokenProvider().trim()
        val h = hashMapOf(
            "User-Agent" to "Dalvik/2.1.0 (Linux; U; Android 13; SM-G991B) +CoolMarket/13.5.2",
            "X-App-Id" to "com.coolapk.market",
            "X-Requested-With" to "XMLHttpRequest"
        )
        if (token.isNotEmpty()) h["X-App-Token"] = token
        return h
    }

    private fun enc(q: String) = URLEncoder.encode(q, "UTF-8")

    override fun search(query: String, page: Int): List<AppItem> {
        if (query.isBlank()) return emptyList()
        if (tokenProvider().isBlank()) throw SourceSetupRequired("酷安接口需要 Access Token，请在「设置 → 源与凭证」里填写")
        val url = "https://api.coolapk.com/v6/search?type=8&q=${enc(query)}&page=$page"
        val resp = Http.get(url, headers(), 15000)
        if (resp.code == 403 || resp.code == 401) throw IllegalStateException("酷安返回 ${resp.code}，Token 可能已失效")
        if (!resp.ok) throw IllegalStateException("酷安返回 ${resp.code}")
        val data = JSONObject(resp.body).a("data") ?: return emptyList()
        val out = ArrayList<AppItem>()
        for (i in 0 until data.length()) {
            val item = data.o(i) ?: continue
            val pkg = item.s("packageName", "package_name")
            val name = item.s("title", "name") ?: continue
            out.add(
                AppItem(
                    sourceId = id,
                    sourceName = name,
                    packageName = pkg,
                    name = name,
                    summary = stripHtml(item.s("description", "intro") ?: "").take(200),
                    iconUrl = item.s("logo", "icon"),
                    versionName = item.s("version", "versionName"),
                    updatedAt = 0L,
                    developer = item.s("developer", "author"),
                    rating = item.d("score", "rating"),
                    detailUrl = item.s("url")?.let { if (it.startsWith("http")) it else "https://www.coolapk.com$it" },
                    downloads = item.l("downCount", "down"),
                    tags = listOf("酷安"),
                    raw = mapOf("id" to (item.l("id").toString()))
                )
            )
        }
        return out
    }

    override fun detail(item: AppItem): AppDetail {
        val pkg = item.packageName
        val apkId = item.raw["id"]
        var description = item.summary
        val options = ArrayList<DownloadOption>()
        runCatching {
            val id = apkId?.takeIf { it != "0" }
            val url = if (id != null) {
                "https://api.coolapk.com/v6/apk/detail?id=$id"
            } else if (pkg != null) {
                "https://api.coolapk.com/v6/apk/detail?packageName=${enc(pkg)}"
            } else {
                return@runCatching
            }
            val resp = Http.get(url, headers(), 15000)
            if (resp.ok) {
                val data = JSONObject(resp.body).o("data")
                data?.s("description", "intro")?.let { description = stripHtml(it) }
                val apkUrl = data?.s("apkUrl", "downloadUrl", "apkDownloadUrl")
                if (apkUrl != null) {
                    options.add(
                        DownloadOption(
                            label = "酷安官方包 · ${data.s("version") ?: item.versionName ?: "最新版"}",
                            url = apkUrl,
                            versionName = data.s("version") ?: item.versionName,
                            sizeBytes = data.l("apkSize", "size")
                        )
                    )
                }
            }
        }
        return AppDetail(
            item = item.copy(downloadUrl = options.firstOrNull()?.url),
            description = description,
            options = options,
            homepage = item.detailUrl,
            extra = buildList {
                pkg?.let { add("包名" to it) }
                add("说明" to "酷安接口为私有协议，Token 失效时需要重新获取")
                if (options.isEmpty()) add("提示" to "未取到直链，可打开酷安页面手动下载")
            }
        )
    }

    override fun healthCheck(): String {
        if (tokenProvider().isBlank()) throw SourceSetupRequired("未填写 Access Token")
        val resp = Http.get("https://api.coolapk.com/v6/search?type=8&q=wechat&page=1", headers(), 12000)
        if (resp.code == 403 || resp.code == 401) throw IllegalStateException("Token 无效（${resp.code}）")
        if (!resp.ok) throw IllegalStateException("返回 ${resp.code}")
        val n = JSONObject(resp.body).a("data")?.length() ?: 0
        return "接口正常（样本 $n 条）"
    }
}