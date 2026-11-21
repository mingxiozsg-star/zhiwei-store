package com.zhiwei.store.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiwei.store.data.StorePrefs
import com.zhiwei.store.data.StoreRepository
import com.zhiwei.store.download.Downloader
import com.zhiwei.store.model.AppItem
import kotlinx.coroutines.launch

/**
 * 应用主框架：底部四页（发现 / 搜索 / 下载 / 我的），详情页整屏覆盖。
 */
@Composable
fun AppStoreApp(context: Context) {
    val prefs = remember { StorePrefs(context) }
    val repo = remember { StoreRepository(context, prefs) }
    val downloader = remember { Downloader(context, prefs) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    var tab by remember { mutableIntStateOf(0) }
    var detailItem by remember { mutableStateOf<AppItem?>(null) }
    var searchSeed by remember { mutableStateOf("") }
    var syncStage by remember { mutableStateOf<String?>(null) }
    var indexCount by remember { mutableIntStateOf(runCatching { repo.registry.fdroid.count() }.getOrDefault(0)) }

    fun toast(text: String) = Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()

    fun startIndexSync() {
        if (syncStage != null) return
        scope.launch {
            syncStage = "准备中…"
            runCatching { repo.syncIndex(repo.registry.fdroid) { stage -> syncStage = stage } }
                .onSuccess {
                    indexCount = repo.registry.fdroid.count()
                    syncStage = null
                    prefs.setLastFdroidSync(System.currentTimeMillis())
                    prefs.setFdroidIndexCount(indexCount)
                    toast("开源索引已更新：$it 个应用")
                }
                .onFailure {
                    syncStage = null
                    toast("索引同步失败：${it.message}")
                }
        }
    }

    BackHandler(enabled = detailItem != null) { detailItem = null }

    Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        bottomBar = {
            if (detailItem == null) {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Text("🏠", fontSize = 18.sp) },
                        label = { Text("发现", fontSize = 11.sp) }
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Text("🔍", fontSize = 18.sp) },
                        label = { Text("搜索", fontSize = 11.sp) }
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Text("⬇", fontSize = 18.sp) },
                        label = { Text("下载", fontSize = 11.sp) }
                    )
                    NavigationBarItem(
                        selected = tab == 3,
                        onClick = { tab = 3 },
                        icon = { Text("⚙", fontSize = 18.sp) },
                        label = { Text("我的", fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val current = detailItem
            if (current != null) {
                DetailScreen(
                    repo = repo,
                    downloader = downloader,
                    item = current,
                    onBack = { detailItem = null },
                    onToast = { toast(it) }
                )
            } else when (tab) {
                0 -> HomeScreen(
                    repo = repo,
                    prefs = prefs,
                    indexCount = indexCount,
                    syncStage = syncStage,
                    onSync = { startIndexSync() },
                    onOpen = { detailItem = it },
                    onSearch = { q ->
                        searchSeed = q
                        tab = 1
                    }
                )
                1 -> SearchScreen(
                    repo = repo,
                    seed = searchSeed,
                    onOpen = { detailItem = it }
                )
                2 -> DownloadsScreen(
                    downloader = downloader,
                    onToast = { toast(it) }
                )
                else -> SettingsScreen(
                    prefs = prefs,
                    repo = repo,
                    indexCount = indexCount,
                    onSync = { startIndexSync() },
                    syncStage = syncStage,
                    onToast = { toast(it) }
                )
            }
        }
    }
}