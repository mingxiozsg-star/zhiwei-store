package com.zhiwei.store.util

import android.content.Context
import android.content.pm.PackageManager

/** 查询已安装应用版本，用于在详情页显示「已安装 / 可更新」 */
object InstalledApps {

    private val cache = HashMap<String, Long>()

    fun versionCode(context: Context, pkg: String?): Long {
        if (pkg.isNullOrBlank()) return 0L
        cache[pkg]?.let { return it }
        val code = runCatching {
            val info = context.packageManager.getPackageInfo(pkg, 0)
            @Suppress("DEPRECATION")
            if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        }.getOrDefault(0L)
        cache[pkg] = code
        return code
    }

    fun isInstalled(context: Context, pkg: String?): Boolean = versionCode(context, pkg) > 0

    fun refresh(pkg: String?) {
        if (pkg != null) cache.remove(pkg)
    }

    fun hasInstallPermission(context: Context): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            context.packageManager.canRequestPackageInstalls()
        } else true

    fun isPackageManagerAvailable(context: Context, pkg: String): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        }.getOrDefault(false)

    @Suppress("unused")
    fun pm(context: Context): PackageManager = context.packageManager
}