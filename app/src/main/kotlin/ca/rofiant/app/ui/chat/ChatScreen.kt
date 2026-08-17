package ca.rofiant.app.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import ca.rofiant.app.data.auth.AuthState
import ca.rofiant.app.data.local.AudioRecorder
import ca.rofiant.app.data.model.ChatModels
import ca.rofiant.app.data.model.Role

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: AppViewModel,
    isOnline: Boolean,
    onOpenDrawer: () -> Unit,
) {
    val conversation by viewModel.activeConversation.collectAsStateWithLifecycle()
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
    val isTranscribing by viewModel.isTranscribing.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var draft by remember { mutableStateOf("") }
    var pendingImageDataUrl by remember { mutableStateOf<String?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var micLevel by remember { mutableStateOf(0f) }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current
    val audioRecorder = remember { AudioRecorder(context) }
    val recordPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            isRecording = true
            audioRecorder.start()
        }
    }
    fun toggleRecording() {
        if (isRecording) {
            isRecording = false
            audioRecorder.stop()?.let { file ->
                viewModel.transcribeVoice(file) { text ->
                    draft = if (draft.isBlank()) text else "$draft $text"
                }
            }
        } else {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) {
                isRecording = true
                audioRecorder.start()
            } else {
                recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Polls MediaRecorder's peak-since-last-read amplitude so the mic button can
    // show the user their voice is actually being picked up while recording.
    LaunchedEffect(isRecording) {
        if (!isRecording) {
            micLevel = 0f
            return@LaunchedEffect
        }
        while (isRecording) {
            micLevel = (audioRecorder.amplitude() / 32767f).coerceIn(0f, 1f)
            delay(100)
        }
    }

    LaunchedEffect(conversation?.messages?.size, conversation?.messages?.lastOrNull()?.content) {
        val count = conversation?.messages?.size ?: 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    val canSend = authState is AuthState.SignedIn

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Filled.Menu, contentDescription = "Conversations")
                        }
                    },
                    title = { Text("Rofiant", fontWeight = FontWeight.SemiBold, maxLines = 1) },
                    actions = {
                        IconButton(onClick = { viewModel.newConversation() }) {
                            Icon(Icons.Filled.EditNote, contentDescription = "New chat")
                        }
                    },
                )
                if (!isOnline) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        OfflineBanner()
                    }
                }
            }
        },
        bottomBar = {
            Composer(
                text = draft,
                onTextChange = { draft = it },
                onSend = {
                    if (draft.isNotBlank() || pendingImageDataUrl != null) {
                        viewModel.sendMessage(draft, pendingImageDataUrl)
                        draft = ""
                        pendingImageDataUrl = null
                    }
                },
                isStreaming = isStreaming,
                onStop = { viewModel.stopStreaming() },
                enabled = canSend && isOnline,
                pendingImageDataUrl = pendingImageDataUrl,
                onImagePicked = { pendingImageDataUrl = it },
                isRecording = isRecording,
                micLevel = micLevel,
                isTranscribing = isTranscribing,
                onToggleRecording = ::toggleRecording,
                showEffort = ChatModels.byId(settings.model)?.supportsEffort == true,
                effort = settings.reasoningEffort,
                onEffortSelected = viewModel::setEffort,
            )
        },
    ) { padding ->
        val messages = conversation?.messages.orEmpty()
        if (messages.isEmpty()) {
            EmptyState(
                modifier = Modifier.fillMaxSize().padding(padding),
                enabled = canSend && isOnline,
                onPromptSelected = { prompt ->
                    viewModel.sendMessage(prompt, null)
                },
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubbleView(
                        message = message,
                        showTimestamp = settings.showTimestamps,
                        showRegenerate = message.id == messages.lastOrNull { it.role == Role.assistant }?.id,
                        onRetry = { conversation?.let { viewModel.retry(it.id) } },
                    )
                }
                if (isStreaming && messages.lastOrNull()?.content.isNullOrEmpty()) {
                    item(key = "typing") { TypingIndicator(modifier = Modifier.padding(vertical = 4.dp)) }
                }
            }
        }
    }
}

@Composable
private fun OfflineBanner() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.CloudOff, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text("You're offline — messages will fail to send", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun EmptyState(
    enabled: Boolean,
    onPromptSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val prompts = listOf(
        "Help me plan my day",
        "Explain something clearly",
        "Brainstorm ideas with me",
    )
    Box(modifier = modifier.padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                "What can I help with?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                "Start with an idea, a question, or one of these prompts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                prompts.forEach { prompt ->
                    OutlinedButton(
                        onClick = { onPromptSelected(prompt) },
                        enabled = enabled,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(prompt)
                    }
                }
            }
        }
    }
}
