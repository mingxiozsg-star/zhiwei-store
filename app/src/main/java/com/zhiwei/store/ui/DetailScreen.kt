package com.zhiwei.store.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiwei.store.core.formatSize
import com.zhiwei.store.core.relativeTime
import com.zhiwei.store.data.StoreRepository
import com.zhiwei.store.download.Downloader
import com.zhiwei.store.model.AppDetail
import com.zhiwei.store.model.AppItem
import com.zhiwei.store.model.DownloadOption
import com.zhiwei.store.util.InstalledApps

@Composable
fun DetailScreen(
    repo: StoreRepository,
    downloader: Downloader,
    item: AppItem,
    onBack: () -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    var detail by remember(item) { mutableStateOf<AppDetail?>(null) }
    var loading by remember(item) { mutableStateOf(true) }
    var error by remember(item) { mutableStateOf<String?>(null) }
    var expanded by remember(item) { mutableStateOf(false) }
    var installedCode by remember(item) { mutableStateOf(InstalledApps.versionCode(context, item.packageName)) }

    LaunchedEffect(item) {
        loading = true
        error = null
        runCatching { repo.detail(item) }
            .onSuccess { detail = it }
            .onFailure {
                error = it.message
                detail = AppDetail(item, description = item.summary)
            }
        loading = false
    }

    fun openUrl(url: String?) {
        if (url.isNullOrBlank()) return
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    val data = detail ?: AppDetail(item, description = item.summary)
    val app = data.item
    val installed = installedCode > 0
    val hasUpdate = installed && app.versionCode > 0 && app.versionCode > installedCode

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "←",
                fontSize = 22.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Text("应用详情", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }

        LazyColumn {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    RemoteIcon(url = app.iconUrl, fallback = app.name, size = 72.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            app.developer ?: app.packageName ?: "未知开发者",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SourceChip(app.sourceName)
                            Spacer(Modifier.width(6.dp))
                            app.versionName?.let { TagChip("v$it") }
                            if (app.rating > 0) {
                                Spacer(Modifier.width(6.dp))
                                TagChip("★ ${"%.1f".format(app.rating)}")
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val primary = data.options.firstOrNull() ?: item.downloadUrl?.let {
                        DownloadOption(label = "直接下载", url = it, versionName = item.versionName)
                    }
                    Button(
                        onClick = {
                            if (primary == null) {
                                onToast("该源没有可用的下载直链，可点「打开来源页」")
                            } else {
                                downloader.download(
                                    name = app.name,
                                    packageName = app.packageName,
                                    sourceName = app.sourceName,
                                    url = primary.url,
                                    versionName = primary.versionName ?: app.versionName
                                )
                                onToast("已加入下载：${app.name}")
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            when {
                                !installed -> "下载安装"
                                hasUpdate -> "更新到 v${app.versionName ?: "最新"}"
                                else -> "重新下载"
                            },
                            fontSize = 14.sp
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    OutlinedButton(
                        onClick = { openUrl(data.homepage ?: data.sourceUrl ?: app.detailUrl) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("来源页", fontSize = 13.sp)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val status = when {
                        hasUpdate -> "已安装 v$installedCode，可更新到 v${app.versionCode}"
                        installed -> "已安装（本机版本 $installedCode）"
                        else -> "未安装"
                    }
                    Text(
                        status,
                        fontSize = 11.sp,
                        color = if (hasUpdate) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    if (installed) {
                        Text(
                            "刷新",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    InstalledApps.refresh(app.packageName)
                                    installedCode = InstalledApps.versionCode(context, app.packageName)
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (loading) {
                item { LoadingBlock("读取详情…") }
            }
            error?.let {
                item {
                    Text(
                        "详情接口异常：$it（已展示已知信息）",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InfoPill("版本", app.versionName ?: "—")
                    InfoPill("大小", if (app.sizeBytes > 0) formatSize(app.sizeBytes) else "—")
                    InfoPill("更新", if (app.updatedAt > 0) relativeTime(app.updatedAt) else "—")
                }
            }

            if (data.options.isNotEmpty()) {
                item { SectionHeader("下载线路", "共 ${data.options.size} 条，可换源") }
                items(data.options) { opt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(opt.label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(2.dp))
                            val meta = buildList {
                                opt.versionName?.let { add("v$it") }
                                opt.arch?.let { add(it) }
                                if (opt.sizeBytes > 0) add(formatSize(opt.sizeBytes))
                                add(opt.format)
                            }
                            Text(
                                meta.joinToString(" · "),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = {
                            downloader.download(
                                name = app.name,
                                packageName = app.packageName,
                                sourceName = app.sourceName,
                                url = opt.url,
                                versionName = opt.versionName ?: app.versionName
                            )
                            onToast("已加入下载")
                        }) {
                            Text("下载", fontSize = 13.sp)
                        }
                    }
                }
            }

            item {
                SectionHeader("应用简介")
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    val text = data.description.ifBlank { app.summary.ifBlank { "暂无简介" } }
                    Text(
                        text = if (expanded) text else text.take(500) + if (text.length > 500) "…" else "",
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                    if (text.length > 500) {
                        Text(
                            if (expanded) "收起" else "展开全文",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { expanded = !expanded }
                                .padding(vertical = 6.dp)
                        )
                    }
                }
            }

            if (data.screenshots.isNotEmpty()) {
                item { SectionHeader("截图") }
                item {
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(data.screenshots) { shot ->
                            RemoteIcon(url = shot, fallback = "图", size = 150.dp)
                        }
                    }
                }
            }

            if (data.extra.isNotEmpty() || !data.license.isNullOrBlank() || !data.homepage.isNullOrBlank()) {
                item { SectionHeader("详细信息") }
                items(
                    buildList {
                        app.packageName?.let { add("包名" to it) }
                        if (!data.license.isNullOrBlank()) add("许可证" to data.license!!)
                        addAll(data.extra)
                    }
                ) { (k, v) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 3.dp)
                    ) {
                        Text(
                            k,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(76.dp)
                        )
                        Text(v, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    }
                }
            }

            item {
                Text(
                    "下载的 APK 保存在应用私有目录，安装时由系统安装器接管；请自行确认为可信来源。",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun InfoPill(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}