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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistakemonsters.app.App
import com.mistakemonsters.app.data.AiSettings
import com.mistakemonsters.app.data.Profile
import com.mistakemonsters.app.data.Taxonomy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ===== 设置：学生档案 / AI 服务商 / 关于 =====

@Composable
fun SettingsScreen(app: App) {
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf(app.prefs.profile()) }
    var ai by remember { mutableStateOf(app.prefs.aiSettings()) }
    var apiKeyInput by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    fun saveAi(s: AiSettings) {
        app.prefs.saveAiSettings(s)
        ai = s
        apiKeyInput = ""
        msg = "已保存 ✓"
    }

    LazyColumn(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("⚙️ 设置", fontSize = 20.sp, fontWeight = FontWeight.Bold) }

        // 学生档案
        item {
            MsCard {
                Text("🧑‍🎓 学生档案", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Taxonomy.AVATARS.forEach { a ->
                        Text(
                            a,
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (profile.avatar == a) C.Primary else Color(0xFFF0EDE8))
                                .clickable {
                                    profile = profile.copy(avatar = a)
                                    app.prefs.saveProfile(profile)
                                },
                            fontSize = 18.sp,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = profile.name,
                        onValueChange = { n ->
                            profile = profile.copy(name = n.take(12))
                            app.prefs.saveProfile(profile)
                        },
                        label = { Text("昵称") },
                        modifier = Modifier.weight(1f),
                        colors = colorsLite(),
                    )
                    DropdownPicker(
                        label = "${profile.grade} 年级",
                        options = (1..6).map { "$it 年级" },
                        selectedIndex = profile.grade - 1,
                        onSelect = { i ->
                            if (i != null) {
                                profile = profile.copy(grade = i + 1)
                                app.prefs.saveProfile(profile)
                            }
                        },
                        modifier = Modifier.weight(0.8f),
                    )
                }
            }
        }

        // AI 服务商
        item {
            MsCard {
                Text("🤖 AI 服务商（OpenAI 兼容接口）", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "支持任何 OpenAI 兼容服务：分别配置「视觉模型」（看图识别题目）和「文本模型」（错因分析/出题/辅导）。密钥只保存在本机。",
                    color = C.InkSoft, fontSize = 12.sp, lineHeight = 17.sp,
                )
                Spacer(Modifier.height(10.dp))
                DropdownPicker(
                    label = (Taxonomy.AI_PRESETS.find { it.id == ai.presetId }?.name ?: ai.presetId),
                    options = Taxonomy.AI_PRESETS.map { it.name },
                    selectedIndex = Taxonomy.AI_PRESETS.indexOfFirst { it.id == ai.presetId }.takeIf { it >= 0 },
                    onSelect = { i ->
                        if (i != null) {
                            val p = Taxonomy.AI_PRESETS[i]
                            ai = ai.copy(
                                presetId = p.id,
                                baseUrl = p.baseUrl,
                                visionModel = p.visionModel,
                                textModel = p.textModel,
                            )
                            saveAi(ai)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Labeled("API 地址（Base URL）") {
                    OutlinedTextField(
                        value = ai.baseUrl,
                        onValueChange = { ai = ai.copy(baseUrl = it) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = colorsLite(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Labeled(if (ai.apiKey.isBlank()) "API Key" else "API Key（已配置，输入新值可更换）") {
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        placeholder = { Text(if (ai.apiKey.isBlank()) "sk-..." else "留空保持不变") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = colorsLite(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Labeled("视觉模型（识别题目照片）") {
                    OutlinedTextField(
                        value = ai.visionModel,
                        onValueChange = { ai = ai.copy(visionModel = it) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = colorsLite(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Labeled("文本模型（错因分析 / 出题 / 辅导）") {
                    OutlinedTextField(
                        value = ai.textModel,
                        onValueChange = { ai = ai.copy(textModel = it) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = colorsLite(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Labeled("思考深度（推理模型的 reasoning_effort）") {
                    DropdownPicker(
                        label = (Taxonomy.THINKING_DEPTHS.find { it.id == ai.thinkingDepth }?.name ?: "高（推理更充分，推荐）"),
                        options = Taxonomy.THINKING_DEPTHS.map { it.name },
                        selectedIndex = Taxonomy.THINKING_DEPTHS.indexOfFirst { it.id == ai.thinkingDepth }.takeIf { it >= 0 },
                        onSelect = { i ->
                            if (i != null) {
                                ai = ai.copy(thinkingDepth = Taxonomy.THINKING_DEPTHS[i].id)
                                saveAi(ai)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "档位越高思维链越长、越不容易算错，但更慢。非推理模型会忽略；服务商不支持时自动去掉后重试。输出预算已放宽到服务商上限（DeepSeek 为 393216 tokens，按实际用量计费）。",
                    color = C.InkSoft, fontSize = 11.sp, lineHeight = 16.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MsButton("保存 AI 配置", { saveAi(ai.copy(apiKey = apiKeyInput.ifBlank { ai.apiKey })) })
                    GhostButton(if (testing) "测试中…" else "🔌 测试连接", {
                        // 先把当前输入保存，再测
                        app.prefs.saveAiSettings(ai.copy(apiKey = apiKeyInput.ifBlank { ai.apiKey }))
                        ai = app.prefs.aiSettings()
                        testing = true
                        msg = null
                        scope.launch {
                            try {
                                val reply = withContext(Dispatchers.IO) {
                                    com.mistakemonsters.app.llm.LlmClient.chat(
                                        ai,
                                        listOf(com.mistakemonsters.app.llm.LlmClient.Msg("user", "请只回复两个字：连接")),
                                        maxTokens = com.mistakemonsters.app.llm.LlmClient.MAX_OUTPUT_TOKENS,
                                    )
                                }
                                msg = "连接成功！AI 回复：${reply.trim().take(30)}"
                            } catch (e: Exception) {
                                msg = "连接失败：${e.message}"
                            } finally {
                                testing = false
                            }
                        }
                    }, enabled = !testing)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("演示模式（不用 Key 也能体验全流程）", fontSize = 12.sp, color = C.InkSoft, modifier = Modifier.weight(1f))
                    Switch(
                        checked = ai.mock,
                        onCheckedChange = { saveAi(ai.copy(mock = it)) },
                        colors = SwitchDefaults.colors(checkedTrackColor = C.Primary),
                    )
                }
                msg?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(msg!!, color = if (msg!!.startsWith("连接失败")) C.Rose else C.Mint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 版本与更新
        item {
            MsCard {
                val st = app.updater.status
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("🔄 版本与更新", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("当前版本 ${com.mistakemonsters.app.update.Updater.currentVersion()}", color = C.InkSoft, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "启动时自动检查新版本；更新包从 GitHub Release 下载并引导安装。",
                    color = C.InkSoft, fontSize = 12.sp, lineHeight = 17.sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MsButton(
                        if (st is com.mistakemonsters.app.update.UpdateManager.Status.Checking) "检查中…" else "检查更新",
                        { app.updater.manualCheck() },
                        enabled = st !is com.mistakemonsters.app.update.UpdateManager.Status.Checking,
                    )
                    when (st) {
                        is com.mistakemonsters.app.update.UpdateManager.Status.UpToDate ->
                            Text("✅ 已是最新版本", color = C.Mint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        is com.mistakemonsters.app.update.UpdateManager.Status.Available ->
                            Text("🎉 发现新版本 ${st.info.version}", color = C.PrimaryDeep, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        is com.mistakemonsters.app.update.UpdateManager.Status.Downloading ->
                            Text("⬇️ ${st.pct}%", color = C.PrimaryDeep, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        is com.mistakemonsters.app.update.UpdateManager.Status.Failed ->
                            Text("⚠️ ${st.message}", color = C.Rose, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        else -> {}
                    }
                }
            }
        }

        item {
            MsCard {
                Text("ℹ️ 关于", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "错题小怪兽是一款本地优先的小学数学错题本：数据（错题、错因、记忆、照片）全部保存在手机本地，只有题目文本和照片会发送给你配置的 AI 服务商。本应用不提供「拍题给答案」功能——收录的题目必须填写自己的答案，AI 只做归因、引导和出题。",
                    color = C.InkSoft, fontSize = 12.sp, lineHeight = 18.sp,
                )
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}
