package com.zhiwei.store.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiwei.store.data.StorePrefs
import com.zhiwei.store.data.StoreRepository
import com.zhiwei.store.model.AppItem
import kotlinx.coroutines.delay

private val HOT_WORDS = listOf(
    "微信", "抖音", "支付宝", "Termux", "F-Droid", "KeePassDX", "哔哩哔哩", "Telegram", "VS Code"
)

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
    var recent by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var openSourcePicks by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var loadingPicks by remember { mutableStateOf(true) }
    val enabledCount = remember { repo.registry.enabled().size }

    LaunchedEffect(indexCount) {
        recent = runCatching { repo.registry.fdroid.recent(20) }.getOrDefault(emptyList())
    }

    LaunchedEffect(Unit) {
        openSourcePicks = runCatching {
            repo.registry.all.firstOrNull { it.id == "github" }?.featured() ?: emptyList()
        }.getOrDefault(emptyList())
        loadingPicks = false
    }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFFF4D7D), Color(0xFF8A4DFF))
                        )
                    )
                    .padding(18.dp)
            ) {
                Column {
                    Text(
                        "知微应用商店",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "一个入口，搜遍所有商店",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .clickable { onSearch("") }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text("搜索应用、包名或 GitHub 项目…", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("多源聚合", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "已启用 $enabledCount 个源 · 开源索引 $indexCount 个应用",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        when {
                            syncStage != null -> "同步中…"
                            indexCount == 0 -> "同步索引"
                            else -> "重新同步"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = syncStage == null) { onSync() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                if (syncStage != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        syncStage,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (indexCount == 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "F-Droid 索引约 18MB，同步后开源应用可离线搜索。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            SectionHeader("热门搜索", "点一下直接搜")
        }
        item {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(HOT_WORDS) { word ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable { onSearch(word) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(word, fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            SectionHeader(
                "最近更新（开源）",
                if (indexCount == 0) "先同步索引才能离线搜索" else "来自 F-Droid 本地索引"
            )
        }
        if (recent.isEmpty()) {
            item {
                EmptyBlock(
                    if (indexCount == 0) "还没有开源索引" else "暂无数据",
                    if (indexCount == 0) "点上面的「同步索引」拉取 F-Droid 全量目录" else null
                )
            }
        } else {
            items(recent, key = { "recent-${it.sourceId}-${it.packageName}" }) { app ->
                AppRow(app = app, onClick = { onOpen(app) })
                ThinDivider()
            }
        }

        item {
            SectionHeader("开源精选", "来自 GitHub 官方 Releases")
        }
        if (loadingPicks) {
            item { LoadingBlock("正在读取 GitHub…") }
        } else if (openSourcePicks.isEmpty()) {
            item { EmptyBlock("暂时拿不到 GitHub 数据", "可能是网络或接口限流，稍后再试") }
        } else {
            items(openSourcePicks, key = { "gh-${it.raw["repo"]}" }) { app ->
                AppRow(app = app, onClick = { onOpen(app) })
                ThinDivider()
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "聚合 F-Droid / IzzyOnDroid / GitHub / Aptoide / APKPure / APKCombo / 酷安 / 应用宝",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "APK 由各商店提供，请自行确认来源与签名",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}