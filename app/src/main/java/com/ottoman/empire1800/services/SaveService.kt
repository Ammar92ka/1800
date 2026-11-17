package com.ottoman.empire1800.services

import android.content.Context
import com.ottoman.empire1800.models.GameState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** حفظ واستعادة اللعبة محليا على الجهاز باستخدام SharedPreferences. */
class SaveService(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun save(data: JSONObject) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_SAVE, data.toString()).apply()
    }

    suspend fun load(): JSONObject? = withContext(Dispatchers.IO) {
        val s = prefs.getString(KEY_SAVE, null) ?: return@withContext null
        try {
            JSONObject(s)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveState(state: GameState) = save(state.toJson())

    suspend fun loadState(): GameState? {
        val data = load() ?: return null
        return try {
            GameState.fromJson(data)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_SAVE).apply()
    }

    fun exportJson(state: GameState): String = state.toJson().toString()

    companion object {
        private const val PREFS_NAME = "ottoman_empire_prefs"
        private const val KEY_SAVE = "ottoman_save"
    }
}
