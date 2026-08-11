package ca.rofiant.app.ui.chat

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.core.animateFloatAsState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ca.rofiant.app.data.local.uriToImageDataUrl
import ca.rofiant.app.data.model.ChatModels
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Single-row pill, like the ChatGPT app's composer: "+" to attach an image
// (real feature — the vision model accepts it, same as rofiant-desktop),
// text field, mic (dictation via Whisper, same as rofiant-desktop's
// transcribe_audio), and send. A fixed (not percent/stadium) corner radius
// is what keeps it a true pill at one line and a rounded rectangle rather
// than a ballooning oval once text wraps to more.
@Composable
fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isStreaming: Boolean,
    onStop: () -> Unit,
    enabled: Boolean,
    pendingImageDataUrl: String?,
    onImagePicked: (String?) -> Unit,
    isRecording: Boolean,
    micLevel: Float = 0f,
    isTranscribing: Boolean,
    onToggleRecording: () -> Unit,
    showEffort: Boolean,
    effort: String,
    onEffortSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch { onImagePicked(uriToImageDataUrl(context, uri)) }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth().navigationBarsPadding().imePadding(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                IconButton(
                    onClick = { pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = enabled,
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Attach image")
                }

                Column(modifier = Modifier.weight(1f)) {
                    if (pendingImageDataUrl != null) {
                        ImagePreviewChip(pendingImageDataUrl, onRemove = { onImagePicked(null) })
                    }
                    TextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                if (enabled) "Message Rofiant" else "Sign in to chat",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        enabled = enabled,
                        maxLines = 6,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                    )
                    if (showEffort) {
                        EffortPicker(
                            effort = effort,
                            enabled = enabled && !isStreaming,
                            onEffortSelected = onEffortSelected,
                        )
                    }
                }

                MicButton(
                    enabled = enabled && !isStreaming,
                    isRecording = isRecording,
                    micLevel = micLevel,
                    isTranscribing = isTranscribing,
                    onToggle = onToggleRecording,
                )
                SendButton(
                    hasText = text.isNotBlank() || pendingImageDataUrl != null,
                    isStreaming = isStreaming,
                    enabled = enabled,
                    onSend = onSend,
                    onStop = onStop,
                )
            }
        }
    }
}

@Composable
private fun ImagePreviewChip(dataUrl: String, onRemove: () -> Unit) {
    val bitmap = remember(dataUrl) {
        runCatching {
            val base64 = dataUrl.substringAfter(",", "")
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap == null) return
    Box(modifier = Modifier.padding(top = 8.dp, start = 4.dp)) {
        Image(
            bitmap = bitmap,
            contentDescription = "Attached image",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .size(20.dp)
                .align(Alignment.TopEnd)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove image",
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

// A compact effort button opens the three-stop thinking-time slider, keeping
// the composer quiet until the user explicitly wants to tune reasoning depth.
@Composable
private fun EffortPicker(effort: String, enabled: Boolean, onEffortSelected: (String) -> Unit) {
    val selectedIndex = ChatModels.EFFORT_LEVELS.indexOf(effort).coerceAtLeast(0)
    var sliderValue by remember(effort) { mutableStateOf(selectedIndex.toFloat()) }
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)) {
        TextButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.semantics {
                contentDescription = "Thinking effort: ${effort.replaceFirstChar { it.uppercase() }}. Open effort slider."
            },
        ) {
            Icon(
                Icons.Filled.Psychology,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Thinking",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 6.dp),
            )
            Text(
                text = effort.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 264.dp, max = 300.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Thinking effort", style = MaterialTheme.typography.titleSmall)
                Text(
                    effort.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { value ->
                        sliderValue = value
                        val selected = ChatModels.EFFORT_LEVELS[value.roundToInt().coerceIn(0, ChatModels.EFFORT_LEVELS.lastIndex)]
                        if (selected != effort) onEffortSelected(selected)
                    },
                    valueRange = 0f..2f,
                    steps = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .semantics { contentDescription = "Thinking effort slider. ${effort.replaceFirstChar { it.uppercase() }} selected." },
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Low", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "High",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).wrapContentWidth(Alignment.End),
                    )
                }
            }
        }
    }
}

@Composable
private fun MicButton(
    enabled: Boolean,
    isRecording: Boolean,
    micLevel: Float,
    isTranscribing: Boolean,
    onToggle: () -> Unit,
) {
    if (isTranscribing) {
        Box(modifier = Modifier.size(48.dp).padding(bottom = 4.dp, top = 4.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        return
    }
    Box(
        modifier = Modifier.size(48.dp).padding(bottom = 4.dp, top = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isRecording) {
            // Halo grows with the mic's peak amplitude — the only feedback a user
            // gets that recording is actually picking up their voice vs. silence.
            val animatedLevel by animateFloatAsState(targetValue = micLevel, label = "mic-level")
            Box(
                modifier = Modifier
                    .size(28.dp + 20.dp * animatedLevel)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.25f)),
            )
        }
        IconButton(onClick = onToggle, enabled = enabled) {
            Icon(
                imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = if (isRecording) "Stop recording" else "Record voice message",
                tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SendButton(
    hasText: Boolean,
    isStreaming: Boolean,
    enabled: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    FilledIconButton(
        onClick = { if (isStreaming) onStop() else onSend() },
        enabled = enabled && (hasText || isStreaming),
        modifier = Modifier.padding(bottom = 4.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Icon(
            imageVector = if (isStreaming) Icons.Filled.Stop else Icons.Filled.ArrowUpward,
            contentDescription = if (isStreaming) "Stop generating" else "Send message",
        )
    }
}
