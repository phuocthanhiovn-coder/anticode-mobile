package vn.anticode.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

object SettingsStore {
    private val API_KEY = stringPreferencesKey("api_key")
    private val BASE_URL = stringPreferencesKey("base_url")
    private val SELECTED_MODEL = stringPreferencesKey("selected_model")
    private val EDITOR_FONT_SIZE = floatPreferencesKey("editor_font_size")
    private val CHAT_FONT_SIZE = floatPreferencesKey("chat_font_size")

    fun getApiKey(context: Context): Flow<String> =
        context.dataStore.data.map { it[API_KEY] ?: "" }

    fun getBaseUrl(context: Context): Flow<String> =
        context.dataStore.data.map { it[BASE_URL] ?: "https://anticode.vn" }

    fun getSelectedModel(context: Context): Flow<String> =
        context.dataStore.data.map { it[SELECTED_MODEL] ?: "claude-sonnet-4-6" }

    fun getEditorFontSize(context: Context): Flow<Float> =
        context.dataStore.data.map { it[EDITOR_FONT_SIZE] ?: 13f }

    fun getChatFontSize(context: Context): Flow<Float> =
        context.dataStore.data.map { it[CHAT_FONT_SIZE] ?: 13f }

    suspend fun setApiKey(context: Context, key: String) {
        context.dataStore.edit { it[API_KEY] = key }
    }

    suspend fun setBaseUrl(context: Context, url: String) {
        context.dataStore.edit { it[BASE_URL] = url }
    }

    suspend fun setSelectedModel(context: Context, model: String) {
        context.dataStore.edit { it[SELECTED_MODEL] = model }
    }

    suspend fun setEditorFontSize(context: Context, size: Float) {
        context.dataStore.edit { it[EDITOR_FONT_SIZE] = size }
    }

    suspend fun setChatFontSize(context: Context, size: Float) {
        context.dataStore.edit { it[CHAT_FONT_SIZE] = size }
    }
}
