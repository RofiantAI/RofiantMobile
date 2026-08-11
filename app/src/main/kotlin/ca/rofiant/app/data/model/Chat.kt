package ca.rofiant.app.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class Role { user, assistant }

@Serializable
data class ChatMessage(
    val id: String,
    val role: Role,
    val content: String,
    val createdAt: Long,
    val durationMs: Long? = null,
    val error: Boolean = false,
    val imageDataUrl: String? = null,
)

@Serializable
enum class ConversationStatus { idle, running, done }

@Serializable
data class Conversation(
    val id: String,
    val title: String,
    val messages: List<ChatMessage> = emptyList(),
    val updatedAt: Long,
    val pinned: Boolean = false,
    val status: ConversationStatus = ConversationStatus.idle,
)
