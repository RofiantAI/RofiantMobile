package ca.rofiant.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import android.content.Context
import ca.rofiant.app.data.model.AppSettings
import ca.rofiant.app.data.model.AppTheme
import ca.rofiant.app.data.model.ChatModels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "rofiant_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val MODEL = stringPreferencesKey("model")
        val EFFORT = stringPreferencesKey("reasoning_effort")
        val CUSTOM_INSTRUCTIONS = stringPreferencesKey("custom_instructions")
        val CONTEXT_LIMIT = intPreferencesKey("context_limit")
        val THEME = stringPreferencesKey("theme")
        val SHOW_TIMESTAMPS = booleanPreferencesKey("show_timestamps")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            model = prefs[Keys.MODEL] ?: ChatModels.DEFAULT_MODEL,
            reasoningEffort = prefs[Keys.EFFORT] ?: ChatModels.DEFAULT_EFFORT,
            customInstructions = prefs[Keys.CUSTOM_INSTRUCTIONS] ?: "",
            contextLimit = prefs[Keys.CONTEXT_LIMIT] ?: 20,
            theme = prefs[Keys.THEME]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.system,
            showTimestamps = prefs[Keys.SHOW_TIMESTAMPS] ?: false,
        )
    }

    suspend fun setModel(id: String) = context.settingsDataStore.edit { it[Keys.MODEL] = id }
    suspend fun setEffort(level: String) = context.settingsDataStore.edit { it[Keys.EFFORT] = level }
    suspend fun setCustomInstructions(text: String) =
        context.settingsDataStore.edit { it[Keys.CUSTOM_INSTRUCTIONS] = text }
    suspend fun setContextLimit(limit: Int) = context.settingsDataStore.edit { it[Keys.CONTEXT_LIMIT] = limit }
    suspend fun setTheme(theme: AppTheme) = context.settingsDataStore.edit { it[Keys.THEME] = theme.name }
    suspend fun setShowTimestamps(show: Boolean) =
        context.settingsDataStore.edit { it[Keys.SHOW_TIMESTAMPS] = show }
}
