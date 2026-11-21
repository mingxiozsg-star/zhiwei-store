package com.zhiwei.store.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiwei.store.core.formatDate
import com.zhiwei.store.data.StorePrefs
import com.zhiwei.store.data.StoreRepository
import com.zhiwei.store.model.SourceState
import kotlinx.coroutines.launch

/**
 * 设置页：源管理、开源索引、下载策略、令牌、行为开关、关于。
 */
@Composable
fun SettingsScreen(
    prefs: StorePrefs,
    repo: StoreRepository,
    indexCount: Int,
    onSync: () -> Unit,
    syncStage: String?,
    onToast: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var health by remember { mutableStateOf<List<SourceState>?>(null) }
    var checking by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }   // "github" / "coolapk" / "mirror" / "repos"
    var input by remember { mutableStateOf("") }

    fun refresh() {
        tick++
    }

    // 让开关状态随 tick 变化重新读取
    val enabled = remember(tick) { prefs.enabledSources() }
    val sources = remember { repo.registry.all }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)) {
                Text("我的", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(
                    "所有源聚合在一起，一个应用商店看全网",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---------------- 源管理 ----------------
        item {
            SectionHeader(
                title = "应用源",
                subtitle = "已开启 ${enabled.size} / ${sources.size} 个源",
                action = {
                    TextButton(
                        enabled = !checking,
                        onClick = {
                            checking = true
                            scope.launch {
                                health = runCatching { repo.health() }.getOrDefault(emptyList())
                                checking = false
                                onToast("源状态检测完成")
                            }
                        }
                    ) { Text(if (checking) "检测中…" else "检测", fontSize = 12.sp) }
                }
            )
        }
        items(sources.size) { index ->
            val src = sources[index]
            val state = health?.firstOrNull { it.id == src.id }
            val on = enabled.contains(src.id)
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(src.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Spacer(Modifier.width(6.dp))
                            SourceChip(src.kind.label)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = src.note,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state != null) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "状态：${state.status}",
                                fontSize = 10.sp,
                                color = if (state.status.contains("可用") || state.status.contains("正常") ||
                                    state.status.contains("OK") || state.status.contains("次限额") ||
                                    state.status.contains("已就绪")
                                ) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Switch(
                        checked = on,
                        onCheckedChange = {
                            prefs.setSourceEnabled(src.id, it)
                            refresh()
                        }
                    )
                }
                ThinDivider()
            }
        }

        // ---------------- 开源索引 ----------------
        item {
            SectionHeader(
                title = "开源索引",
                subtitle = "F-Droid 索引决定本地可搜到的开源应用数量"
            )
        }
        item {
            SettingRow(
                title = "镜像节点",
                value = StorePrefs.FDROID_MIRRORS.firstOrNull { it.second == prefs.fdroidMirror() }?.first
                    ?: prefs.fdroidMirror(),
                onClick = {
                    input = prefs.fdroidMirror()
                    dialog = "mirror"
                }
            )
        }
        item {
            SettingRow(
                title = "自动选最快镜像",
                value = if (prefs.autoPickFdroidMirror()) "已开启" else "已关闭",
                onClick = {
                    prefs.setAutoPickFdroidMirror(!prefs.autoPickFdroidMirror())
                    refresh()
                }
            )
        }
        item {
            SettingRow(
                title = "同步索引",
                value = when {
                    syncStage != null -> syncStage!!
                    indexCount > 0 -> "已收录 $indexCount 个应用"
                    else -> "尚未同步，点击开始"
                },
                onClick = { onSync() }
            )
        }
        item {
            val last = prefs.lastFdroidSync()
            Text(
                text = if (last > 0) "上次同步：${formatDate(last)}" else "上次同步：从未",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
            )
        }

        // ---------------- 下载与安装 ----------------
        item { SectionHeader(title = "下载与安装") }
        item {
            SwitchRow(
                title = "仅 Wi-Fi 下载",
                subtitle = "移动网络下任务会暂停等待",
                checked = prefs.wifiOnly(),
                onChange = {
                    prefs.setWifiOnly(it)
                    refresh()
                }
            )
        }
        item {
            SwitchRow(
                title = "下载完成自动安装",
                subtitle = "需要「安装未知应用」权限",
                checked = prefs.autoInstall(),
                onChange = {
                    prefs.setAutoInstall(it)
                    refresh()
                }
            )
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("同时下载任务数", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(
                        "${prefs.parallelDownloads()}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                Slider(
                    value = prefs.parallelDownloads().toFloat(),
                    onValueChange = {
                        prefs.setParallelDownloads(it.toInt().coerceIn(1, 4))
                        refresh()
                    },
                    valueRange = 1f..4f,
                    steps = 2
                )
            }
        }

        // ---------------- 搜索与合并 ----------------
        item { SectionHeader(title = "搜索与合并") }
        item {
            SwitchRow(
                title = "跨源合并同名应用",
                subtitle = "同一包名的多个来源合并为一条，详情页可切换来源",
                checked = prefs.mergeAcrossSources(),
                onChange = {
                    prefs.setMergeAcrossSources(it)
                    refresh()
                }
            )
        }
        item {
            SwitchRow(
                title = "包含预发布版本",
                subtitle = "GitHub Releases 中的 beta / rc 资产",
                checked = prefs.includePrerelease(),
                onChange = {
                    prefs.setIncludePrerelease(it)
                    refresh()
                }
            )
        }

        // ---------------- 令牌 ----------------
        item { SectionHeader(title = "访问令牌", subtitle = "部分源需要令牌，不填则只跳过该源") }
        item {
            SettingRow(
                title = "GitHub Token",
                value = prefs.githubToken().let { if (it.isBlank()) "未设置（限速 60 次/时）" else mask(it) },
                onClick = {
                    input = prefs.githubToken()
                    dialog = "github"
                }
            )
        }
        item {
            SettingRow(
                title = "酷安 Token",
                value = prefs.coolapkToken().let { if (it.isBlank()) "未设置（酷安源会跳过）" else mask(it) },
                onClick = {
                    input = prefs.coolapkToken()
                    dialog = "coolapk"
                }
            )
        }
        item {
            SettingRow(
                title = "自定义 F-Droid 兼容仓库",
                value = "已添加 ${prefs.customRepos().size} 个",
                onClick = {
                    input = prefs.customRepos().joinToString("\n")
                    dialog = "repos"
                }
            )
        }

        // ---------------- 关于 ----------------
        item { SectionHeader(title = "关于") }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("知微应用商店 1.0.0", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "把官方商店、第三方商店与开源平台（GitHub Releases / F-Droid）"
                        + "的下载源汇聚到一处：一次搜索，看全部来源、全部版本。\n"
                        + "构建与发布由 GitHub Actions 完成，不在本地打包。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(6.dp))
                Row {
                    TagChip("F-Droid")
                    Spacer(Modifier.width(6.dp))
                    TagChip("IzzyOnDroid")
                    Spacer(Modifier.width(6.dp))
                    TagChip("GitHub")
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    TagChip("Aptoide")
                    Spacer(Modifier.width(6.dp))
                    TagChip("APKPure")
                    Spacer(Modifier.width(6.dp))
                    TagChip("APKCombo")
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    TagChip("酷安")
                    Spacer(Modifier.width(6.dp))
                    TagChip("应用宝")
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    // ---------------- 对话框 ----------------
    when (dialog) {
        "mirror" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("选择 F-Droid 镜像") },
            text = {
                Column {
                    StorePrefs.FDROID_MIRRORS.forEach { (label, url) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (prefs.fdroidMirror() == url)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(label, fontSize = 13.sp)
                                Text(
                                    url,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = {
                                prefs.setFdroidMirror(url)
                                dialog = null
                                refresh()
                                onToast("已切换镜像：$label")
                            }) { Text("选", fontSize = 12.sp) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialog = null }) { Text("关闭") }
            }
        )

        "github", "coolapk" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(if (dialog == "github") "GitHub Token" else "酷安 Token") },
            text = {
                Column {
                    Text(
                        if (dialog == "github")
                            "填写后有更高 API 限额；留空则匿名访问（60 次/小时）。"
                        else
                            "酷安接口需要 X-App-Token，留空则自动跳过酷安源。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        label = { Text("令牌", fontSize = 12.sp) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (dialog == "github") prefs.setGithubToken(input) else prefs.setCoolapkToken(input)
                    dialog = null
                    refresh()
                    onToast("已保存")
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (dialog == "github") prefs.setGithubToken("") else prefs.setCoolapkToken("")
                    dialog = null
                    refresh()
                    onToast("已清除")
                }) { Text("清除") }
            }
        )

        "repos" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("自定义仓库地址") },
            text = {
                Column {
                    Text(
                        "每行一个 F-Droid 兼容仓库的 repo 目录地址，例如 https://example.com/fdroid/repo",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        minLines = 4,
                        label = { Text("仓库地址", fontSize = 12.sp) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.setCustomRepos(input.split("\n"))
                    dialog = null
                    refresh()
                    onToast("已保存 ${prefs.customRepos().size} 个仓库")
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = 14.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onClick) {
                Text(value, fontSize = 12.sp, maxLines = 1)
            }
        }
        ThinDivider()
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
        ThinDivider()
    }
}

private fun mask(token: String): String =
    if (token.length <= 8) "已设置" else token.take(4) + "…" + token.takeLast(4)