package com.mistakemonsters.app.data

// ===== 小学数学题型分类体系 + 预置错因库 =====

data class Category(val key: String, val emoji: String, val subtypes: List<String>)

object Taxonomy {
    val CATEGORIES = listOf(
        Category("数与运算", "🔢", listOf("口算", "竖式计算", "简便运算", "分数小数运算", "混合运算")),
        Category("应用题", "🚗", listOf("行程问题", "归一归总", "平均数问题", "鸡兔同笼", "分数百分数应用", "利润折扣", "和差倍问题", "年龄问题", "植树问题", "其他应用题")),
        Category("图形几何", "📐", listOf("周长与面积", "表面积与体积", "角度计算", "图形变换", "观察物体")),
        Category("量与计量", "⏰", listOf("单位换算", "时间计算", "人民币计算")),
        Category("代数初步", "🧮", listOf("用字母表示数", "简易方程", "找规律", "比和比例")),
        Category("统计与概率", "📊", listOf("统计图表", "可能性", "数据整理")),
        Category("其他", "🧩", emptyList()),
    )

    val CATEGORY_KEYS = CATEGORIES.map { it.key }

    fun emojiOf(key: String): String = CATEGORIES.find { it.key == key }?.emoji ?: "🧩"

    fun categoryOk(c: String?) = c != null && c in CATEGORY_KEYS

    val CAUSE_CATEGORIES = listOf("知识性", "习惯性", "审题性", "心理性")

    val STATUS_LIST = listOf("待订正", "已订正", "已掌握")

    val AVATARS = listOf("🐭", "🐰", "🦊", "🐼", "🐯", "🐨", "🐸", "🦄", "🐙", "🐔")

    val PRESET_CAUSES = listOf(
        Cause(name = "概念理解不清", category = "知识性", description = "对知识点本身理解有偏差，例如混淆周长与面积、不理解分数的意义。", strategy = "回到概念本源，用画图、实物演示重建理解，再练 3 道基础题确认。", preset = 1),
        Cause(name = "计算失误", category = "习惯性", description = "进位退位、乘法口诀、运算顺序等计算环节出错。", strategy = "圈出出错的那一步，做 5 分钟针对性口算，养成算完回头验算的习惯。", preset = 1),
        Cause(name = "审题不清", category = "审题性", description = "看错条件、漏看关键词（如“最多”“至少”）、理解题意有偏差。", strategy = "训练“读题两遍 + 圈关键词”，让孩子先复述题目意思再动笔。", preset = 1),
        Cause(name = "抄错数字", category = "习惯性", description = "从题目抄到草稿、或从草稿誊到答卷时抄错。", strategy = "抄完立刻核对一遍；草稿纸分区书写，别跳步抄写。", preset = 1),
        Cause(name = "单位与答语遗漏", category = "习惯性", description = "忘写单位、忘写答句，或答非所问。", strategy = "应用题做完检查三件事：单位、答语、结论有没有回答问题。", preset = 1),
        Cause(name = "方法选择错误", category = "知识性", description = "解题方向不对，用了不合适的策略。", strategy = "把错法和正法放在一起对比，讨论“为什么这个方法走不通”，再归纳题型特征。", preset = 1),
        Cause(name = "图形空间理解偏差", category = "知识性", description = "图形关系、旋转平移、立体图形想象出错。", strategy = "动手画图或折纸演示，先建立直观印象，再进行计算。", preset = 1),
        Cause(name = "粗心大意", category = "习惯性", description = "综合性的马虎，暂时找不到单一原因。", strategy = "不要贴“粗心”标签，定位到具体环节（看题 / 计算 / 检查），逐项针对性训练。", preset = 1),
        Cause(name = "紧张或时间压力", category = "心理性", description = "考试紧张、时间不够导致发挥失常。", strategy = "做限时模拟练习，先易后难，平时降低对分数的焦虑。", preset = 1),
    )

    data class AiPreset(val id: String, val name: String, val baseUrl: String, val visionModel: String, val textModel: String)

    val AI_PRESETS = listOf(
        AiPreset("zhipu", "智谱 GLM（多模态）", "https://open.bigmodel.cn/api/paas/v4", "glm-4v-flash", "glm-4-flash"),
        AiPreset("dashscope", "阿里云百炼（Qwen）", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-vl-max", "qwen-plus"),
        AiPreset("deepseek", "DeepSeek（纯文本）", "https://api.deepseek.com/v1", "", "deepseek-chat"),
        AiPreset("openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", "gpt-4o-mini"),
        AiPreset("siliconflow", "硅基流动 SiliconFlow", "https://api.siliconflow.cn/v1", "Qwen/Qwen2.5-VL-7B-Instruct", "deepseek-ai/DeepSeek-V3"),
        AiPreset("moonshot", "月之暗面 Kimi", "https://api.moonshot.cn/v1", "moonshot-v1-8k-vision-preview", "moonshot-v1-8k"),
    )
}
