package com.mistakemonsters.app.data

// ===== 数据模型（与 Web 版错题小怪兽同构）=====

data class Question(
    val id: Long = 0,
    val createdAt: String = "",
    val imagePath: String? = null,
    val questionText: String = "",
    val category: String = "其他",
    val subtype: String = "",
    val knowledgeTags: List<String> = emptyList(),
    val difficulty: Int = 3,
    val myAnswer: String = "",
    val correctAnswer: String = "",
    val analysis: String = "",
    val causeIds: List<Long> = emptyList(),
    val status: String = "待订正",
    val hintChain: List<String> = emptyList(),
    val coaching: String = "",
    val note: String = "",
    val practiceCount: Int = 0,
)

data class Cause(
    val id: Long = 0,
    val name: String = "",
    val category: String = "习惯性",
    val description: String = "",
    val strategy: String = "",
    val count: Int = 0,
    val lastSeenAt: String? = null,
    val preset: Int = 0,
)

data class MemoryItem(
    val id: Long = 0,
    val content: String,
    val kind: String = "pattern",
    val weight: Int = 1,
    val createdAt: String = "",
)

data class PracticeItem(
    val stem: String,
    val answer: String,
    val solution: String,
    val targetedCause: String = "",
    val difficulty: Int = 3,
    val variant: String = "基础",
)

data class PracticeSet(
    val id: Long = 0,
    val createdAt: String = "",
    val title: String = "",
    val items: List<PracticeItem> = emptyList(),
    val sourceQuestionIds: List<Long> = emptyList(),
)

data class DraftQuestion(
    val questionText: String = "",
    val category: String = "其他",
    val subtype: String = "",
    val knowledgeTags: List<String> = emptyList(),
    val myAnswerVisible: Boolean = false,
    val myAnswer: String = "",
    val note: String = "",
    var selected: Boolean = true,
)

data class AiSettings(
    val presetId: String = "deepseek",
    val baseUrl: String = "https://api.deepseek.com/v1",
    val apiKey: String = "",
    val visionModel: String = "deepseek-flash",
    val textModel: String = "deepseek-flash",
    val mock: Boolean = false,
)

data class Profile(
    val name: String = "小数学家",
    val grade: Int = 3,
    val avatar: String = "🐭",
    val memoDigest: String = "",
    val digestUpdatedAt: String? = null,
)

data class ReportData(
    val counts: Counts,
    val causes: List<Cause>,
    val weakTags: List<Pair<String, Int>>,
    val memories: List<MemoryItem>,
    val recent: List<Question>,
)

data class Counts(
    val total: Int,
    val pending: Int,
    val corrected: Int,
    val mastered: Int,
    val sets: Int,
)
