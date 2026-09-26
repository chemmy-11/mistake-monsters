package com.mistakemonsters.app.logic

import com.mistakemonsters.app.data.Cause
import com.mistakemonsters.app.data.DraftQuestion
import com.mistakemonsters.app.data.Db
import com.mistakemonsters.app.data.Prefs
import com.mistakemonsters.app.data.PracticeItem
import com.mistakemonsters.app.data.PracticeSet
import com.mistakemonsters.app.data.Profile
import com.mistakemonsters.app.data.Question
import com.mistakemonsters.app.data.nowStr
import com.mistakemonsters.app.llm.LlmClient
import com.mistakemonsters.app.llm.LlmException
import com.mistakemonsters.app.llm.Prompts
import com.mistakemonsters.app.data.Taxonomy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// ===== 业务管线：识别 → 归因入库 → 记忆沉淀 → 出题 → 画像 =====

class Pipeline(private val db: Db, private val prefs: Prefs) {

    private fun ai() = prefs.aiSettings()
    private fun profile(): Profile = prefs.profile()

    // ---------- 1. 图片识别（只转录，不解题） ----------
    suspend fun recognizeDraft(dataUrl: String): List<DraftQuestion> = withContext(Dispatchers.IO) {
        val out = LlmClient.chatJson(
            ai(),
            listOf(
                LlmClient.Msg("system", Prompts.EXTRACT_PROMPT),
                LlmClient.Msg("user", "请识别这张照片中的数学题，按系统提示输出 JSON。", dataUrl),
            ),
            maxTokens = LlmClient.MAX_OUTPUT_TOKENS,
        )
        val list = out.optJSONArray("questions") ?: JSONArray()
        (0 until list.length()).mapNotNull { i ->
            val o = list.optJSONObject(i) ?: return@mapNotNull null
            val text = o.optString("question_text").trim()
            if (text.isEmpty()) null
            else DraftQuestion(
                questionText = text,
                category = if (Taxonomy.categoryOk(o.optString("category"))) o.getString("category") else "其他",
                subtype = o.optString("subtype").take(30),
                knowledgeTags = stringListOf(o.optJSONArray("knowledge_tags")).take(4),
                myAnswerVisible = o.optBoolean("my_answer_visible"),
                myAnswer = o.optString("my_answer"),
                note = o.optString("note"),
                selected = true,
            )
        }.take(5)
    }

    // ---------- 2. 错因分析 + 入库 + 记忆更新 ----------
    suspend fun analyzeAndCreate(
        questionText: String,
        myAnswer: String,
        category: String?,
        subtype: String?,
        knowledgeTags: List<String>,
        note: String?,
        imagePath: String?,
        suspectedCause: String? = null,
    ): Question = withContext(Dispatchers.IO) {
        val p = profile()
        val causes = db.listCauses()
        val out = LlmClient.chatJson(
            ai(),
            listOf(
                LlmClient.Msg(
                    "user",
                    Prompts.analyze(
                        questionText, myAnswer, note, p.memoDigest,
                        causes.map { Prompts.CauseLite(it.id, it.name, it.category, it.description) },
                        suspectedCause,
                    ),
                )
            ),
            maxTokens = LlmClient.MAX_OUTPUT_TOKENS,
        )

        // ---- 错因 upsert ----
        var causeId: Long? = null
        val outCauseId = if (out.isNull("cause_id")) null else out.optLong("cause_id", -1L)
        if (outCauseId != null && outCauseId > 0 && causes.any { it.id == outCauseId }) {
            causeId = outCauseId
        } else {
            val nc = out.optJSONObject("new_cause")
            val name = nc?.optString("name")?.trim().orEmpty()
            if (nc != null && name.isNotEmpty()) {
                val existing = db.findCauseByName(name.take(24))
                causeId = existing?.id ?: db.insertCause(
                    name.take(24),
                    if (nc.optString("category") in Taxonomy.CAUSE_CATEGORIES) nc.getString("category") else "习惯性",
                    nc.optString("description").take(200),
                )
            }
        }

        val hintChain = stringListOf(out.optJSONArray("hint_chain")).filter { it.isNotBlank() }.take(3)
        val tagsIn = stringListOf(out.optJSONArray("knowledge_tags"))
        val tags = tagsIn.ifEmpty { knowledgeTags }.take(4)
        val cat = if (Taxonomy.categoryOk(out.optString("category"))) out.getString("category")
        else if (Taxonomy.categoryOk(category)) category!! else "其他"

        val id = db.insertQuestion(
            Question(
                imagePath = imagePath,
                questionText = questionText,
                category = cat,
                subtype = out.optString("subtype").ifBlank { subtype ?: "" }.take(30),
                knowledgeTags = tags,
                difficulty = out.optInt("difficulty", 3).coerceIn(1, 5),
                myAnswer = myAnswer,
                correctAnswer = out.optString("correct_answer"),
                analysis = out.optString("analysis"),
                causeIds = if (causeId != null) listOf(causeId) else emptyList(),
                status = "待订正",
                hintChain = hintChain,
                coaching = out.optString("coaching"),
                note = note ?: "",
            )
        )

        if (causeId != null) {
            db.bumpCause(causeId)
            updateMemoryPattern(causeId)
        }

        // ---- 每积累 8 题自动刷新画像（尽力而为） ----
        if (db.countAll("questions") % 8 == 0) {
            try { regenDigest() } catch (e: Exception) { /* ignore */ }
        }

        db.getQuestion(id)!!
    }

