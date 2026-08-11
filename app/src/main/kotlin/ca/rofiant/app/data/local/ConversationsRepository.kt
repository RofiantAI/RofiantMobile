package ca.rofiant.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ca.rofiant.app.data.model.Conversation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.conversationsDataStore: DataStore<Preferences> by preferencesDataStore(name = "rofiant_conversations")

/**
 * Single JSON-blob-per-key persistence, same shape as rofiant-desktop's
 * localStorage-backed src/lib/conversations.ts — this is always the source
 * of truth device-side. AppViewModel additionally best-effort mirrors writes
 * to Supabase (see ChatSyncApi) as a cloud backup; that sync is one-way from
 * mobile today, not shared with desktop, which still only reads localStorage.
 */
class ConversationsRepository(private val context: Context) {
    private val key = stringPreferencesKey("conversations_json")
    private val json = Json { ignoreUnknownKeys = true }
    private val prettyJson = Json { prettyPrint = true }

    val conversations: Flow<List<Conversation>> = context.conversationsDataStore.data.map { prefs ->
        val raw = prefs[key] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<Conversation>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun save(conversations: List<Conversation>) {
        context.conversationsDataStore.edit { it[key] = json.encodeToString(conversations) }
    }

    /** Same shape as rofiant-desktop's "Export conversations" (Settings > Data), just pretty-printed for a human reading the file. */
    fun exportJson(conversations: List<Conversation>): String = prettyJson.encodeToString(conversations)
}
