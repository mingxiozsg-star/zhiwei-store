package com.zhiwei.store.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiwei.store.core.Http
import com.zhiwei.store.core.formatSize
import com.zhiwei.store.core.relativeTime
import com.zhiwei.store.model.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 图标内存缓存，避免每次滚动都重新请求 */
object AppIconCache {
    private val cache = LruCache<String, Bitmap>(160)

    fun get(url: String?): Bitmap? = url?.let { cache.get(it) }

    suspend fun load(url: String): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(url) ?: runCatching {
            val (code, bytes) = Http.getBytes(url, timeoutMs = 15000)
            if (code in 200..299 && bytes.isNotEmpty()) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { cache.put(url, it) }
            } else null
        }.getOrNull()
    }
}

@Composable
fun RemoteIcon(
    url: String?,
    fallback: String,
    size: Dp = 52.dp,
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<Bitmap?>(initialValue = AppIconCache.get(url), url) {
        if (value == null && !url.isNullOrBlank()) {
            value = AppIconCache.load(url)
        }
    }
    val shape = RoundedCornerShape(size / 4)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = fallback.take(1).ifBlank { "?" },
                color = MaterialTheme.colorScheme.primary,
                fontSize = (size.value / 2.4f).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SourceChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
fun TagChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
fun AppRow(
    app: AppItem,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RemoteIcon(url = app.iconUrl, fallback = app.name, size = 56.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = app.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (app.rating > 0) {
                    Text(
                        text = "★" + "%.1f".format(app.rating),
                        fontSize = 11.sp,
                        color = Color(0xFFFFA000)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                val installs = formatInstalls(app.downloads)
                Text(
                    text = installs.ifBlank {
                        buildString {
                            app.versionName?.let { append("v$it") }
                            if (app.sizeBytes > 0) {
                                if (isNotEmpty()) append(" · ")
                                append(formatSize(app.sizeBytes))
                            }
                        }.ifBlank { app.packageName ?: "" }
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (app.summary.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = app.summary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.let {
            Spacer(Modifier.width(10.dp))
            it()
        }
    }
}

/** 下载量格式化：1.5亿次 / 78万次 */
fun formatInstalls(n: Long): String = when {
    n >= 100_000_000L -> "%.1f亿次".format(n / 100_000_000.0)
    n >= 10_000L -> "%.0f万次".format(n / 10_000.0)
    n > 0 -> "$n 次"
    else -> ""
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        action?.invoke()
    }
}

@Composable
fun LoadingBlock(text: String = "加载中…", modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyBlock(title: String, detail: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("…", fontSize = 26.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                detail,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ThinDivider(color: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.6.dp)
            .background(color)
    )
}