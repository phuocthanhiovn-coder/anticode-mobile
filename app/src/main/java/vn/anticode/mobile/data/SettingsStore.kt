package vn.anticode.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

object SettingsStore {
    private val API_KEY = stringPreferencesKey("api_key")
    private val BASE_URL = stringPreferencesKey("base_url")
    private val SELECTED_MODEL = stringPreferencesKey("selected_model")

    fun getApiKey(context: Context): Flow<String> =
        context.dataStore.data.map { it[API_KEY] ?: "" }

    fun getBaseUrl(context: Context): Flow<String> =
        context.dataStore.data.map { it[BASE_URL] ?: "https://anticode.vn" }

    fun getSelectedModel(context: Context): Flow<String> =
        context.dataStore.data.map { it[SELECTED_MODEL] ?: "claude-sonnet-4-6" }

    suspend fun setApiKey(context: Context, key: String) {
        context.dataStore.edit { it[API_KEY] = key }
    }

    suspend fun setBaseUrl(context: Context, url: String) {
        context.dataStore.edit { it[BASE_URL] = url }
    }

    suspend fun setSelectedModel(context: Context, model: String) {
        context.dataStore.edit { it[SELECTED_MODEL] = model }
    }
}
