package com.ottoman.empire1800.services

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AiSettings(
    val provider: String = "gemini",
    val apiKey: String = "",
    val model: String = "",
    val useSearch: Boolean = false,
) {
    val hasKey: Boolean get() = apiKey.trim().isNotEmpty()

    val effectiveModel: String
        get() = if (model.trim().isNotEmpty()) model.trim() else aiProviderById(provider).defaultModel
}

class SettingsService(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun load(): AiSettings = withContext(Dispatchers.IO) {
        AiSettings(
            provider = prefs.getString(KEY_PROVIDER, "gemini") ?: "gemini",
            apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
            model = prefs.getString(KEY_MODEL, "") ?: "",
            useSearch = prefs.getBoolean(KEY_SEARCH, false),
        )
    }

    suspend fun save(s: AiSettings) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_PROVIDER, s.provider)
            .putString(KEY_API_KEY, s.apiKey.trim())
            .putString(KEY_MODEL, s.model.trim())
            .putBoolean(KEY_SEARCH, s.useSearch)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "ottoman_ai_prefs"
        private const val KEY_PROVIDER = "ai_provider"
        private const val KEY_API_KEY = "ai_api_key"
        private const val KEY_MODEL = "ai_model"
        private const val KEY_SEARCH = "ai_search"
    }
}
