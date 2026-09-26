package com.mistakemonsters.app.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistakemonsters.app.App
import com.mistakemonsters.app.data.Cause
import com.mistakemonsters.app.data.Question
import com.mistakemonsters.app.data.Taxonomy
import java.io.File
import kotlinx.coroutines.launch

// ===== 错题详情：题目 / 解析门 / 错因 / 提示链 / AI 小老师 =====

@Composable
fun QuestionDetailScreen(
    app: App,
    questionId: Long,
    onClose: () -> Unit,
    onPractice: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf<Question?>(null) }
    var causes by remember { mutableStateOf<List<Cause>>(emptyList()) }
    var revealed by remember { mutableStateOf(false) }
    var hintLevel by remember { mutableStateOf(0) }
    var editCauses by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var chat by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var chatInput by remember { mutableStateOf("") }
    var chatting by remember { mutableStateOf(false) }

    fun reload() {
        q = app.db.getQuestion(questionId)
    }
    LaunchedEffect(questionId) {
        reload()
        causes = app.db.listCauses()
    }

    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("← 返回", color = C.InkSoft, fontSize = 13.sp, modifier = Modifier.clickable { onClose() })
            Text("🗑 删除", color = C.Rose, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                app.db.deleteQuestion(questionId)
                onClose()
            })
        }
        error?.let { ErrorBox(it) { error = null } }

        val question = q
        if (question == null) {
            Text("加载中…", color = C.InkSoft)
            return@Column
        }

        // 题目卡
        MsCard {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                MsChip("${Taxonomy.emojiOf(question.category)} ${question.category}", C.PrimarySoft, C.PrimaryDeep)
                if (question.subtype.isNotBlank()) MsChip(question.subtype, C.SkySoft, C.Sky)
                Stars(question.difficulty)
            }
            Spacer(Modifier.height(10.dp))
            Text(question.questionText, fontSize = 15.sp, lineHeight = 22.sp)
            if (question.knowledgeTags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    question.knowledgeTags.forEach { MsChip(it, C.MintSoft, C.Mint) }
                }
            }
            question.imagePath?.let { p ->
                val f = File(File(app.filesDir, "uploads"), p)
                if (f.exists()) {
                    val bmp = remember(p) { android.graphics.BitmapFactory.decodeFile(f.absolutePath) }
                    if (bmp != null) {
                        Spacer(Modifier.height(10.dp))
                        Image(
                            bmp.asImageBitmap(), null,
                            Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }

        // 我的答案
        MsCard {
            Text("✏️ 我的答案", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().background(C.RoseSoft, RoundedCornerShape(14.dp)).padding(12.dp)) {
                Text(question.myAnswer.ifBlank { "（空白 / 没写）" }, fontSize = 13.sp)
            }
        }

        // 解析门
        if (!revealed) {
            MsCard(bg = C.SunSoft) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("🙈", fontSize = 36.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("先别急着看答案！", fontWeight = FontWeight.Bold)
                    Text("自己重新做一遍这道题，或者先跟 AI 小老师聊几句。想好了再核对。", color = C.InkSoft, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    GhostButton("我订正好了，看正确答案与解析", { revealed = true })
                }
            }
        } else {
            MsCard {
                Text("✅ 正确答案", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Box(Modifier.fillMaxWidth().background(C.MintSoft, RoundedCornerShape(14.dp)).padding(12.dp)) {
                    Text(question.correctAnswer.ifBlank { "（AI 未给出）" }, color = C.Mint, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text("💡 解析", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(question.analysis.ifBlank { "（无解析）" }, color = C.InkSoft, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }

        // 错因
        MsCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("🧠 错因判断", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(if (editCauses) "完成" else "调整", color = C.Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { editCauses = !editCauses })
            }
            val qCauses = causes.filter { it.id in question.causeIds }
            if (qCauses.isEmpty()) {
                Text("AI 暂时无法判断，或这题没错——你可以手动指定错因。", color = C.InkSoft, fontSize = 13.sp)
            } else {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    qCauses.forEach { c ->
                        MsChip("${c.category} · ${c.name}", causeColor(c.category).copy(alpha = 0.15f), causeColor(c.category))
                    }
                }
            }
            if (editCauses) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().background(Color(0xFFF7F4FE), RoundedCornerShape(14.dp)).padding(8.dp)) {
                    Column {
                        causes.forEach { c ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Checkbox(
                                    checked = c.id in question.causeIds,
                                    onCheckedChange = { on ->
                                        val newIds = if (on) question.causeIds + c.id else question.causeIds - c.id
                                        app.db.updateQuestionCauses(questionId, newIds, question.causeIds)
                                        reload()
                                    },
                                )
                                MsChip(c.category, causeColor(c.category).copy(alpha = 0.15f), causeColor(c.category))
                                Text(" ${c.name}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            val strategy = qCauses.filter { it.strategy.isNotBlank() }.joinToString(" ") { it.strategy }
            if (strategy.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().background(C.PrimarySoft, RoundedCornerShape(14.dp)).padding(12.dp)) {
                    Text("👨‍👩‍👧 给家长的辅导建议：$strategy", fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
            if (question.coaching.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().background(C.SunSoft, RoundedCornerShape(14.dp)).padding(12.dp)) {
                    Text("🎯 AI 建议：${question.coaching}", fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }

        // 状态
        MsCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "当前状态：" + when (question.status) {
                        "待订正" -> "🔥 待订正"
                        "已订正" -> "✅ 已订正"
                        else -> "⭐ 已收服"
                    },
                    fontWeight = FontWeight.Bold, fontSize = 14.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (question.status == "待订正") MsButton("已订正", { app.db.updateQuestionStatus(questionId, "已订正"); reload() }, bg = C.Accent)
                    if (question.status != "已掌握") MsButton("⭐ 收服", { app.db.updateQuestionStatus(questionId, "已掌握"); reload() }, bg = C.Mint)
                    if (question.status != "待订正") GhostButton("重开", { app.db.updateQuestionStatus(questionId, "待订正"); reload() })
                }
            }
        }

        // 提示链
        MsCard {
            Text("🪜 提示链（一步步来）", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("先自己想，卡住了再点开下一条提示", color = C.InkSoft, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            if (question.hintChain.isEmpty()) {
                Text("这道题没有生成提示链，可以直接问下面的 AI 小老师。", color = C.InkSoft, fontSize = 13.sp)
            } else {
                question.hintChain.forEachIndexed { i, h ->
                    when {
                        i < hintLevel -> {
                            Box(Modifier.fillMaxWidth().background(C.PrimarySoft, RoundedCornerShape(14.dp)).padding(12.dp)) {
                                Text("💡 提示 ${i + 1}：$h", fontSize = 13.sp, lineHeight = 18.sp)
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        i == hintLevel -> {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFF7F4FE))
                                    .clickable { hintLevel = i + 1 }
                                    .padding(12.dp),
                            ) {
                                Text("💡 看提示 ${i + 1}", color = C.Primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                        else -> {}
                    }
                }
            }
        }

        // AI 小老师
        MsCard {
            Text("🧙 AI 小老师", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("只引导、不给答案——动脑的事交给你", color = C.InkSoft, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().background(Color(0xFFFFF4EA), RoundedCornerShape(14.dp)).padding(10.dp)) {
                if (chat.isEmpty()) {
                    Text("跟小老师说说你卡在哪一步吧，比如：「我不懂为什么要先算总数」🤔", color = C.InkSoft, fontSize = 13.sp)
                }
                chat.forEach { (role, text) ->
                    val isUser = role == "user"
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                        Box(
                            Modifier
                                .background(if (isUser) C.Primary else Color.White, RoundedCornerShape(14.dp))
                                .padding(10.dp),
                        ) {
                            Text(text, color = if (isUser) Color.White else C.Ink, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                if (chatting) Text("小老师思考中…", color = C.InkSoft, fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = chatInput,
                    onValueChange = { chatInput = it },
                    placeholder = { Text("问我吧，我不会直接告诉你答案 😊", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    colors = colorsLite(),
                )
                MsButton("发送", {
                    val text = chatInput.trim()
                    chatInput = ""
                    val history = chat + ("user" to text)
                    chat = history
                    chatting = true
                    scope.launch {
                        try {
                            val reply = app.pipeline.guideReply(questionId, history)
                            chat = chat + ("assistant" to reply)
                        } catch (e: Exception) {
                            chat = chat + ("assistant" to ("出了点问题：" + (e.message ?: "未知")))
                        } finally {
                            chatting = false
                        }
                    }
                }, enabled = chatInput.isNotBlank() && !chatting)
            }
        }

        MsButton("📝 针对这道题生成同类题练习卷", { onPractice() }, bg = C.Accent, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
    }
}
