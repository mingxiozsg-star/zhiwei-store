package com.zhiwei.store.source

import android.content.Context
import com.zhiwei.store.data.StorePrefs
import com.zhiwei.store.source.fdroid.FdroidIndexDb
import com.zhiwei.store.source.fdroid.FdroidSource
import java.io.File

/**
 * 所有源的注册中心。
 * 新增一个源＝写一个 StoreSource 实现＋在这里追加一行。
 */
class SourceRegistry(context: Context, val prefs: StorePrefs) {

    private val app = context.applicationContext
    private val cacheRoot = File(app.cacheDir, "store").apply { mkdirs() }

    val fdroid: FdroidSource = FdroidSource(
        cacheDir = cacheRoot,
        mirrorProvider = { prefs.fdroidMirror() },
        db = FdroidIndexDb(app, "fdroid_index.db"),
        id = "fdroid",
        name = "F-Droid",
        note = "开源安卓仓库（官方索引），国内自动选清华/中科大镜像",
        altMirrors = StorePrefs.FDROID_MIRRORS.map { it.second },
        cacheTag = "fdroid",
        autoPick = { prefs.autoPickFdroidMirror() }
    )

    val izzy: FdroidSource = FdroidSource(
        cacheDir = cacheRoot,
        mirrorProvider = { "https://apt.izzysoft.de/fdroid/repo" },
        db = FdroidIndexDb(app, "izzy_index.db"),
        id = "izzy",
        name = "IzzyOnDroid",
        note = "F-Droid 兼容的第三方开源仓库，收录大量 GitHub 上架应用；国内需代理",
        altMirrors = emptyList(),
        cacheTag = "izzy"
    )

    private val github = GithubSource { prefs.githubToken() }
    private val aptoide = AptoideSource()
    private val apkpure = ApkPureSource()
    private val apkcombo = ApkComboSource()
    private val coolapk = CoolapkSource { prefs.coolapkToken() }
    private val myapp = TencentSource()

    val all: List<StoreSource> = listOf(fdroid, izzy, github, aptoide, apkpure, apkcombo, coolapk, myapp)

    fun enabled(): List<StoreSource> = all.filter { prefs.isSourceEnabled(it.id) }

    fun byId(id: String): StoreSource? = all.firstOrNull { it.id == id }

    /** 索引型源，需要用户主动同步 */
    fun indexSources(): List<FdroidSource> = listOf(fdroid, izzy)
}