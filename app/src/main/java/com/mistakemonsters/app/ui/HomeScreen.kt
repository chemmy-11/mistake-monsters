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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import com.mistakemonsters.app.App
import com.mistakemonsters.app.data.ReportData
import com.mistakemonsters.app.data.Taxonomy

// ===== 首页 =====

@Composable
fun HomeScreen(
    app: App,
    onCapture: () -> Unit,
    onOpenQuestion: (Long) -> Unit,
    onOpenReport: () -> Unit,
    onOpenPractice: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenQuestions: () -> Unit,
) {
    var data by remember { mutableStateOf<ReportData?>(null) }
    LaunchedEffect(Unit) { data = app.pipeline.report() }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (data == null) {
            Text("加载中…", color = C.InkSoft, modifier = Modifier.padding(32.dp))
            return@Column
        }
        val d = data!!

        // 欢迎横幅
        val profile = app.prefs.profile()
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(C.Primary, C.PrimaryDeep)))
                .padding(20.dp),
        ) {
            Column {
                Text("${profile.avatar} ${profile.name}同学 · ${profile.grade} 年级", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        d.counts.total == 0 -> "欢迎来到错题收容所！"
                        d.counts.pending > 0 -> "还有 ${d.counts.pending} 只小怪兽等你收服 👾"
                        else -> "太棒了，怪兽都被收服啦 ⭐"
                    },
                    color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp,
                )
                Text("拍下写错的题，AI 帮你找出错因、出同类题巩固", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White)
                        .clickable { onCapture() }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text("📸 拍错题，收录它", color = C.PrimaryDeep, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // 数据卡
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("👾", d.counts.total, "累计错题", C.PrimarySoft, Modifier.weight(1f))
            StatCard("🔥", d.counts.pending, "待订正", C.AccentSoft, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("⭐", d.counts.mastered, "已收服", C.MintSoft, Modifier.weight(1f))
            StatCard("📝", d.counts.sets, "练习卷", C.SkySoft, Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // 常见错因
            MsCard(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("🧠 最常出错的错因", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("画像 →", color = C.Primary, fontSize = 12.sp, modifier = Modifier.clickable { onOpenReport() })
                }
                val top = d.causes.filter { it.count > 0 }.take(3)
                if (top.isEmpty()) {
                    Text("收录错题后，AI 会自动归纳你的错因 🧐", color = C.InkSoft, fontSize = 13.sp, modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    val max = top[0].count.coerceAtLeast(1)
                    top.forEach { c ->
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MsChip(c.category, causeColor(c.category).copy(alpha = 0.15f), causeColor(c.category))
                                Spacer(Modifier.padding(2.dp))
                                Text(c.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Text("×${c.count}", color = C.InkSoft, fontSize = 12.sp)
                        }
                        Box(
                            Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)).background(Color(0xFFF0EDE8)),
                        ) {
                            Box(
                                Modifier.fillMaxWidth(c.count / max.toFloat()).height(8.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Brush.horizontalGradient(listOf(C.Accent, C.Sun))),
                            )
                        }
                    }
                }
            }
        }

        // 最近错题
        MsCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("👾 最近的错题", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("全部错题 →", color = C.Primary, fontSize = 12.sp, modifier = Modifier.clickable { onOpenQuestions() })
            }
            if (d.recent.isEmpty()) {
                Text("还没有错题，拍下第一道吧 📷", color = C.InkSoft, fontSize = 13.sp, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                d.recent.take(3).forEach { q ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFF7F4FE))
                            .clickable { onOpenQuestion(q.id) }
                            .padding(10.dp),
                    ) {
                        Column {
                            Text(
                                "${Taxonomy.emojiOf(q.category)} ${q.category} · " +
                                    when (q.status) {
                                        "已掌握" -> "⭐ 已收服"
                                        "已订正" -> "✅ 已订正"
                                        else -> "🔥 待订正"
                                    },
                                color = C.InkSoft, fontSize = 11.sp,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(q.questionText, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2)
                        }
                    }
                }
            }
        }

        // 功能导览
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NavCard("📝", "同类题卷", Modifier.weight(1f), onOpenPractice)
            NavCard("🧠", "错因画像", Modifier.weight(1f), onOpenReport)
            NavCard("⚙️", "AI 设置", Modifier.weight(1f), onOpenSettings)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatCard(emoji: String, value: Int, label: String, bg: Color, modifier: Modifier = Modifier) {
    MsCard(modifier = modifier, bg = bg) {
        Text(emoji, fontSize = 20.sp)
        Text("$value", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, fontSize = 11.sp, color = C.InkSoft)
    }
}

@Composable
private fun NavCard(emoji: String, title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    MsCard(modifier = modifier, onClick = onClick) {
        Text(emoji, fontSize = 20.sp)
        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
