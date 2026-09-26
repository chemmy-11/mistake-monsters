package com.mistakemonsters.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

// ===== 轻量配置存储（AI 服务商 / 学生档案 / 演示模式）=====

class Prefs private constructor(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("mistake_monsters", Context.MODE_PRIVATE)

    companion object {
        @Volatile private var instance: Prefs? = null
        fun get(context: Context): Prefs =
            instance ?: synchronized(this) { instance ?: Prefs(context.applicationContext).also { instance = it } }
    }

    fun aiSettings(): AiSettings {
        val raw = sp.getString("ai", null) ?: return AiSettings()
        return try {
            val o = JSONObject(raw)
            AiSettings(
                presetId = o.optString("presetId", "deepseek"),
                baseUrl = o.optString("baseUrl"),
                apiKey = o.optString("apiKey"),
                visionModel = o.optString("visionModel"),
                textModel = o.optString("textModel"),
                thinkingDepth = o.optString("thinkingDepth", "high"),
                mock = o.optBoolean("mock", false),
            )
        } catch (e: Exception) {
            AiSettings()
        }
    }

    fun saveAiSettings(s: AiSettings) {
        sp.edit().putString("ai", JSONObject().apply {
            put("presetId", s.presetId); put("baseUrl", s.baseUrl); put("apiKey", s.apiKey)
            put("visionModel", s.visionModel); put("textModel", s.textModel)
            put("thinkingDepth", s.thinkingDepth); put("mock", s.mock)
        }.toString()).apply()
    }

    fun profile(): Profile {
        val raw = sp.getString("profile", null) ?: return Profile()
        return try {
            val o = JSONObject(raw)
            Profile(
                name = o.optString("name", "小数学家"),
                grade = o.optInt("grade", 3),
                avatar = o.optString("avatar", "🐭"),
                memoDigest = o.optString("memoDigest"),
                digestUpdatedAt = if (o.isNull("digestUpdatedAt")) null else o.optString("digestUpdatedAt"),
            )
        } catch (e: Exception) {
            Profile()
        }
    }

    fun saveProfile(p: Profile) {
        sp.edit().putString("profile", JSONObject().apply {
            put("name", p.name); put("grade", p.grade); put("avatar", p.avatar)
            put("memoDigest", p.memoDigest); put("digestUpdatedAt", p.digestUpdatedAt ?: JSONObject.NULL)
        }.toString()).apply()
    }
}
