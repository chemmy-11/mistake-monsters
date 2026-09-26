package com.mistakemonsters.app.llm

import com.mistakemonsters.app.data.AiSettings
import com.mistakemonsters.app.data.Prefs
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ===== OpenAI 兼容 LLM 客户端（OkHttp）+ JSON 容错 + 演示模式 =====

class LlmException(message: String) : Exception(message)

object LlmClient {
    // 输出预算：DeepSeek 允许的最大值（max_tokens 只是上限，按实际生成量计费）。
    // 推理模型（如 deepseek-flash）的思维链与答案共享该预算，预算过小会把答案截断成空。
    const val MAX_OUTPUT_TOKENS = 393_216
    // 部分服务商（如 OpenAI gpt-4o 系）max_tokens 上限远低于此，被拒时降级重试用
    private const val FALLBACK_OUTPUT_TOKENS = 8_192

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(240, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    // 消息 content：String 或 Pair(文本, 图片dataUrl) 列表
    data class Msg(val role: String, val text: String, val imageDataUrl: String? = null)

    fun isMock(settings: AiSettings): Boolean = settings.mock || settings.apiKey.isBlank()

    fun chatJson(settings: AiSettings, messages: List<Msg>, maxTokens: Int = 2000): JSONObject {
        val text = chat(settings, messages, maxTokens, forceJson = true)
        return try {
            extractJson(text)
        } catch (e: Exception) {
            val retry = chat(
                settings,
                messages + Msg("assistant", text) + Msg("user", "你刚才的输出不是合法 JSON。请重新输出，只输出 JSON 本体，不要任何解释或代码块标记。"),
                maxTokens,
                forceJson = true,
            )
            extractJson(retry)
        }
    }

    fun chat(settings: AiSettings, messages: List<Msg>, maxTokens: Int = MAX_OUTPUT_TOKENS, forceJson: Boolean = false): String {
        if (isMock(settings)) return Mock.reply(messages)

        val base = normalizeBaseUrl(settings.baseUrl)
        val needVision = messages.any { it.imageDataUrl != null }
        val model = if (needVision) {
            if (settings.visionModel.isBlank())
                throw LlmException("当前服务商未配置视觉模型，无法识别图片。请到「设置」填写视觉模型名")
            settings.visionModel
        } else {
            if (settings.textModel.isBlank()) throw LlmException("当前服务商未配置文本模型，请到「设置」填写")
            settings.textModel
        }

        val arr = JSONArray()
        for (m in messages) {
            val o = JSONObject().put("role", m.role)
            if (m.imageDataUrl != null) {
                o.put("content", JSONArray()
                    .put(JSONObject().put("type", "text").put("text", m.text))
                    .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", m.imageDataUrl))))
            } else {
                o.put("content", m.text)
            }
            arr.put(o)
        }

        // useAdvanced=false 时去掉思考深度并把预算降到保守值（服务商不支持大 max_tokens 时的回退）
        fun buildBody(useAdvanced: Boolean): JSONObject {
            val b = JSONObject()
                .put("model", model)
                .put("messages", arr)
                .put("temperature", 0.3)
                .put("stream", false)
                .put(
                    "max_tokens",
                    if (useAdvanced) maxTokens else minOf(maxTokens, FALLBACK_OUTPUT_TOKENS),
                )
            if (forceJson) b.put("response_format", JSONObject().put("type", "json_object"))
            if (useAdvanced && settings.thinkingDepth.isNotBlank()) b.put("reasoning_effort", settings.thinkingDepth)
            return b
        }

        fun send(useAdvanced: Boolean): okhttp3.Response {
            val req = Request.Builder()
                .url("$base/chat/completions")
                .header("Authorization", "Bearer ${settings.apiKey}")
                .post(buildBody(useAdvanced).toString().toRequestBody("application/json".toMediaType()))
                .build()
            return try {
                client.newCall(req).execute()
            } catch (e: Exception) {
                throw LlmException("无法连接 AI 服务（$base）：${e.message ?: e}")
            }
        }

        var resp = send(true)
        if (resp.code == 400) {
            val detail = resp.use { r ->
                val t = r.body?.string() ?: ""
                (try { JSONObject(t).optJSONObject("error")?.optString("message") } catch (e: Exception) { t.take(200) }) ?: t.take(200)
            }
            if (Regex("max_tokens|reasoning|unsupported|invalid.*parameter", RegexOption.IGNORE_CASE).containsMatchIn(detail)) {
                resp = send(false) // 服务商不支持大预算/思考深度 → 自动降级重试
            } else {
                throw LlmException("AI 服务返回错误 400：$detail")
            }
        }
        resp.use { r ->
            val text = r.body?.string() ?: ""
            if (!r.isSuccessful) {
                val detail = try { JSONObject(text).optJSONObject("error")?.optString("message") } catch (e: Exception) { text.take(200) }
                throw LlmException("AI 服务返回错误 ${r.code}：${detail ?: "未知错误"}")
            }
            val choice = JSONObject(text).optJSONArray("choices")?.optJSONObject(0)
            val content = choice?.optJSONObject("message")?.optString("content")
            // 推理模型（如 deepseek-flash）的思维链与答案共享 max_tokens：
            // 预算不足时答案会被截断甚至为空，直接给出可操作的提示，避免无效重试
            if (choice?.optString("finish_reason") == "length") {
                val usedReasoning = !choice.optJSONObject("message")?.optString("reasoning_content").isNullOrBlank()
                throw LlmException(
                    if (usedReasoning)
                        "AI 的推理过程占满了输出上限，答案被截断（推理模型如 deepseek-flash 的思维链与答案共享输出预算）。请到「设置」换成非推理模型（如 deepseek-chat）后重试"
                    else
                        "AI 输出超出长度上限被截断，请缩短题目内容或到「设置」更换模型"
                )
            }
            if (content.isNullOrBlank()) throw LlmException("AI 返回了空内容，请重试或更换模型")
            return content
        }
        throw LlmException("AI 调用失败")
    }

    private fun normalizeBaseUrl(raw: String): String {
        var u = raw.trim().trimEnd('/')
        if (u.isBlank()) throw LlmException("未配置 AI 服务地址，请到「设置」配置")
        // 只有域名时自动补 /v1
        val afterScheme = u.substringAfter("://", "")
        if (!afterScheme.contains('/')) u = "$u/v1"
        return u
    }

    // ---------- JSON 容错提取 ----------
    fun extractJson(text: String): JSONObject {
        var t = text.trim()
        Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(t)?.let { t = it.groupValues[1].trim() }
        try { return JSONObject(t) } catch (e: Exception) { /* continue */ }
        val start = listOf(t.indexOf('{'), t.indexOf('[')).filter { it >= 0 }.minOrNull()
            ?: throw LlmException("AI 返回内容中未找到 JSON，请重试或更换模型")
        val end = maxOf(t.lastIndexOf('}'), t.lastIndexOf(']'))
        if (end <= start) throw LlmException("AI 返回的 JSON 不完整")
        val slice = t.substring(start, end + 1)
        try { return JSONObject(slice) } catch (e: Exception) { /* continue */ }
        val fixed = Regex(",\\s*([}\\]])").replace(slice, "$1")
        return JSONObject(fixed)
    }
}

// ===== 演示模式：无 Key / mock 开关时用固定数据走通全流程 =====
object Mock {
    fun reply(messages: List<LlmClient.Msg>): String {
        val last = messages.lastOrNull()?.text ?: ""
        val all = messages.joinToString("\n") { it.text }
        val hasImage = messages.any { it.imageDataUrl != null }

        if (hasImage) {
            return """{"questions":[{
                "question_text":"学校买了 3 盒钢笔，每盒 12 支，把这些钢笔平均分给 4 个班，每个班分得多少支？",
                "category":"应用题","subtype":"归一归总",
                "knowledge_tags":["乘法","平均分","两步计算应用题"],
                "my_answer_visible":true,"my_answer":"每个班分得 11 支",
                "note":"演示模式：这是模拟识别结果，配置真实 AI 服务商后即可识别你的照片。"}]}"""
        }
        if (all.contains("同类练习题") || all.contains("重新计算验证")) {
            val items = (1..6).map { i ->
                """{"stem":"（演示题 $i）小明买了 ${i + 2} 盒铅笔，每盒 ${10 + i} 支，平均分给 ${i + 3} 个小组，每组分得多少支？",
                "answer":"${((i + 2) * (10 + i)) / (i + 3)} 支","solution":"先用乘法求总支数，再用除法平均分，注意答案要写单位。",
                "targeted_cause":"计算失误","difficulty":${if (i > 4) 4 else 3},"variant":"${if (i > 4) "变式" else "基础"}"}"""
            }
            return """{"items":[${items.joinToString(",")}]}"""
        }
        if (last.contains("只输出 JSON") || all.contains("hint_chain")) {
            return """{"correct_answer":"每个班分得 9 支",
                "analysis":"第一步：先算一共有多少支钢笔，3 盒 × 每盒 12 支 = 36 支。第二步：把 36 支平均分给 4 个班，36 ÷ 4 = 9。第三步：别忘了写单位和答语。",
                "cause_id":null,
                "new_cause":{"name":"计算失误","category":"习惯性","description":"两步计算的中间结果出错。"},
                "difficulty":3,
                "hint_chain":["先想一想：要求每个班分到多少支，需要先知道什么？","对啦，要先算出钢笔的总数。用什么运算把 3 盒和每盒 12 支合起来呢？","总数算出来后，'平均分给 4 个班' 该用哪一种运算？算完记得检查单位哦。"],
                "coaching":"两步应用题建议先列分步式再列综合式，每一步写清'这一步求的是什么'。",
                "knowledge_tags":["乘法","平均分","两步计算应用题"]}"""
        }
        if (all.contains("长期记忆画像")) {
            return "演示画像：该学生总体计算基础扎实，但在两步计算应用题上容易出现中间步骤的计算失误，且偶尔遗漏单位。近期在「平均分」类题目上连续出错，建议用分步列式的方法放慢速度，先说思路再动笔。优势是应用题读题能力强，审题准确率高。"
        }
        return "（演示模式）好问题！我们先看第一步：题目里一共有几盒钢笔、每盒几支？把这两个数用算式写出来试试 😊"
    }
}
