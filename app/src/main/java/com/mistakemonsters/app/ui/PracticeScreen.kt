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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistakemonsters.app.App
import com.mistakemonsters.app.data.Cause
import com.mistakemonsters.app.data.PracticeSet
import com.mistakemonsters.app.logic.WorksheetPdf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ===== 同类题练习卷：生成 / 预览 / PDF 导出 / 历史 =====

@Composable
fun PracticeScreen(app: App, presetQuestionIds: List<Long>?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var causes by remember { mutableStateOf<List<Cause>>(emptyList()) }
    var causeId by remember { mutableStateOf<Long?>(app.lastCauseId) }
    var count by remember { mutableStateOf(6) }
    var ramp by remember { mutableStateOf(true) }
    var generating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var current by remember { mutableStateOf<PracticeSet?>(null) }
    var showAnswers by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<PracticeSet>>(emptyList()) }
    var toast by remember { mutableStateOf<String?>(null) }

    fun loadHistory() { history = app.db.listPracticeSets() }
    LaunchedEffect(Unit) {
        causes = app.db.listCauses()
        loadHistory()
        if (app.lastCauseId != null) causeId = app.lastCauseId
    }

    LazyColumn(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("📝 同类题练习卷", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        item {
            MsCard {
                if (!presetQuestionIds.isNullOrEmpty()) {
                    Text("将围绕错题 #${presetQuestionIds.joinToString(",")} 生成同类题。", color = C.InkSoft, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                } else {
                    DropdownPicker(
                        label = if (causeId == null) "自动选择（最近出错的题）"
                        else "针对：" + (causes.find { it.id == causeId }?.name ?: "错因"),
                        options = causes.filter { it.count > 0 }.map { "${it.name}（已出现 ${it.count} 次）" },
                        selectedIndex = causes.filter { it.count > 0 }.indexOfFirst { it.id == causeId }.takeIf { it >= 0 },
                        onSelect = { i -> causeId = if (i == null) null else causes.filter { it.count > 0 }[i].id },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("题量：", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    listOf(5, 6, 8, 10).forEach { n ->
                        Text(
                            "$n",
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (count == n) C.Primary else Color(0xFFF0EDE8))
                                .clickable { count = n }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            color = if (count == n) Color.White else C.InkSoft,
                            fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.padding(2.dp))
                    Text("变式提升", fontSize = 12.sp, color = C.InkSoft)
                    Switch(checked = ramp, onCheckedChange = { ramp = it }, colors = SwitchDefaults.colors(checkedTrackColor = C.Primary))
                }
                Spacer(Modifier.height(10.dp))
                MsButton(if (generating) "AI 出题中…" else "🪄 生成练习卷", {
                    error = null
                    generating = true
                    showAnswers = false
                    scope.launch {
                        try {
                            val set = withContext(Dispatchers.IO) {
                                app.pipeline.generateSimilar(
                                    causeId = if (presetQuestionIds.isNullOrEmpty()) causeId else null,
                                    questionIds = presetQuestionIds,
                                    count = count,
                                    ramp = ramp,
                                )
                            }
                            current = set
                            loadHistory()
                        } catch (e: Exception) {
                            error = e.message ?: "生成失败"
                        } finally {
                            generating = false
                        }
                    }
                }, enabled = !generating)
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    ErrorBox(it) { error = null }
                }
                toast?.let {
                    Spacer(Modifier.height(8.dp))
                    Text("✅ $toast", color = C.Mint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (generating) {
            item {
                MsCard {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                        CircularProgressIndicator(Modifier.padding(8.dp), color = C.Primary)
                        Text("AI 正在根据错因和你的画像出题，约需十几秒…", color = C.InkSoft, fontSize = 13.sp)
                    }
                }
            }
        }

        current?.let { set ->
            item {
                MsCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(set.title, fontWeight = FontWeight.Bold)
                            Text("${set.items.size} 题 · 生成于 ${set.createdAt}", color = C.InkSoft, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MsButton("⬇️ 下载 PDF（含答案页）", {
                            exportPdf(app, set, withAnswers = true) { toast = it }
                        })
                        GhostButton("仅题目卷", {
                            exportPdf(app, set, withAnswers = false) { toast = it }
                        })
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            if (showAnswers) "🙈 收起答案" else "👀 先看看都考什么（显示答案）",
                            color = C.Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { showAnswers = !showAnswers },
                        )
                    }
                    set.items.forEachIndexed { i, it ->
                        Column(Modifier.fillMaxWidth().background(Color(0xFFF8F6FD), RoundedCornerShape(14.dp)).padding(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Stars(it.difficulty)
                                if (it.variant == "变式") MsChip("变式", C.AccentSoft, C.Accent)
                                if (it.targetedCause.isNotBlank()) MsChip("针对：${it.targetedCause}", C.RoseSoft, C.Rose)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("${i + 1}. ${it.stem}", fontSize = 13.sp, lineHeight = 19.sp)
                            if (showAnswers) {
                                Spacer(Modifier.height(6.dp))
                                Text("答案：${it.answer}　解析：${it.solution}", color = C.Mint, fontSize = 12.sp, lineHeight = 17.sp)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }

        if (history.isNotEmpty()) {
            item { Text("🕘 历史练习卷", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            items(history.size) { i ->
                val s = history[i]
                MsCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${s.createdAt} · ${s.items.size} 题", color = C.InkSoft, fontSize = 11.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("下载 PDF", color = C.Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { exportPdf(app, s, withAnswers = true) { toast = it } })
                            Text("预览", color = C.Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { current = s; showAnswers = false })
                        }
                    }
                }
            }
        } else if (current == null && !generating) {
            item {
                MsCard {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
                        Text("🪄", fontSize = 36.sp)
                        Text("还没有练习卷", fontWeight = FontWeight.Bold)
                        Text("从上方选择错因生成，或到错题本勾选错题批量生成", color = C.InkSoft, fontSize = 13.sp)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

private fun exportPdf(app: App, set: PracticeSet, withAnswers: Boolean, onDone: (String) -> Unit) {
    val context = app
    val profile = app.prefs.profile()
    val fileName = "同类题练习-${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}${if (withAnswers) "" else "-无答案"}.pdf"
    Thread {
        try {
            val doc = WorksheetPdf.build(set.items, profile, set.title, withAnswers)
            val msg = WorksheetPdf.save(context, doc, fileName)
            onDone(msg)
        } catch (e: Exception) {
            onDone("导出失败：${e.message}")
        }
    }.start()
}
