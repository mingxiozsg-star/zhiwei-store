package com.zhiwei.store.source

import com.zhiwei.store.core.Http
import com.zhiwei.store.core.a
import com.zhiwei.store.core.i
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
 * 开源平台源：GitHub Releases。
 * 搜索仓库 → 详情页读取 release 资产里的 APK，提供主链 + 加速镜像两条下载线路。
 */
class GithubSource(private val tokenProvider: () -> String) : StoreSource {

    override val id: String = "github"
    override val name: String = "GitHub 开源发布"
    override val kind: SourceKind = SourceKind.OPEN_SOURCE
    override val note: String = "开源项目的官方 Releases，直接取官方 APK 资产；可填 Token 提升额度"
    override val homepage: String = "https://github.com"

    private fun headers(): Map<String, String> {
        val h = HashMap<String, String>()
        h["Accept"] = "application/vnd.github+json"
        h["X-GitHub-Api-Version"] = "2022-11-28"
        val token = tokenProvider().trim()
        if (token.isNotEmpty()) h["Authorization"] = "Bearer $token"
        return h
    }

    private fun enc(q: String) = URLEncoder.encode(q, "UTF-8")

    override fun search(query: String, page: Int): List<AppItem> {
        if (query.isBlank()) return emptyList()
        val url = "https://api.github.com/search/repositories?q=${
            enc("$query android")
        }&sort=stars&order=desc&per_page=20&page=$page"
        val resp = Http.get(url, headers())
        if (resp.code == 403 || resp.code == 429) {
            throw IllegalStateException("GitHub 接口限流，填入 Token 可提升额度")
        }
        if (!resp.ok) throw IllegalStateException("GitHub 返回 ${resp.code}")
        val root = JSONObject(resp.body)
        val items = root.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<AppItem>()
        for (idx in 0 until items.length()) {
            val repo = items.optJSONObject(idx) ?: continue
            val full = repo.s("full_name") ?: continue
            val desc = repo.s("description") ?: ""
            val topics = repo.optJSONArray("topics")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
            } ?: emptyList()
            val looksAndroid = topics.any { it.contains("android", true) } ||
                desc.contains("android", true) ||
                (repo.s("language") ?: "") in listOf("Kotlin", "Java", "Dart")
            if (!looksAndroid) continue
            out.add(
                AppItem(
                    sourceId = id,
                    sourceName = name,
                    packageName = null,
                    name = repo.s("name") ?: full,
                    summary = desc,
                    iconUrl = repo.o("owner")?.s("avatar_url"),
                    updatedAt = parseIso(repo.s("pushed_at")),
                    developer = repo.o("owner")?.s("login"),
                    downloads = repo.l("stargazers_count"),
                    detailUrl = repo.s("html_url"),
                    tags = buildList {
                        add("开源")
                        topics.take(3).forEach { add(it) }
                    },
                    raw = mapOf(
                        "repo" to full,
                        "branch" to (repo.s("default_branch") ?: "main"),
                        "stars" to repo.l("stargazers_count").toString()
                    )
                )
            )
        }
        return out
    }

    override fun featured(): List<AppItem> {
        val url = "https://api.github.com/search/repositories?q=topic:android-app&sort=stars&order=desc&per_page=15"
        val resp = Http.get(url, headers())
        if (!resp.ok) return emptyList()
        val items = JSONObject(resp.body).optJSONArray("items") ?: return emptyList()
        val out = ArrayList<AppItem>()
        for (i in 0 until items.length()) {
            val repo = items.optJSONObject(i) ?: continue
            val full = repo.s("full_name") ?: continue
            out.add(
                AppItem(
                    sourceId = id,
                    sourceName = name,
                    name = repo.s("name") ?: full,
                    summary = repo.s("description") ?: "",
                    iconUrl = repo.o("owner")?.s("avatar_url"),
                    updatedAt = parseIso(repo.s("pushed_at")),
                    developer = repo.o("owner")?.s("login"),
                    downloads = repo.l("stargazers_count"),
                    detailUrl = repo.s("html_url"),
                    tags = listOf("开源", "GitHub"),
                    raw = mapOf("repo" to full, "stars" to repo.l("stargazers_count").toString())
                )
            )
        }
        return out
    }

    override fun detail(item: AppItem): AppDetail {
        val repo = item.raw["repo"] ?: item.detailUrl?.removePrefix("https://github.com/")?.trim('/')
        ?: return AppDetail(item)
        val options = ArrayList<DownloadOption>()
        var notes = ""
        var homepage: String? = null
        var license: String? = null
        runCatching {
            val relResp = Http.get("https://api.github.com/repos/$repo/releases?per_page=10", headers())
            if (relResp.ok) {
                val releases = org.json.JSONArray(relResp.body)
                for (i in 0 until releases.length()) {
                    val rel = releases.optJSONObject(i) ?: continue
                    val body = stripHtml(rel.s("body") ?: "")
                    if (notes.isBlank()) notes = body.take(4000)
                    val tag = rel.s("tag_name") ?: ""
                    val published = parseIso(rel.s("published_at"))
                    val assets = rel.optJSONArray("assets") ?: continue
                    for (j in 0 until assets.length()) {
                        val asset = assets.optJSONObject(j) ?: continue
                        val assetName = asset.s("name") ?: continue
                        if (!assetName.endsWith(".apk", true)) continue
                        val url = asset.s("browser_download_url") ?: continue
                        val arch = when {
                            assetName.contains("arm64", true) -> "arm64-v8a"
                            assetName.contains("armv7", true) || assetName.contains("armeabi", true) -> "armeabi-v7a"
                            assetName.contains("x86_64", true) -> "x86_64"
                            assetName.contains("x86", true) -> "x86"
                            else -> null
                        }
                        options.add(
                            DownloadOption(
                                label = "$tag · $assetName",
                                url = url,
                                versionName = tag,
                                sizeBytes = asset.l("size"),
                                arch = arch
                            )
                        )
                    }
                    if (options.isNotEmpty()) break
                }
            }
        }
        runCatching {
            val repoResp = Http.get("https://api.github.com/repos/$repo", headers())
            if (repoResp.ok) {
                val r = JSONObject(repoResp.body)
                homepage = r.s("homepage")?.takeIf { it.isNotBlank() } ?: r.s("html_url")
                license = r.o("license")?.s("spdx_id")
            }
        }
        val withMirror = ArrayList<DownloadOption>()
        options.forEach { opt ->
            withMirror.add(opt.copy(label = opt.label + "（官方直连）"))
            withMirror.add(
                opt.copy(
                    label = opt.label + "（加速镜像）",
                    url = "https://ghfast.top/" + opt.url
                )
            )
        }
        return AppDetail(
            item = item.copy(downloadUrl = options.firstOrNull()?.url),
            description = notes.ifBlank { item.summary },
            options = withMirror,
            homepage = homepage,
            license = license,
            sourceUrl = "https://github.com/$repo",
            extra = listOf(
                "仓库" to repo,
                "Star" to (item.raw["stars"] ?: "—"),
                "说明" to "开源项目由作者自行发布，签名与商店版本可能不同"
            )
        )
    }

    override fun healthCheck(): String {
        val resp = Http.get("https://api.github.com/rate_limit", headers(), 10000)
        if (!resp.ok) throw IllegalStateException("GitHub 接口返回 ${resp.code}")
        val remaining = JSONObject(resp.body).o("resources")?.o("core")?.l("remaining") ?: 0
        return "接口正常，剩余额度 $remaining" + if (tokenProvider().isBlank()) "（未填 Token，搜索限 10 次/分钟）" else ""
    }

    private fun parseIso(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        return runCatching {
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
            fmt.parse(text)?.time ?: 0L
        }.getOrDefault(0L)
    }
}