package com.zhiwei.store.data

import android.content.Context

/**
 * 集中管理所有可配置项。用 SharedPreferences，避免引入 DataStore 依赖。
 */
class StorePrefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("zhiwei_store_prefs", Context.MODE_PRIVATE)

    // ---------- 源开关 ----------

    fun enabledSources(): Set<String> {
        val saved = sp.getStringSet(KEY_ENABLED, null)
        if (saved == null) {
            // 首次启动：默认开启国内可达 + 开源源
            return DEFAULT_ENABLED.toSet()
        }
        return saved.toSet()
    }

    fun setSourceEnabled(id: String, enabled: Boolean) {
        val cur = enabledSources().toMutableSet()
        if (enabled) cur.add(id) else cur.remove(id)
        sp.edit().putStringSet(KEY_ENABLED, cur).apply()
    }

    fun isSourceEnabled(id: String): Boolean = enabledSources().contains(id)

    // ---------- F-Droid 镜像 ----------

    fun fdroidMirror(): String = sp.getString(KEY_FDROID_MIRROR, FDROID_MIRRORS[0].second) ?: FDROID_MIRRORS[0].second

    fun setFdroidMirror(url: String) {
        sp.edit().putString(KEY_FDROID_MIRROR, url).apply()
    }

    fun autoPickFdroidMirror(): Boolean = sp.getBoolean(KEY_FDROID_AUTO_MIRROR, true)

    fun setAutoPickFdroidMirror(value: Boolean) {
        sp.edit().putBoolean(KEY_FDROID_AUTO_MIRROR, value).apply()
    }

    // ---------- 令牌 ----------

    fun githubToken(): String = sp.getString(KEY_GITHUB_TOKEN, "") ?: ""

    fun setGithubToken(token: String) {
        sp.edit().putString(KEY_GITHUB_TOKEN, token.trim()).apply()
    }

    fun coolapkToken(): String = sp.getString(KEY_COOLAPK_TOKEN, "") ?: ""

    fun setCoolapkToken(token: String) {
        sp.edit().putString(KEY_COOLAPK_TOKEN, token.trim()).apply()
    }

    // ---------- 下载与安装 ----------

    fun wifiOnly(): Boolean = sp.getBoolean(KEY_WIFI_ONLY, false)

    fun setWifiOnly(v: Boolean) = sp.edit().putBoolean(KEY_WIFI_ONLY, v).apply()

    fun autoInstall(): Boolean = sp.getBoolean(KEY_AUTO_INSTALL, true)

    fun setAutoInstall(v: Boolean) = sp.edit().putBoolean(KEY_AUTO_INSTALL, v).apply()

    fun parallelDownloads(): Int = sp.getInt(KEY_PARALLEL, 2).coerceIn(1, 4)

    fun setParallelDownloads(v: Int) = sp.edit().putInt(KEY_PARALLEL, v).apply()

    // ---------- 行为 ----------

    fun mergeAcrossSources(): Boolean = sp.getBoolean(KEY_MERGE, true)

    fun setMergeAcrossSources(v: Boolean) = sp.edit().putBoolean(KEY_MERGE, v).apply()

    fun includePrerelease(): Boolean = sp.getBoolean(KEY_PRERELEASE, true)

    fun setIncludePrerelease(v: Boolean) = sp.edit().putBoolean(KEY_PRERELEASE, v).apply()

    fun customRepos(): List<String> =
        (sp.getString(KEY_CUSTOM_REPOS, "") ?: "").split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    fun setCustomRepos(list: List<String>) {
        sp.edit().putString(KEY_CUSTOM_REPOS, list.joinToString("\n")).apply()
    }

    fun lastFdroidSync(): Long = sp.getLong(KEY_FDROID_SYNC, 0L)

    fun setLastFdroidSync(v: Long) = sp.edit().putLong(KEY_FDROID_SYNC, v).apply()

    fun fdroidIndexCount(): Int = sp.getInt(KEY_FDROID_COUNT, 0)

    fun setFdroidIndexCount(v: Int) = sp.edit().putInt(KEY_FDROID_COUNT, v).apply()

    companion object {
        private const val KEY_ENABLED = "enabled_sources"
        private const val KEY_FDROID_MIRROR = "fdroid_mirror"
        private const val KEY_FDROID_AUTO_MIRROR = "fdroid_auto_mirror"
        private const val KEY_GITHUB_TOKEN = "github_token"
        private const val KEY_COOLAPK_TOKEN = "coolapk_token"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_AUTO_INSTALL = "auto_install"
        private const val KEY_PARALLEL = "parallel_downloads"
        private const val KEY_MERGE = "merge_sources"
        private const val KEY_PRERELEASE = "include_prerelease"
        private const val KEY_CUSTOM_REPOS = "custom_repos"
        private const val KEY_FDROID_SYNC = "fdroid_last_sync"
        private const val KEY_FDROID_COUNT = "fdroid_index_count"

        /** F-Droid 官方与国内镜像（官方在部分网络不可达，默认走清华） */
        val FDROID_MIRRORS = listOf(
            "清华镜像" to "https://mirrors.tuna.tsinghua.edu.cn/fdroid/repo",
            "中科大镜像" to "https://mirrors.ustc.edu.cn/fdroid/repo",
            "北外镜像" to "https://mirrors.bfsu.edu.cn/fdroid/repo",
            "阿里云镜像" to "https://mirrors.aliyun.com/fdroid/repo",
            "官方源" to "https://f-droid.org/repo"
        )

        val DEFAULT_ENABLED = setOf(
            "fdroid", "github", "aptoide", "apkpure", "apkcombo", "coolapk", "myapp"
        )
    }
}