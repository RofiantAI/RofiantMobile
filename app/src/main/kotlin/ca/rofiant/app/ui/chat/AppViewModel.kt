package ca.rofiant.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ca.rofiant.app.AppContainer
import ca.rofiant.app.data.auth.AuthState
import ca.rofiant.app.data.model.AppSettings
import ca.rofiant.app.data.model.ChatMessage
import ca.rofiant.app.data.model.ChatModels
import ca.rofiant.app.data.model.Conversation
import ca.rofiant.app.data.model.ConversationStatus
import ca.rofiant.app.data.model.Role
import ca.rofiant.app.data.remote.ChatRequestMessage
import ca.rofiant.app.data.remote.ChatStreamEvent
import ca.rofiant.app.data.remote.stripThinkTags
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class AppViewModel(private val container: AppContainer) : ViewModel() {
    private val conversationsRepo = container.conversationsRepository
    private val settingsRepo = container.settingsRepository
    private val authRepo = container.authRepository

    val authState: StateFlow<AuthState> = authRepo.state

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    val activeConversation: StateFlow<Conversation?> =
        kotlinx.coroutines.flow.combine(_conversations, _activeId) { list, id -> list.find { it.id == id } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _authBusy = MutableStateFlow(false)
    val authBusy: StateFlow<Boolean> = _authBusy.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private var streamJob: Job? = null
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()

    init {
        viewModelScope.launch {
            authRepo.bootstrap()
            restoreFromCloudIfEmpty()
        }
        viewModelScope.launch {
            conversationsRepo.conversations.collectLatest { loaded ->
                _conversations.value = loaded
                if (_activeId.value == null) _activeId.value = loaded.firstOrNull()?.id
            }
        }
    }

    /** Reinstall/new-device restore: only pulls if this device has no local history yet, so it never clobbers an existing local conversation. */
    private suspend fun restoreFromCloudIfEmpty() {
        if (conversationsRepo.conversations.first().isNotEmpty()) return
        val token = authRepo.validAccessToken() ?: return
        val pulled = runCatching { container.chatSyncApi.pullConversations(token) }.getOrNull()
        if (!pulled.isNullOrEmpty()) conversationsRepo.save(pulled)
    }

    fun selectConversation(id: String) {
        _activeId.value = id
    }

    fun exportConversationsJson(list: List<Conversation>): String = conversationsRepo.exportJson(list)

    fun newConversation() {
        val convo = Conversation(id = UUID.randomUUID().toString(), title = "New chat", updatedAt = System.currentTimeMillis())
        _conversations.value = listOf(convo) + _conversations.value
        _activeId.value = convo.id
        persist()
        syncPush(convo.id)
    }

    fun deleteConversation(id: String) {
        _conversations.value = _conversations.value.filterNot { it.id == id }
        if (_activeId.value == id) _activeId.value = _conversations.value.firstOrNull()?.id
        persist()
        syncDelete(id)
    }

    fun clearAllConversations() {
        streamJob?.cancel()
        _conversations.value = emptyList()
        _activeId.value = null
        persist()
        syncDeleteAll()
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
        _isStreaming.value = false
        updateActive { it.copy(status = ConversationStatus.idle) }
    }

    fun sendMessage(text: String, imageDataUrl: String? = null) {
        val trimmed = text.trim()
        if ((trimmed.isEmpty() && imageDataUrl == null) || _isStreaming.value) return

        if (imageDataUrl != null && !ChatModels.isVisionModel(settings.value.model)) {
            _errorMessage.value = "Switch to a vision-capable model (Qwen 3.6 27B or Nemotron 3 Nano Omni) to send images."
            return
        }

        val convo = activeConversation.value ?: run {
            val fresh = Conversation(id = UUID.randomUUID().toString(), title = "New chat", updatedAt = System.currentTimeMillis())
            _conversations.value = listOf(fresh) + _conversations.value
            _activeId.value = fresh.id
            fresh
        }

        val isFirstMessage = convo.messages.isEmpty()
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = Role.user,
            content = trimmed,
            createdAt = System.currentTimeMillis(),
            imageDataUrl = imageDataUrl,
        )
        val titled = if (isFirstMessage) trimmed.take(40).ifBlank { "Image" } else convo.title
        replaceConversation(convo.copy(messages = convo.messages + userMessage, title = titled, updatedAt = System.currentTimeMillis()))
        runGeneration(convo.id)
        if (isFirstMessage && trimmed.isNotBlank()) generateTitle(convo.id, trimmed)
    }

    /** Records via [ca.rofiant.app.data.local.AudioRecorder], transcribes, and hands the text back — the file is always cleaned up. */
    fun transcribeVoice(file: File, onResult: (String) -> Unit) {
        viewModelScope.launch {
            _isTranscribing.value = true
            try {
                val token = authRepo.validAccessToken()
                if (token == null) {
                    _errorMessage.value = "Sign in to use voice input."
                    return@launch
                }
                val text = container.chatApi.transcribeAudio(token, file, "audio/m4a")
                if (!text.isNullOrBlank()) onResult(text)
            } finally {
                file.delete()
                _isTranscribing.value = false
            }
        }
    }

    /** Fire-and-forget, same as rofiant-desktop's generateTitle call in App.tsx — runs alongside the reply, not before it. */
    private fun generateTitle(conversationId: String, userText: String) {
        viewModelScope.launch {
            val token = authRepo.validAccessToken() ?: return@launch
            val title = container.chatApi.generateTitle(token, userText) ?: return@launch
            updateConversation(conversationId) { it.copy(title = title) }
            persist()
            syncPush(conversationId)
        }
    }

    fun retry(conversationId: String) {
        val convo = _conversations.value.find { it.id == conversationId } ?: return
        val lastUser = convo.messages.lastOrNull { it.role == Role.user } ?: return
        val trimmedMessages = convo.messages.takeWhile { it.id != lastUser.id } + lastUser
        replaceConversation(convo.copy(messages = trimmedMessages))
        runGeneration(conversationId)
    }

    private fun runGeneration(conversationId: String) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            _isStreaming.value = true
            updateConversation(conversationId) { it.copy(status = ConversationStatus.running) }

            val token = authRepo.validAccessToken()
            if (token == null) {
                _errorMessage.value = "Sign in to send messages."
                updateConversation(conversationId) { it.copy(status = ConversationStatus.idle) }
                _isStreaming.value = false
                return@launch
            }

            val s = settings.value
            val convo = _conversations.value.find { it.id == conversationId }
            if (convo == null) {
                _isStreaming.value = false
                return@launch
            }

            var systemPrompt = "You are Rofiant, a helpful, concise AI assistant."
            if (s.customInstructions.isNotBlank()) systemPrompt += "\n\n${s.customInstructions}"
            val modelSupportsVision = ChatModels.isVisionModel(s.model)
            val history = convo.messages.takeLast(s.contextLimit).map {
                ChatRequestMessage(
                    role = it.role.name,
                    content = it.content,
                    imageDataUrl = it.imageDataUrl.takeIf { modelSupportsVision },
                )
            }
            val requestMessages = listOf(ChatRequestMessage("system", systemPrompt)) + history

            val assistantId = UUID.randomUUID().toString()
            var buffer = StringBuilder()

            // Placeholder goes in before the request even fires — the empty
            // bubble is what drives the typing indicator (see ChatScreen's
            // isStreaming + empty-content check), and every later Delta
            // updates this same message in place so content streams into
            // view token-by-token instead of appearing all at once on Done.
            updateConversation(conversationId) { c ->
                c.copy(messages = c.messages + ChatMessage(assistantId, Role.assistant, "", System.currentTimeMillis()))
            }

            val edgeFunction = ChatModels.byId(s.model)?.edgeFunction ?: "groq-proxy"

            container.chatApi.streamChat(
                accessToken = token,
                model = s.model,
                messages = requestMessages,
                reasoningEffort = s.reasoningEffort,
                edgeFunction = edgeFunction,
            ).collect { event ->
                when (event) {
                    is ChatStreamEvent.Delta -> {
                        buffer.append(event.text)
                        val clean = stripThinkTags(buffer.toString())
                        updateConversation(conversationId) { c ->
                            c.copy(messages = c.messages.map { if (it.id == assistantId) it.copy(content = clean) else it })
                        }
                    }
                    is ChatStreamEvent.Usage -> Unit
                    ChatStreamEvent.Done -> {
                        updateConversation(conversationId) { it.copy(status = ConversationStatus.done) }
                        if (buffer.isEmpty()) {
                            // stream ended with no content — surface as an error bubble rather than a silent no-op
                            updateConversation(conversationId) { c ->
                                c.copy(
                                    messages = c.messages.map {
                                        if (it.id == assistantId) it.copy(content = "No response received.", error = true) else it
                                    },
                                )
                            }
                        }
                        _isStreaming.value = false
                        persist()
                        syncPush(conversationId)
                    }
                    is ChatStreamEvent.Error -> {
                        updateConversation(conversationId) { c ->
                            val withoutPartial = c.messages.filterNot { it.id == assistantId }
                            c.copy(
                                status = ConversationStatus.idle,
                                messages = withoutPartial + ChatMessage(assistantId, Role.assistant, event.message, System.currentTimeMillis(), error = true),
                            )
                        }
                        _errorMessage.value = event.message
                        _isStreaming.value = false
                        persist()
                        syncPush(conversationId)
                    }
                }
            }
        }
    }

    fun dismissAuthError() {
        _authError.value = null
    }

    val rememberedEmail: String? get() = authRepo.rememberedEmail()

    fun signInWithPassword(email: String, password: String, rememberMe: Boolean) =
        runAuthAction(onSuccess = { authRepo.setRememberedEmail(if (rememberMe) email else null) }) {
            authRepo.signInWithPassword(email, password)
        }

    fun signInAnonymously() = runAuthAction {
        authRepo.signInAnonymously()
    }

    fun signUp(email: String, password: String, onNeedsConfirmation: () -> Unit) = runAuthAction {
        if (!authRepo.signUp(email, password)) onNeedsConfirmation()
    }

    fun requestPasswordReset(email: String, onSent: () -> Unit) = runAuthAction(onSuccess = onSent) {
        authRepo.requestPasswordReset(email)
    }

    fun confirmPasswordReset(email: String, code: String, newPassword: String) = runAuthAction {
        authRepo.confirmPasswordReset(email, code, newPassword)
    }

    fun startGoogleOAuth(): String = authRepo.startGoogleOAuth()

    fun handleOAuthRedirect(uri: android.net.Uri) = runAuthAction {
        authRepo.handleOAuthRedirect(uri)
    }

    fun verifyMfa(code: String) = runAuthAction {
        authRepo.verifyMfa(code)
    }

    fun signOut() {
        authRepo.signOut()
    }

    private fun runAuthAction(onSuccess: (() -> Unit)? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            _authBusy.value = true
            _authError.value = null
            try {
                block()
                onSuccess?.invoke()
            } catch (e: Exception) {
                _authError.value = e.message ?: "Something went wrong"
            } finally {
                _authBusy.value = false
            }
        }
    }

    fun setTheme(theme: ca.rofiant.app.data.model.AppTheme) = viewModelScope.launch { settingsRepo.setTheme(theme) }
    fun setShowTimestamps(show: Boolean) = viewModelScope.launch { settingsRepo.setShowTimestamps(show) }
    fun setCustomInstructions(text: String) = viewModelScope.launch { settingsRepo.setCustomInstructions(text) }
    fun setContextLimit(limit: Int) = viewModelScope.launch { settingsRepo.setContextLimit(limit) }
    fun setModel(id: String) = viewModelScope.launch { settingsRepo.setModel(id) }
    fun setEffort(level: String) = viewModelScope.launch { settingsRepo.setEffort(level) }

    fun setDisplayName(name: String) = runAuthAction { authRepo.updateProfile(displayName = name) }
    fun uploadAvatar(jpegBytes: ByteArray) = runAuthAction { authRepo.uploadAvatar(jpegBytes) }
    /** Scoped result callback rather than runAuthAction's shared authError banner — this is a one-off scan action with its own toast, not a form field. */
    fun linkDevice(code: String, onResult: (success: Boolean, errorMessage: String?) -> Unit) {
        viewModelScope.launch {
            try {
                authRepo.linkDevice(code)
                onResult(true, null)
            } catch (e: Exception) {
                onResult(false, e.message ?: "Something went wrong")
            }
        }
    }

    private fun updateActive(transform: (Conversation) -> Conversation) {
        val id = _activeId.value ?: return
        updateConversation(id, transform)
    }

    private fun updateConversation(id: String, transform: (Conversation) -> Conversation) {
        _conversations.value = _conversations.value.map { if (it.id == id) transform(it) else it }
    }

    private fun replaceConversation(updated: Conversation) {
        _conversations.value = _conversations.value.map { if (it.id == updated.id) updated else it }
        persist()
        syncPush(updated.id)
    }

    private fun persist() {
        viewModelScope.launch { conversationsRepo.save(_conversations.value) }
    }

    // Cloud backup is best-effort: failures (offline, signed out) are swallowed
    // silently since the local DataStore save via persist() is the source of
    // truth the rest of the app relies on.
    private fun syncPush(conversationId: String) {
        viewModelScope.launch {
            val userId = (authState.value as? AuthState.SignedIn)?.session?.user?.id ?: return@launch
            val token = authRepo.validAccessToken() ?: return@launch
            val conversation = _conversations.value.find { it.id == conversationId } ?: return@launch
            runCatching { container.chatSyncApi.pushConversation(token, userId, conversation) }
        }
    }

    private fun syncDelete(conversationId: String) {
        viewModelScope.launch {
            val token = authRepo.validAccessToken() ?: return@launch
            runCatching { container.chatSyncApi.deleteConversation(token, conversationId) }
        }
    }

    private fun syncDeleteAll() {
        viewModelScope.launch {
            val userId = (authState.value as? AuthState.SignedIn)?.session?.user?.id ?: return@launch
            val token = authRepo.validAccessToken() ?: return@launch
            runCatching { container.chatSyncApi.deleteAllConversations(token, userId) }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(container) as T
    }
}
