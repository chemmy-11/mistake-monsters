package com.mistakemonsters.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ===== SQLite 存储（与 Web 版同构的表结构）=====

fun nowStr(): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

private fun jsonListOf(vararg items: String): String = JSONArray(items.toList()).toString()

private fun List<String>.toJson(): String = JSONArray(this).toString()

private fun String.fromJsonList(): List<String> {
    if (isBlank()) return emptyList()
    return try {
        val arr = JSONArray(this)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun String.fromJsonLongList(): List<Long> =
    fromJsonList().mapNotNull { it.toLongOrNull() }

class Db private constructor(context: Context) : SQLiteOpenHelper(context, "mistake_monsters.db", null, 1) {

    companion object {
        @Volatile private var instance: Db? = null
        fun get(context: Context): Db =
            instance ?: synchronized(this) { instance ?: Db(context.applicationContext).also { instance = it } }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE questions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            created_at TEXT NOT NULL,
            image_path TEXT,
            question_text TEXT NOT NULL,
            category TEXT NOT NULL DEFAULT '其他',
            subtype TEXT DEFAULT '',
            knowledge_tags TEXT NOT NULL DEFAULT '[]',
            difficulty INTEGER NOT NULL DEFAULT 3,
            my_answer TEXT DEFAULT '',
            correct_answer TEXT DEFAULT '',
            analysis TEXT DEFAULT '',
            cause_ids TEXT NOT NULL DEFAULT '[]',
            status TEXT NOT NULL DEFAULT '待订正',
            hint_chain TEXT NOT NULL DEFAULT '[]',
            coaching TEXT DEFAULT '',
            note TEXT DEFAULT '',
            practice_count INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("""CREATE TABLE causes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE,
            category TEXT NOT NULL DEFAULT '习惯性',
            description TEXT DEFAULT '',
            strategy TEXT DEFAULT '',
            count INTEGER NOT NULL DEFAULT 0,
            last_seen_at TEXT,
            status TEXT NOT NULL DEFAULT '活跃',
            preset INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("""CREATE TABLE memories (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            content TEXT NOT NULL,
            kind TEXT NOT NULL DEFAULT 'pattern',
            weight INTEGER NOT NULL DEFAULT 1,
            created_at TEXT NOT NULL)""")
        db.execSQL("""CREATE TABLE practice_sets (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            created_at TEXT NOT NULL,
            title TEXT DEFAULT '',
            params TEXT DEFAULT '{}',
            items TEXT NOT NULL DEFAULT '[]',
            source_question_ids TEXT NOT NULL DEFAULT '[]')""")
        for (c in Taxonomy.PRESET_CAUSES) {
            db.insert("causes", null, ContentValues().apply {
                put("name", c.name); put("category", c.category)
                put("description", c.description); put("strategy", c.strategy); put("preset", 1)
            })
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    // ---------- questions ----------
    private fun rowToQuestion(c: android.database.Cursor): Question = Question(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        createdAt = c.getString(c.getColumnIndexOrThrow("created_at")),
        imagePath = c.getString(c.getColumnIndexOrThrow("image_path")),
        questionText = c.getString(c.getColumnIndexOrThrow("question_text")),
        category = c.getString(c.getColumnIndexOrThrow("category")),
        subtype = c.getString(c.getColumnIndexOrThrow("subtype")) ?: "",
        knowledgeTags = (c.getString(c.getColumnIndexOrThrow("knowledge_tags")) ?: "[]").fromJsonList(),
        difficulty = c.getInt(c.getColumnIndexOrThrow("difficulty")),
        myAnswer = c.getString(c.getColumnIndexOrThrow("my_answer")) ?: "",
        correctAnswer = c.getString(c.getColumnIndexOrThrow("correct_answer")) ?: "",
        analysis = c.getString(c.getColumnIndexOrThrow("analysis")) ?: "",
        causeIds = (c.getString(c.getColumnIndexOrThrow("cause_ids")) ?: "[]").fromJsonLongList(),
        status = c.getString(c.getColumnIndexOrThrow("status")),
        hintChain = (c.getString(c.getColumnIndexOrThrow("hint_chain")) ?: "[]").fromJsonList(),
        coaching = c.getString(c.getColumnIndexOrThrow("coaching")) ?: "",
        note = c.getString(c.getColumnIndexOrThrow("note")) ?: "",
        practiceCount = c.getInt(c.getColumnIndexOrThrow("practice_count")),
    )

    fun insertQuestion(q: Question): Long {
        val cv = ContentValues().apply {
            put("created_at", if (q.createdAt.isBlank()) nowStr() else q.createdAt)
            put("image_path", q.imagePath)
            put("question_text", q.questionText)
            put("category", q.category)
            put("subtype", q.subtype)
            put("knowledge_tags", q.knowledgeTags.toJson())
            put("difficulty", q.difficulty)
            put("my_answer", q.myAnswer)
            put("correct_answer", q.correctAnswer)
            put("analysis", q.analysis)
            put("cause_ids", q.causeIds.map { it.toString() }.toJson())
            put("status", q.status)
            put("hint_chain", q.hintChain.toJson())
            put("coaching", q.coaching)
            put("note", q.note)
        }
        return writableDatabase.insert("questions", null, cv)
    }

    fun listQuestions(status: String? = null, category: String? = null, causeId: Long? = null, q: String? = null): List<Question> {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        status?.let { where.add("status = ?"); args.add(it) }
        category?.let { where.add("category = ?"); args.add(it) }
        causeId?.let { where.add("cause_ids LIKE ?"); args.add("%\"$it\"%") }
        q?.let { where.add("(question_text LIKE ? OR knowledge_tags LIKE ?)"); args.add("%$it%"); args.add("%$it%") }
        val sql = "SELECT * FROM questions ${if (where.isEmpty()) "" else "WHERE " + where.joinToString(" AND ")} ORDER BY id DESC"
        val out = mutableListOf<Question>()
        readableDatabase.rawQuery(sql, args.toTypedArray()).use { c ->
            while (c.moveToNext()) out.add(rowToQuestion(c))
        }
        return out
    }

    fun getQuestion(id: Long): Question? {
        readableDatabase.rawQuery("SELECT * FROM questions WHERE id = ?", arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) rowToQuestion(c) else null
        }
    }

    fun updateQuestionStatus(id: Long, status: String) {
        writableDatabase.execSQL("UPDATE questions SET status = ? WHERE id = ?", arrayOf(status, id))
    }

    fun updateQuestionCauses(id: Long, causeIds: List<Long>, prevCauseIds: List<Long>) {
        writableDatabase.execSQL(
            "UPDATE questions SET cause_ids = ? WHERE id = ?",
            arrayOf(causeIds.map { it.toString() }.toJson(), id)
        )
        for (cid in causeIds) {
            if (cid !in prevCauseIds) bumpCause(cid)
        }
    }

    fun deleteQuestion(id: Long) {
        writableDatabase.delete("questions", "id = ?", arrayOf(id.toString()))
    }

    fun bumpPracticeCount(id: Long) {
        writableDatabase.execSQL("UPDATE questions SET practice_count = practice_count + 1 WHERE id = ?", arrayOf(id))
    }

    fun countByStatus(status: String): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM questions WHERE status = ?", arrayOf(status)).use { c ->
            c.moveToFirst(); return c.getInt(0)
        }
    }

    fun countAll(table: String): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use { c ->
            c.moveToFirst(); return c.getInt(0)
        }
    }

    // ---------- causes ----------
    private fun rowToCause(c: android.database.Cursor): Cause = Cause(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        name = c.getString(c.getColumnIndexOrThrow("name")),
        category = c.getString(c.getColumnIndexOrThrow("category")),
        description = c.getString(c.getColumnIndexOrThrow("description")) ?: "",
        strategy = c.getString(c.getColumnIndexOrThrow("strategy")) ?: "",
        count = c.getInt(c.getColumnIndexOrThrow("count")),
        lastSeenAt = c.getString(c.getColumnIndexOrThrow("last_seen_at")),
        preset = c.getInt(c.getColumnIndexOrThrow("preset")),
    )

    fun listCauses(): List<Cause> {
        val out = mutableListOf<Cause>()
        readableDatabase.rawQuery("SELECT * FROM causes ORDER BY preset DESC, count DESC, id ASC", null).use { c ->
            while (c.moveToNext()) out.add(rowToCause(c))
        }
        return out
    }

    fun findCauseByName(name: String): Cause? {
        readableDatabase.rawQuery("SELECT * FROM causes WHERE name = ?", arrayOf(name)).use { c ->
            return if (c.moveToFirst()) rowToCause(c) else null
        }
    }

    fun insertCause(name: String, category: String, description: String): Long {
        return writableDatabase.insert("causes", null, ContentValues().apply {
            put("name", name); put("category", category); put("description", description); put("preset", 0)
        })
    }

    fun bumpCause(id: Long) {
        writableDatabase.execSQL("UPDATE causes SET count = count + 1, last_seen_at = ? WHERE id = ?", arrayOf(nowStr(), id))
    }

    // ---------- memories ----------
    fun listMemories(): List<MemoryItem> {
        val out = mutableListOf<MemoryItem>()
        readableDatabase.rawQuery("SELECT * FROM memories ORDER BY weight DESC, id DESC", null).use { c ->
            while (c.moveToNext()) out.add(
                MemoryItem(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    content = c.getString(c.getColumnIndexOrThrow("content")),
                    kind = c.getString(c.getColumnIndexOrThrow("kind")),
                    weight = c.getInt(c.getColumnIndexOrThrow("weight")),
                    createdAt = c.getString(c.getColumnIndexOrThrow("created_at")),
                )
            )
        }
        return out
    }

    fun insertMemory(content: String, kind: String, weight: Int): Long {
        return writableDatabase.insert("memories", null, ContentValues().apply {
            put("content", content); put("kind", kind); put("weight", weight); put("created_at", nowStr())
        })
    }

    fun upsertMemoryByFragments(frag1: String, frag2: String, content: String, weight: Int) {
        val db = writableDatabase
        var id: Long = -1
        db.rawQuery("SELECT id FROM memories WHERE content LIKE ? AND content LIKE ?", arrayOf("%$frag1%", "%$frag2%")).use { c ->
            if (c.moveToFirst()) id = c.getLong(0)
        }
        if (id > 0) {
            db.execSQL("UPDATE memories SET content = ?, weight = ? WHERE id = ?", arrayOf(content, weight, id))
        } else {
            insertMemory(content, "pattern", weight)
        }
    }

    fun deleteMemory(id: Long) {
        writableDatabase.delete("memories", "id = ?", arrayOf(id.toString()))
    }

    // ---------- practice sets ----------
    fun insertPracticeSet(title: String, items: List<PracticeItem>, sourceIds: List<Long>): Long {
        val itemsJson = JSONArray().apply {
            items.forEach {
                put(org.json.JSONObject().apply {
                    put("stem", it.stem); put("answer", it.answer); put("solution", it.solution)
                    put("targeted_cause", it.targetedCause); put("difficulty", it.difficulty); put("variant", it.variant)
                })
            }
        }.toString()
        return writableDatabase.insert("practice_sets", null, ContentValues().apply {
            put("created_at", nowStr()); put("title", title); put("items", itemsJson)
            put("source_question_ids", sourceIds.map { it.toString() }.toJson())
        })
    }

    fun listPracticeSets(): List<PracticeSet> {
        val out = mutableListOf<PracticeSet>()
        readableDatabase.rawQuery("SELECT * FROM practice_sets ORDER BY id DESC", null).use { c ->
            while (c.moveToNext()) out.add(rowToSet(c))
        }
        return out
    }

    fun getPracticeSet(id: Long): PracticeSet? {
        readableDatabase.rawQuery("SELECT * FROM practice_sets WHERE id = ?", arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) rowToSet(c) else null
        }
    }

    private fun rowToSet(c: android.database.Cursor): PracticeSet {
        val itemsJson = c.getString(c.getColumnIndexOrThrow("items")) ?: "[]"
        val arr = JSONArray(itemsJson)
        val items = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            PracticeItem(
                stem = o.optString("stem"), answer = o.optString("answer"), solution = o.optString("solution"),
                targetedCause = o.optString("targeted_cause"), difficulty = o.optInt("difficulty", 3),
                variant = o.optString("variant", "基础"),
            )
        }
        return PracticeSet(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            createdAt = c.getString(c.getColumnIndexOrThrow("created_at")),
            title = c.getString(c.getColumnIndexOrThrow("title")) ?: "",
            items = items,
            sourceQuestionIds = (c.getString(c.getColumnIndexOrThrow("source_question_ids")) ?: "[]").fromJsonLongList(),
        )
    }

    // ---------- 画像 ----------
    fun recentQuestionsWithTagByCause(causeId: Long, limit: Int = 20): List<Question> =
        listQuestions(causeId = causeId).take(limit)

    fun recentQuestions(limit: Int = 8): List<Question> = listQuestions().take(limit)
}