    // ---------- 3. 长期记忆：模式沉淀 ----------
    private fun updateMemoryPattern(causeId: Long) {
        val cause = db.listCauses().find { it.id == causeId } ?: return
        val questions = db.recentQuestionsWithTagByCause(causeId)
        val tagCount = HashMap<String, Int>()
        for (q in questions) for (t in q.knowledgeTags) tagCount[t] = (tagCount[t] ?: 0) + 1
        val top = tagCount.entries.maxByOrNull { it.value } ?: return
        if (top.value < 2) return
        val content = "近 20 道错题里有 ${top.value} 道都指向「${top.key}」，且错因集中在「${cause.name}」——这是当前最需要巩固的组合。"
        db.upsertMemoryByFragments(top.key, cause.name, content, top.value)
    }

    // ---------- 4. 学生画像 ----------
    suspend fun regenDigest(): String = withContext(Dispatchers.IO) {
        val p = profile()
        val text = LlmClient.chat(
            ai(),
            listOf(
                LlmClient.Msg(
                    "user",
                    Prompts.digest(
                        p.name, p.grade,
                        db.listCauses().filter { it.count > 0 },
                        db.listMemories(),
                        db.recentQuestions(8),
                        p.memoDigest,
                    ),
                )
            ),
            maxTokens = LlmClient.MAX_OUTPUT_TOKENS,
        )
        val digest = text.trim().take(1200)
        prefs.saveProfile(p.copy(memoDigest = digest, digestUpdatedAt = nowStr()))
        digest
    }

    // ---------- 5. 同类题生成 ----------
    suspend fun generateSimilar(causeId: Long?, questionIds: List<Long>?, count: Int, ramp: Boolean): PracticeSet =
        withContext(Dispatchers.IO) {
            val p = profile()
            val n = count.coerceIn(3, 12)

            var questions: List<Question> = emptyList()
            if (!questionIds.isNullOrEmpty()) {
                questions = questionIds.take(3).mapNotNull { db.getQuestion(it) }
            } else if (causeId != null) {
                questions = db.listQuestions(causeId = causeId).take(2)
            }
            if (questions.isEmpty()) questions = db.listQuestions().take(2)
            if (questions.isEmpty()) throw LlmException("还没有错题，先去收录一道吧")

            val causeIdSet = LinkedHashSet<Long>()
            questions.forEach { causeIdSet.addAll(it.causeIds) }
            causeId?.let { causeIdSet.add(it) }
            val causes = db.listCauses()
            val causeNames = causes.filter { it.id in causeIdSet }.map { it.name }

            val out = LlmClient.chatJson(
                ai(),
                listOf(
                    LlmClient.Msg(
                        "user",
                        Prompts.similar(
                            p.grade, n, ramp, p.memoDigest, causeNames,
                            questions.map { Triple(it.questionText, it.correctAnswer, it.knowledgeTags) },
                        ),
                    )
                ),
                maxTokens = LlmClient.MAX_OUTPUT_TOKENS,
            )

            val arr = out.optJSONArray("items") ?: JSONArray()
            val items = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val stem = o.optString("stem").trim()
                if (stem.isEmpty()) null
                else PracticeItem(
                    stem = stem,
                    answer = o.optString("answer").trim(),
                    solution = o.optString("solution").trim(),
                    targetedCause = o.optString("targeted_cause").trim(),
                    difficulty = o.optInt("difficulty", 3).coerceIn(1, 5),
                    variant = if (o.optString("variant") == "变式") "变式" else "基础",
                )
            }.take(n).toMutableList()
            if (items.isEmpty()) throw LlmException("AI 没有生成有效题目，请重试")

