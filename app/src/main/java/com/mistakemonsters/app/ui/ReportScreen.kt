package com.mistakemonsters.app.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistakemonsters.app.App
import com.mistakemonsters.app.data.ReportData
import kotlinx.coroutines.launch

// ===== 错因画像 · 长期记忆 =====

@Composable
fun ReportScreen(app: App, onPracticeForCause: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<ReportData?>(null) }
    var regenerating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var newMemory by remember { mutableStateOf("") }

    fun load() { data = app.pipeline.report() }
    LaunchedEffect(Unit) { load() }

    LazyColumn(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("🧠 错因画像 · 长期记忆", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        item {
            // AI 学生画像
            val p = app.prefs.profile()
            MsCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("📜 AI 学生画像", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    GhostButton(if (regenerating) "刷新中…" else "🔄 重新归纳", {
                        error = null
                        regenerating = true
                        scope.launch {
                            try {
                                app.pipeline.regenDigest()
                                load()
                            } catch (e: Exception) {
                                error = e.message ?: "刷新失败"
                            } finally {
                                regenerating = false
                            }
                        }
                    }, enabled = !regenerating)
                }
                Spacer(Modifier.height(6.dp))
                if (p.memoDigest.isNotBlank()) {
                    Text(p.memoDigest, fontSize = 13.sp, lineHeight = 20.sp)
                } else {
                    Text(
                        "收录错题后，AI 会自动归纳出孩子的知识薄弱点、反复出现的错因模式和学习习惯，并注入后续所有辅导环节。" +
                            if ((data?.counts?.total ?: 0) > 0) " 点击「重新归纳」立即生成。" else "",
                        color = C.InkSoft, fontSize = 13.sp, lineHeight = 19.sp,
                    )
                }
                p.digestUpdatedAt?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("上次更新：$it（每收录 8 道错题自动更新）", color = C.InkSoft, fontSize = 11.sp)
                }
            }
        }
        error?.let { item { ErrorBox(it) { error = null } } }

        val d = data
        if (d == null) {
            item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                }
            }
            return@LazyColumn
        }
        val activeCauses = d.causes.filter { it.count > 0 }
        val maxCount = (activeCauses.firstOrNull()?.count ?: 1).coerceAtLeast(1)

        item {
            // 错因统计
            MsCard {
                Text("📊 错因统计", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (activeCauses.isEmpty()) {
                    Text("还没有数据，去收录错题吧", color = C.InkSoft, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    activeCauses.forEach { c ->
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MsChip(c.category, causeColor(c.category).copy(alpha = 0.15f), causeColor(c.category))
                                Spacer(Modifier.padding(2.dp))
                                Text(c.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Text("×${c.count}" + (c.lastSeenAt?.let { " · 最近 ${it.substring(5, 10)}" } ?: ""), color = C.InkSoft, fontSize = 11.sp)
                        }
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)).background(Color(0xFFF0EDE8))) {
                            Box(
                                Modifier.fillMaxWidth(c.count / maxCount.toFloat()).height(8.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Brush.horizontalGradient(listOf(C.Primary, C.Accent))),
                            )
                        }
                        if (c.strategy.isNotBlank()) {
                            Text("💡 ${c.strategy}", color = C.InkSoft, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                        Text(
                            "📝 针对此错因出练习卷 →",
                            color = C.Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onPracticeForCause(c.id) },
                        )
                    }
                }
            }
        }
        item {
            // 薄弱知识点
            MsCard {
                Text("🎯 薄弱知识点 Top 8", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (d.weakTags.isEmpty()) {
                    Text("暂无数据", color = C.InkSoft, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp))
                } else {
                    Spacer(Modifier.height(6.dp))
                    d.weakTags.forEach { (tag, count) ->
                        Text(
                            "$tag ×$count",
                            Modifier
                                .padding(bottom = 6.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(C.AccentSoft)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            color = C.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        item {
            // 长期记忆库
            MsCard {
                Text("💾 长期记忆库", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("AI 沉淀的规律 + 家长的观察，都会注入每一次 AI 辅导", color = C.InkSoft, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                if (d.memories.isEmpty()) Text("暂无记忆条目", color = C.InkSoft, fontSize = 13.sp)
                d.memories.forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (m.kind == "pattern") "AI 发现" else "家长记录",
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (m.kind == "pattern") C.SkySoft else C.MintSoft)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            color = if (m.kind == "pattern") C.Sky else C.Mint,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.padding(3.dp))
                        Text(m.content, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                        Text("✕", color = C.Rose, fontSize = 12.sp, modifier = Modifier.clickable {
                            app.db.deleteMemory(m.id); load()
                        })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newMemory,
                        onValueChange = { newMemory = it },
                        placeholder = { Text("记录你的观察，如学习习惯、辅导约定", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        colors = colorsLite(),
                    )
                    MsButton("添加", {
                        app.db.insertMemory(newMemory.trim(), "fact", 5)
                        newMemory = ""
                        load()
                    }, enabled = newMemory.isNotBlank())
                }
            }
        }
        item {
            Spacer(Modifier.height(20.dp))
        }
    }
}
