package com.zhiwei.store.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiwei.store.core.formatSize
import com.zhiwei.store.data.StorePrefs
import com.zhiwei.store.data.StoreRepository
import com.zhiwei.store.model.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

private val HOME_TABS = listOf("推荐", "热门", "必备", "游戏", "应用榜")

private val TAB_KEYWORDS = mapOf(
    "推荐" to listOf("微信", "抖音", "淘宝", "支付宝", "快手", "QQ"),
    "热门" to listOf("拼多多", "美团", "京东", "高德地图", "剪映", "哔哩哔哩"),
    "必备" to listOf("微信", "支付宝", "输入法", "WPS Office", "夸克", "腾讯视频"),
    "游戏" to listOf("王者荣耀", "和平精英", "原神", "蛋仔派对", "迷你世界", "地铁跑酷"),
    "应用榜" to listOf("抖音", "小红书", "微信", "淘宝", "拼多多", "剪映")
)

/**
 * 首页：常见应用商店的形态——搜索框、分类页签、横滑卡片、带「安装」按钮的榜单。
 * 数据取自主源（豌豆荚）的热词搜索，避免依赖需要先同步的本地索引。
 */
@Composable
fun HomeScreen(
    repo: StoreRepository,
    prefs: StorePrefs,
    indexCount: Int,
    syncStage: String?,
    onSync: () -> Unit,
    onOpen: (AppItem) -> Unit,
    onSearch: (String) -> Unit
) {
    var tab by remember { mutableStateOf(HOME_TABS.first()) }
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var hint by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }
    val cache = remember { mutableStateMapOf<String, List<AppItem>>() }
    val primary = remember { repo.registry.byId("wandoujia") }

    LaunchedEffect(tab, retry) {
        cache[tab]?.let {
            apps = it
            loading = false
            hint = null
            return@LaunchedEffect
        }
        loading = true
        hint = null
        val words = TAB_KEYWORDS[tab] ?: TAB_KEYWORDS.getValue("推荐")
        val fetched = coroutineScope {
            words.map { word ->
                async(Dispatchers.IO) {
                    runCatching { primary?.search(word, 1) }.getOrNull().orEmpty()
                }
            }.awaitAll()
        }.flatten()
            .distinctBy { it.packageName ?: it.name }
            .sortedByDescending { it.downloads }
        apps = fetched
        cache[tab] = fetched
        loading = false
        if (fetched.isEmpty()) hint = "没取到数据，点一下重试"
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { BrandHeader(enabledCount = repo.registry.enabled().size) }
        item {
            SearchEntry(
                onClick = { onSearch("") },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
        item { CategoryTabs(current = tab, onSelect = { tab = it }) }
        item {
            BannerRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                onClick = { onSearch("微信") }
            )
        }
        item {
            SectionTitle(
                title = when (tab) {
                    "应用榜" -> "应用榜"
                    "游戏" -> "热门游戏"
                    else -> "$tab应用"
                },
                right = if (apps.isNotEmpty()) "${apps.size} 款" else null
            )
        }
        if (loading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(modifier = Modifier.size(26.dp)) }
            }
        } else if (apps.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { cache.remove(tab); retry++ }
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = hint ?: "暂无内容",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(apps, key = { it.packageName ?: it.name }) { app ->
                AppRow(
                    app = app,
                    onClick = { onOpen(app) },
                    trailing = { InstallButton { onOpen(app) } }
                )
                ThinDivider()
            }
        }
        item {
            OpenSourceCard(
                indexCount = indexCount,
                syncStage = syncStage,
                onSync = onSync,
                modifier = Modifier.padding(16.dp)
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun BrandHeader(enabledCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFFFF4D7D), Color(0xFFFF8A5C))
                )
            )
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 20.dp)
    ) {
        Column {
            Text(
                text = "知微应用商店",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "聚合商店与开源平台 · 已启用 $enabledCount 个源",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SearchEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "搜索应用、游戏或包名",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(text = "搜索", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun CategoryTabs(current: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        HOME_TABS.forEach { name ->
            val selected = name == current
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onSelect(name) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = name,
                    fontSize = if (selected) 16.sp else 15.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .width(if (selected) 20.dp else 0.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun BannerRow(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BannerCard(
            title = "多源一起搜",
            subtitle = "同一应用看全部下载来源",
            colors = listOf(Color(0xFF4776E6), Color(0xFF8E54E9)),
            onClick = onClick
        )
        BannerCard(
            title = "开源专区",
            subtitle = "GitHub Releases 直连安装",
            colors = listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
            onClick = onClick
        )
    }
}

@Composable
private fun BannerCard(
    title: String,
    subtitle: String,
    colors: List<Color>,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(230.dp)
            .height(96.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(colors))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, right: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        right?.let {
            Text(text = it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InstallButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 7.dp)
    ) {
        Text(text = "安装", color = Color.White, fontSize = 13.sp)
    }
}

@Composable
private fun OpenSourceCard(
    indexCount: Int,
    syncStage: String?,
    onSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp)
    ) {
        Text(
            text = "开源索引",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "F-Droid 索引 ${if (indexCount > 0) "$indexCount 个应用" else "尚未同步"}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                syncStage != null -> syncStage
                indexCount > 0 -> "已就绪，可再次同步"
                else -> "点击同步（约 18MB，建议 Wi-Fi）"
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(enabled = syncStage == null, onClick = onSync)
        )
    }
}
