package com.mistakemonsters.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistakemonsters.app.App
import com.mistakemonsters.app.data.Question
import com.mistakemonsters.app.data.Taxonomy

// ===== 错题本列表 =====

@Composable
fun QuestionsScreen(
    app: App,
    onOpenQuestion: (Long) -> Unit,
    onPractice: (List<Long>) -> Unit,
    onCapture: () -> Unit,
) {
    var status by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<Question>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }

    LaunchedEffect(status, category, query) {
        items = app.db.listQuestions(status = status, category = category, q = query.ifBlank { null })
    }

    LazyColumn(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("📒 错题本", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("共 ${items.size} 只小怪兽", color = C.InkSoft, fontSize = 12.sp)
            }
        }
        item {
            // 状态筛选
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(null, "全部", status == null) { status = null }
                Taxonomy.STATUS_LIST.forEach { s ->
                    FilterChip(s + "_chip", s, status == s) { status = if (status == s) null else s }
                }
            }
        }
        item {
            // 题型下拉 + 搜索
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DropdownPicker(
                    label = if (category == null) "全部题型" else category!!,
                    options = Taxonomy.CATEGORIES.map { it.emoji + " " + it.key },
                    selectedIndex = Taxonomy.CATEGORIES.indexOfFirst { it.key == category }.takeIf { it >= 0 },
                    onSelect = { i -> category = if (i == null) null else Taxonomy.CATEGORIES[i].key },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("🔍 搜关键词", fontSize = 12.sp) },
                    modifier = Modifier.weight(1.2f).height(52.dp),
                    singleLine = true,
                    colors = colorsLite(),
                )
            }
        }
        if (items.isEmpty()) {
            item {
                MsCard {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp)) {
                        Text("🌈", fontSize = 40.sp)
                        Text(if (status != null || category != null || query.isNotBlank()) "没有符合条件的错题" else "错题本空空如也", fontWeight = FontWeight.Bold)
                        Text("收录第一道错题，让 AI 开始了解你", color = C.InkSoft, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        MsButton("去收录", { onCapture() })
                    }
                }
            }
        } else {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = selected.size == items.size && items.isNotEmpty(), onCheckedChange = { on ->
                        selected = if (on) items.map { it.id }.toSet() else emptySet()
                    })
                    Text("全选（用于批量生成练习卷）", fontSize = 11.sp, color = C.InkSoft)
                }
            }
            items(items.size) { i ->
                val q = items[i]
                MsCard(onClick = {
                    onOpenQuestion(q.id)
                }) {
                    Row {
                        Checkbox(
                            checked = q.id in selected,
                            onCheckedChange = { on -> selected = if (on) selected + q.id else selected - q.id },
                        )
                        Column(Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                MsChip("${Taxonomy.emojiOf(q.category)} ${q.category}", C.PrimarySoft, C.PrimaryDeep)
                                when (q.status) {
                                    "待订正" -> MsChip("🔥 待订正", C.RoseSoft, C.Rose)
                                    "已订正" -> MsChip("✅ 已订正", C.AccentSoft, C.Accent)
                                    "已掌握" -> MsChip("⭐ 已收服", C.MintSoft, C.Mint)
                                }
                                Stars(q.difficulty)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(q.questionText, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${q.createdAt.take(10)} · 知识点：${q.knowledgeTags.joinToString("、").ifBlank { "—" }}",
                                color = C.InkSoft, fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
            if (selected.isNotEmpty()) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(C.PrimaryDeep)
                            .clickable { onPractice(selected.toList()) }
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text("📝 生成同类题卷（已选 ${selected.size} 道）", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
fun FilterChip(key: String?, text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text,
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) C.Primary else Color(0xFFF0EDE8))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        color = if (active) Color.White else C.InkSoft,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
fun DropdownPicker(label: String, options: List<String>, selectedIndex: Int?, onSelect: (Int?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    androidx.compose.material3.Card(
        modifier = modifier.clickable { open = true },
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0EDE8)),
    ) {
        Text(
            "▾ $label",
            Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            fontSize = 12.sp, color = C.Ink,
        )
    }
    if (open) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                Text("清除", color = C.InkSoft, modifier = Modifier
                    .clickable { open = false; onSelect(null) })
            },
            dismissButton = { Text("关闭", color = C.Primary, modifier = Modifier.clickable { open = false }) },
            title = { Text("选择题型", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    options.forEachIndexed { i, o ->
                        Text(
                            o,
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { open = false; onSelect(i) }
                                .padding(10.dp),
                            color = if (selectedIndex == i) C.PrimaryDeep else C.Ink,
                            fontWeight = if (selectedIndex == i) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            },
        )
    }
}

// OutlinedTextField 颜色的小封装（避免到处写）
@Composable
fun colorsLite() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedBorderColor = C.Primary,
    unfocusedBorderColor = Color(0xFFF0EDE8),
)
