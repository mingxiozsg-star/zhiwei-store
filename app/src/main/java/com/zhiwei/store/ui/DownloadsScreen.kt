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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiwei.store.core.formatSize
import com.zhiwei.store.download.Downloader
import com.zhiwei.store.model.DownloadStatus
import com.zhiwei.store.model.DownloadTask

/**
 * 下载管理：任务队列、实时进度、取消/重试/安装/清理。
 */
@Composable
fun DownloadsScreen(
    downloader: Downloader,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    val tasks = downloader.tasks
    val running = tasks.count { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.WAITING }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("下载", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(
                    text = if (tasks.isEmpty()) "暂无下载任务" else "共 ${tasks.size} 个任务 · 进行中 $running",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = {
                val done = tasks.filter { it.status == DownloadStatus.DONE }
                if (done.isEmpty()) {
                    onToast("没有已完成的记录可清理")
                } else {
                    done.forEach { downloader.remove(it.id) }
                    onToast("已清理 ${done.size} 条完成记录（文件保留）")
                }
            }) {
                Text("清理记录", fontSize = 12.sp)
            }
        }
        ThinDivider()

        if (tasks.isEmpty()) {
            EmptyBlock(
                title = "还没有下载任务",
                detail = "在详情页点「下载安装」，文件会保存在 应用专属目录/downloads"
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(tasks, key = { it.id }) { task ->
                    DownloadRow(
                        task = task,
                        onCancel = { downloader.cancel(task.id) },
                        onRetry = { downloader.retry(task.id) },
                        onInstall = {
                            if (!downloader.completedFile(task).exists()) {
                                onToast("文件不存在，请重新下载")
                            } else {
                                downloader.install(task)
                            }
                        },
                        onRemove = { downloader.remove(task.id) },
                        onOpen = {
                            val f = downloader.completedFile(task)
                            if (f.exists()) {
                                com.zhiwei.store.download.AppInstaller.install(context, f)
                            } else {
                                onToast("文件不存在")
                            }
                        }
                    )
                    ThinDivider()
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    task: DownloadTask,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${task.sourceName} · ${task.fileName}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusBadge(task)
        }

        if (task.status == DownloadStatus.RUNNING || task.status == DownloadStatus.PAUSED) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { task.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    append(formatSize(task.doneBytes))
                    if (task.totalBytes > 0) {
                        append(" / ")
                        append(formatSize(task.totalBytes))
                        append("  ")
                        append((task.progress * 100).toInt())
                        append("%")
                    }
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        task.error?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            when (task.status) {
                DownloadStatus.RUNNING, DownloadStatus.WAITING, DownloadStatus.PAUSED -> {
                    TextButton(onClick = onCancel) { Text("取消", fontSize = 12.sp) }
                }
                DownloadStatus.FAILED, DownloadStatus.CANCELED -> {
                    TextButton(onClick = onRetry) { Text("重试", fontSize = 12.sp) }
                    TextButton(onClick = onRemove) { Text("移除", fontSize = 12.sp) }
                }
                DownloadStatus.DONE -> {
                    TextButton(onClick = onInstall) { Text("安装", fontSize = 12.sp) }
                    TextButton(onClick = onOpen) { Text("用其他方式打开", fontSize = 12.sp) }
                    TextButton(onClick = onRemove) { Text("移除记录", fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(task: DownloadTask) {
    val (text, color) = when (task.status) {
        DownloadStatus.WAITING -> "排队中" to MaterialTheme.colorScheme.onSurfaceVariant
        DownloadStatus.RUNNING -> "下载中" to MaterialTheme.colorScheme.primary
        DownloadStatus.PAUSED -> "已暂停" to MaterialTheme.colorScheme.tertiary
        DownloadStatus.DONE -> "已完成" to MaterialTheme.colorScheme.primary
        DownloadStatus.FAILED -> "失败" to MaterialTheme.colorScheme.error
        DownloadStatus.CANCELED -> "已取消" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(text, fontSize = 10.sp, color = color)
    }
}
