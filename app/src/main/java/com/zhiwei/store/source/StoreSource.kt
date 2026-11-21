package com.zhiwei.store.source

import com.zhiwei.store.model.AppDetail
import com.zhiwei.store.model.AppItem
import com.zhiwei.store.model.SourceKind

/**
 * 所有商店源的统一接口。
 * 新增源只需实现本接口并注册到 SourceRegistry，UI 与搜索聚合自动生效。
 */
interface StoreSource {
    val id: String
    val name: String
    val kind: SourceKind

    /** 显示在设置页的说明（可达性、限制） */
    val note: String
    val homepage: String

    fun search(query: String, page: Int = 1): List<AppItem>

    /** 首页推荐位，默认没有 */
    fun featured(): List<AppItem> = emptyList()

    fun detail(item: AppItem): AppDetail

    /** 探测源是否可用，返回人类可读状态；抛异常视为失败 */
    fun healthCheck(): String
}

/** 部分源需要额外配置（如 token），未配置时给出提示而不是报错 */
class SourceSetupRequired(message: String) : Exception(message)