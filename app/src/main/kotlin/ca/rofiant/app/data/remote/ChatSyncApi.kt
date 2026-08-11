package ca.rofiant.app.data.remote

import ca.rofiant.app.data.auth.AuthConfig
import ca.rofiant.app.data.model.ChatMessage
import ca.rofiant.app.data.model.Conversation
import ca.rofiant.app.data.model.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant

@Serializable
private data class ConversationRow(
    val id: String,
    val title: String,
    val pinned: Boolean = false,
    val updated_at: String? = null,
)

@Serializable
private data class MessageRow(
    val id: String,
    val conversation_id: String,
    val role: String,
    val content: String,
    val created_at: String? = null,
)

@Serializable
private data class ConversationUpsert(val id: String, val user_id: String, val title: String, val pinned: Boolean, val updated_at: String)

@Serializable
private data class MessageInsert(val id: String, val conversation_id: String, val role: String, val content: String, val created_at: String)

/**
 * Cloud backup for conversations, against the same `conversations`/`messages`
 * Postgres tables rofiant-web reads for export/analytics (see
 * rofiant-web/supabase/migrations/001_chat.sql). Desktop doesn't write here
 * either (it's localStorage-only, same as this app's ConversationsRepository)
 * so this isn't cross-device sync with desktop yet — it's mobile's own
 * history surviving a reinstall or new device, and a step toward it.
 *
 * No column exists for durationMs/error/imageDataUrl/status, so those fields
 * don't round-trip — a conversation restored from the cloud has plain
 * text messages only.
 */
class ChatSyncApi(private val client: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json".toMediaType()
    private val restBase = "${AuthConfig.SUPABASE_URL}/rest/v1"

    suspend fun pullConversations(accessToken: String): List<Conversation> = withContext(Dispatchers.IO) {
        val convoRows = json.decodeFromString<List<ConversationRow>>(
            execute(getRequest(accessToken, "$restBase/conversations?select=id,title,pinned,updated_at&order=updated_at.desc"))
        )
        if (convoRows.isEmpty()) return@withContext emptyList()

        val ids = convoRows.joinToString(",") { it.id }
        val messageRows = json.decodeFromString<List<MessageRow>>(
            execute(
                getRequest(
                    accessToken,
                    "$restBase/messages?conversation_id=in.($ids)&select=id,conversation_id,role,content,created_at&order=created_at.asc",
                )
            )
        )
        val messagesByConversation = messageRows.groupBy { it.conversation_id }

        convoRows.map { row ->
            Conversation(
                id = row.id,
                title = row.title,
                pinned = row.pinned,
                updatedAt = parseTimestamp(row.updated_at),
                messages = messagesByConversation[row.id].orEmpty().map { m ->
                    ChatMessage(id = m.id, role = Role.valueOf(m.role), content = m.content, createdAt = parseTimestamp(m.created_at))
                },
            )
        }
    }

    /** Upserts the conversation row, then replaces its messages wholesale (handles edits/retries dropping trailing messages, not just appends). */
    suspend fun pushConversation(accessToken: String, userId: String, conversation: Conversation) = withContext(Dispatchers.IO) {
        val convoBody = json.encodeToString(
            ListSerializer(ConversationUpsert.serializer()),
            listOf(
                ConversationUpsert(
                    id = conversation.id,
                    user_id = userId,
                    title = conversation.title,
                    pinned = conversation.pinned,
                    updated_at = formatTimestamp(conversation.updatedAt),
                )
            ),
        )
        execute(
            Request.Builder()
                .url("$restBase/conversations?on_conflict=id")
                .authHeaders(accessToken)
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(convoBody.toRequestBody(jsonMedia))
                .build()
        )

        execute(
            Request.Builder()
                .url("$restBase/messages?conversation_id=eq.${conversation.id}")
                .authHeaders(accessToken)
                .delete()
                .build()
        )
        if (conversation.messages.isNotEmpty()) {
            val messagesBody = json.encodeToString(
                ListSerializer(MessageInsert.serializer()),
                conversation.messages.map {
                    MessageInsert(
                        id = it.id,
                        conversation_id = conversation.id,
                        role = it.role.name,
                        content = it.content,
                        created_at = formatTimestamp(it.createdAt),
                    )
                },
            )
            execute(
                Request.Builder()
                    .url("$restBase/messages")
                    .authHeaders(accessToken)
                    .header("Prefer", "return=minimal")
                    .post(messagesBody.toRequestBody(jsonMedia))
                    .build()
            )
        }
    }

    /** Messages cascade-delete with the conversation (FK ON DELETE CASCADE). */
    suspend fun deleteConversation(accessToken: String, id: String) = withContext(Dispatchers.IO) {
        execute(Request.Builder().url("$restBase/conversations?id=eq.$id").authHeaders(accessToken).delete().build())
    }

    suspend fun deleteAllConversations(accessToken: String, userId: String) = withContext(Dispatchers.IO) {
        execute(Request.Builder().url("$restBase/conversations?user_id=eq.$userId").authHeaders(accessToken).delete().build())
    }

    private fun getRequest(accessToken: String, url: String) = Request.Builder().url(url).authHeaders(accessToken).get().build()

    private fun Request.Builder.authHeaders(accessToken: String): Request.Builder =
        header("apikey", AuthConfig.SUPABASE_ANON_KEY).header("Authorization", "Bearer $accessToken")

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Request failed (${response.code}): ${response.body?.string()}")
            return response.body?.string().orEmpty()
        }
    }

    private fun parseTimestamp(iso: String?): Long = iso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        ?: System.currentTimeMillis()

    private fun formatTimestamp(millis: Long): String = Instant.ofEpochMilli(millis).toString()
}
