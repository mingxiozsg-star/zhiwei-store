package com.zhiwei.store.data

import android.content.Context
import com.zhiwei.store.model.AppDetail
import com.zhiwei.store.model.AppItem
import com.zhiwei.store.model.MergedApp
import com.zhiwei.store.model.SourceState
import com.zhiwei.store.source.SourceRegistry
import com.zhiwei.store.source.StoreSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * 聚合仓库：把多个商店的搜索结果并发拉回来，按包名合并成一个条目，
 * 同一个应用能看到「来自哪些源」以及各自版本。
 */
class StoreRepository(context: Context, val prefs: StorePrefs = StorePrefs(context)) {

    val registry = SourceRegistry(context, prefs)

    data class Outcome(
        val sourceId: String,
        val sourceName: String,
        val items: List<AppItem>,
        val error: String? = null
    )

    suspend fun searchAll(query: String, page: Int = 1): List<Outcome> = coroutineScope {
        val sources = registry.enabled()
        sources.map { src ->
            async(Dispatchers.IO) {
                runCatching { src.search(query, page) }.fold(
                    onSuccess = { Outcome(src.id, src.name, it) },
                    onFailure = { Outcome(src.id, src.name, emptyList(), it.message ?: "查询失败") }
                )
            }
        }.awaitAll()
    }

    fun mergeAll(outcomes: List<Outcome>, query: String = ""): List<MergedApp> {
        val grouped = LinkedHashMap<String, MutableList<AppItem>>()
        outcomes.forEach { outcome ->
            outcome.items.forEach { item ->
                grouped.getOrPut(item.mergeKey) { ArrayList(2) }.add(item)
            }
        }
        val merged = grouped.map { (key, list) ->
            val sorted = list.sortedWith(
                compareByDescending<AppItem> { it.versionCode }
                    .thenByDescending { it.collectScore() }
            )
            MergedApp(key, sorted.first(), sorted.drop(1))
        }
        if (!prefs.mergeAcrossSources()) {
            // 不合并时按源拆开展示
            return merged.flatMap { m ->
                m.all.map { app -> MergedApp(app.mergeKey + "@" + app.sourceId, app, emptyList()) }
            }
        }
        val q = query.trim().lowercase()
        return merged.sortedWith(
            compareBy<MergedApp> { app ->
                val name = app.primary.name.lowercase()
                val pkg = app.primary.packageName?.lowercase() ?: ""
                when {
                    pkg == q || name == q -> 0
                    name.startsWith(q) -> 1
                    pkg.startsWith(q) -> 2
                    else -> 3
                }
            }.thenByDescending { it.sourceCount }
                .thenByDescending { it.all.maxOf { a -> a.collectScore() } }
        )
    }

    private fun AppItem.collectScore(): Long =
        downloads.coerceAtMost(5_000_000) + (rating * 1000).toLong() + (if (iconUrl != null) 10 else 0)

    suspend fun detail(item: AppItem): AppDetail = withContext(Dispatchers.IO) {
        val source = registry.byId(item.sourceId) ?: throw IllegalStateException("找不到源：${item.sourceId}")
        source.detail(item)
    }

    suspend fun featured(): List<AppItem> = coroutineScope {
        val sources = registry.enabled()
        sources.map { src ->
            async(Dispatchers.IO) {
                runCatching { src.featured() }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten()
            .distinctBy { it.packageName ?: "${it.sourceId}:${it.name}" }
    }

    /** 同步索引型源；返回写入条数 */
    suspend fun syncIndex(source: StoreSource, onStage: (String) -> Unit = {}): Int = withContext(Dispatchers.IO) {
        when (source) {
            is com.zhiwei.store.source.fdroid.FdroidSource -> source.sync(onStage)
            else -> 0
        }
    }

    suspend fun health(): List<SourceState> = coroutineScope {
        registry.all.map { src ->
            async(Dispatchers.IO) {
                val status = runCatching { src.healthCheck() }.fold(
                    onSuccess = { it },
                    onFailure = { it.message ?: "不可用" }
                )
                SourceState(
                    id = src.id,
                    name = src.name,
                    kind = src.kind,
                    note = src.note,
                    enabled = prefs.isSourceEnabled(src.id),
                    status = status
                )
            }
        }.awaitAll()
    }
}