            // 自校验：重算答案、修正除不尽的题（演示模式跳过）
            if (!LlmClient.isMock(ai())) {
                try {
                    val checked = LlmClient.chatJson(
                        ai(),
                        listOf(LlmClient.Msg("user", Prompts.verify(items))),
                        maxTokens = LlmClient.MAX_OUTPUT_TOKENS,
                    )
                    val cArr = checked.optJSONArray("items")
                    if (cArr != null && cArr.length() == items.size) {
                        for (i in 0 until items.size) {
                            val v = cArr.optJSONObject(i) ?: continue
                            val stem = v.optString("stem").trim()
                            if (stem.isNotEmpty()) {
                                val old = items[i]
                                items[i] = PracticeItem(
                                    stem = stem,
                                    answer = v.optString("answer", old.answer).trim(),
                                    solution = v.optString("solution", old.solution).trim(),
                                    targetedCause = old.targetedCause,
                                    difficulty = old.difficulty,
                                    variant = old.variant,
                                )
                            }
                        }
                    }
                } catch (e: Exception) { /* 校验失败不阻塞出题 */ }
            }

            questions.forEach { db.bumpPracticeCount(it.id) }

            val title = "${items.first().targetedCause.ifBlank { "同类题" }}练习 ${nowStr().take(10)}"
            val id = db.insertPracticeSet(title, items, questions.map { it.id })
            db.getPracticeSet(id)!!
        }

    // ---------- 6. AI 小老师 ----------
    suspend fun guideReply(questionId: Long, history: List<Pair<String, String>>): String =
        withContext(Dispatchers.IO) {
            val q = db.getQuestion(questionId) ?: throw LlmException("错题不存在")
            val p = profile()
            val msgs = mutableListOf(
                LlmClient.Msg(
                    "system",
                    Prompts.guide(p.name, p.grade, p.memoDigest, q.questionText, q.myAnswer, q.hintChain),
                )
            )
            history.takeLast(10).forEach { msgs.add(LlmClient.Msg(it.first, it.second)) }
            LlmClient.chat(ai(), msgs, maxTokens = LlmClient.MAX_OUTPUT_TOKENS).trim()
        }

    // ---------- 7. 报告 ----------
    fun report(): com.mistakemonsters.app.data.ReportData {
        val counts = com.mistakemonsters.app.data.Counts(
            total = db.countAll("questions"),
            pending = db.countByStatus("待订正"),
            corrected = db.countByStatus("已订正"),
            mastered = db.countByStatus("已掌握"),
            sets = db.countAll("practice_sets"),
        )
        val tagCount = HashMap<String, Int>()
        for (q in db.listQuestions()) for (t in q.knowledgeTags) tagCount[t] = (tagCount[t] ?: 0) + 1
        val weakTags = tagCount.entries.sortedByDescending { it.value }.take(8).map { it.key to it.value }
        return com.mistakemonsters.app.data.ReportData(
            counts = counts,
            causes = db.listCauses(),
            weakTags = weakTags,
            memories = db.listMemories(),
            recent = db.listQuestions().take(5),
        )
    }

    companion object {
        fun stringListOf(arr: JSONArray?): List<String> {
            arr ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                when (val v = arr.opt(i)) {
                    is String -> v
                    null -> null
                    else -> v.toString()
                }
            }
        }
    }
}
