package com.xjb.linti.data.prefs

import android.content.Context
import com.xjb.linti.domain.PracticeMode
import com.xjb.linti.domain.PracticeSessionDescriptor
import org.json.JSONArray
import org.json.JSONObject

interface PracticeSessionStore {
    fun load(): PracticeSessionDescriptor?
    fun save(descriptor: PracticeSessionDescriptor)
    fun clear()
}

class PrefsPracticeSessionStore(context: Context) : PracticeSessionStore {
    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun load(): PracticeSessionDescriptor? = runCatching {
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        decode(raw)
    }.getOrNull()

    override fun save(descriptor: PracticeSessionDescriptor) {
        prefs.edit().putString(KEY_SESSION, encode(descriptor)).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    companion object {
        private const val FILE_NAME = "linti_practice_session"
        private const val KEY_SESSION = "last_session"

        fun encode(descriptor: PracticeSessionDescriptor): String = JSONObject()
            .put("version", 1)
            .put("mode", descriptor.mode.name)
            .put("bankId", descriptor.bankId ?: JSONObject.NULL)
            .put("bankName", descriptor.bankName)
            .put("paperId", descriptor.paperId ?: JSONObject.NULL)
            .put("questionIds", JSONArray(descriptor.questionIds))
            .put("currentIndex", descriptor.currentIndex)
            .put("updatedAt", descriptor.updatedAt)
            .toString()

        fun decode(raw: String): PracticeSessionDescriptor {
            val json = JSONObject(raw)
            val mode = PracticeMode.valueOf(json.getString("mode"))
            require(mode != PracticeMode.EXAM && mode != PracticeMode.FLASHCARD) {
                "不支持的练习模式：$mode"
            }
            val ids = json.getJSONArray("questionIds")
            return PracticeSessionDescriptor(
                mode = mode,
                bankId = if (json.isNull("bankId")) null else json.getLong("bankId"),
                bankName = json.getString("bankName"),
                paperId = if (json.isNull("paperId")) null else json.getLong("paperId"),
                questionIds = List(ids.length()) { ids.getLong(it) },
                currentIndex = json.getInt("currentIndex"),
                updatedAt = json.getLong("updatedAt"),
            )
        }
    }
}