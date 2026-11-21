package com.zhiwei.store.model

/** 源类型：官方商店 / 第三方商店 / 开源平台 / 镜像 */
enum class SourceKind(val label: String) {
    OFFICIAL("官方商店"),
    THIRD_PARTY("第三方商店"),
    OPEN_SOURCE("开源平台"),
    MIRROR("镜像节点")
}

data class AppItem(
    val sourceId: String,
    val sourceName: String,
    val packageName: String? = null,
    val name: String,
    val summary: String = "",
    val iconUrl: String? = null,
    val versionName: String? = null,
    val versionCode: Long = 0L,
    val sizeBytes: Long = 0L,
    val updatedAt: Long = 0L,
    val developer: String? = null,
    val rating: Double = 0.0,
    val downloadUrl: String? = null,
    val detailUrl: String? = null,
    val category: String? = null,
    val downloads: Long = 0L,
    val tags: List<String> = emptyList(),
    /** 源特有字段（github repo、aptoide store、apkpure slug 等），供详情/下载使用 */
    val raw: Map<String, String> = emptyMap()
) {
    /** 跨源合并用的键 */
    val mergeKey: String get() = packageName?.lowercase() ?: "src:$sourceId:$name"
}

data class DownloadOption(
    val label: String,
    val url: String,
    val versionName: String? = null,
    val versionCode: Long = 0L,
    val sizeBytes: Long = 0L,
    val arch: String? = null,
    val format: String = "APK"
)

data class AppDetail(
    val item: AppItem,
    val description: String = "",
    val screenshots: List<String> = emptyList(),
    val options: List<DownloadOption> = emptyList(),
    val homepage: String? = null,
    val license: String? = null,
    val sourceUrl: String? = null,
    val permissions: List<String> = emptyList(),
    val extra: List<Pair<String, String>> = emptyList()
)

/** 同一应用在多个源的结果聚合 */
data class MergedApp(
    val key: String,
    val primary: AppItem,
    val others: List<AppItem> = emptyList()
) {
    val all: List<AppItem> get() = listOf(primary) + others
    val sourceCount: Int get() = all.map { it.sourceId }.distinct().size
}

data class SourceState(
    val id: String,
    val name: String,
    val kind: SourceKind,
    val note: String,
    val enabled: Boolean,
    val status: String = "未检测",
    val itemCount: Int = 0
)

data class DownloadTask(
    val id: String,
    val name: String,
    val packageName: String?,
    val sourceName: String,
    val url: String,
    val fileName: String,
    val totalBytes: Long = 0L,
    val doneBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.WAITING,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val progress: Float
        get() = when {
            status == DownloadStatus.DONE -> 1f
            totalBytes <= 0 -> 0f
            else -> (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
        }
}

enum class DownloadStatus(val label: String) {
    WAITING("排队中"),
    RUNNING("下载中"),
    PAUSED("已暂停"),
    DONE("已完成"),
    FAILED("失败"),
    CANCELED("已取消")
}