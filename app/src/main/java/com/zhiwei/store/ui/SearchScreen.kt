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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiwei.store.data.StoreRepository
import com.zhiwei.store.model.AppItem
import com.zhiwei.store.model.MergedApp
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    repo: StoreRepository,
    seed: String,
    onOpen: (AppItem) -> Unit
) {
    var query by remember { mutableStateOf(seed) }
    var merged by remember { mutableStateOf<List<MergedApp>>(emptyList()) }
    var outcomes by remember { mutableStateOf<List<StoreRepository.Outcome>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun runSearch(text: String) {
        val q = text.trim()
        if (q.isEmpty()) return
        scope.launch {
            loading = true
            hint = null
            searched = true
            val result = runCatching { repo.searchAll(q) }
            result.onFailure { hint = "搜索出错：${it.message}" }
            val list = result.getOrDefault(emptyList())
            outcomes = list
            merged = repo.mergeAll(list, q)
            if (list.isEmpty()) hint = "没有启用的商店源，去「我的 → 商店源」打开几个"
            else if (merged.isEmpty()) hint = "所有源都没有匹配结果，试试包名或英文名"
            loading = false
        }
    }

    LaunchedEffect(seed) {
        if (seed.isNotBlank()) {
            query = seed
            runSearch(seed)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("应用名 / 包名 / 项目名", fontSize = 13.sp) },
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch(query) })
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(enabled = !loading) { runSearch(query) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    if (loading) "…" else "搜索",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (outcomes.isNotEmpty()) {
            val ok = outcomes.count { it.error == null && it.items.isNotEmpty() }
            val failed = outcomes.filter { it.error != null }
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item { Text("$ok 个源命中", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(outcomes) { o ->
                    val label = if (o.error != null) "${o.sourceName} ✕" else "${o.sourceName} ${o.items.size}"
                    SourceChip(label)
                }
            }
            if (failed.isNotEmpty()) {
                Text(
                    text = "未返回：" + failed.joinToString("；") { "${it.sourceName}（${it.error}）" },
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }

        when {
            loading -> LoadingBlock("正在并发查询 ${repo.registry.enabled().size} 个源…")
            hint != null -> EmptyBlock(hint ?: "", null)
            !searched -> EmptyBlock(
                "输入关键词开始搜索",
                "一次查询 F-Droid / GitHub / Aptoide / APKPure / 酷安 / 应用宝 等源"
            )
            else -> LazyColumn {
                items(merged, key = { it.key }) { m ->
                    AppRow(
                        app = m.primary,
                        onClick = { onOpen(m.primary) },
                        trailing = {
                            if (m.sourceCount > 1) {
                                TagChip("${m.sourceCount} 个源")
                            }
                        }
                    )
                    if (m.others.isNotEmpty()) {
                        Text(
                            text = "其他源：" + m.others.joinToString(" / ") { it.sourceName },
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 78.dp, bottom = 6.dp)
                        )
                    }
                    ThinDivider()
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}