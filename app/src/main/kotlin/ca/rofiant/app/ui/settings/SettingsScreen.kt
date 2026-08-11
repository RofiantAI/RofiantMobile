package ca.rofiant.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ca.rofiant.app.data.model.AppSettings
import ca.rofiant.app.data.model.AppTheme
import ca.rofiant.app.data.model.ChatModels

// App-preferences page — Appearance/Chat/Data. Identity/session (profile,
// email, sign out) lives on AccountScreen, reached via the "Settings" row
// there rather than merged into this one; matches the ChatGPT app's split
// between its Account page and its separate Settings page.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onThemeChange: (AppTheme) -> Unit,
    onShowTimestampsChange: (Boolean) -> Unit,
    onCustomInstructionsChange: (String) -> Unit,
    onContextLimitChange: (Int) -> Unit,
    onClearConversations: () -> Unit,
    onSelectModel: (String) -> Unit,
    onSelectEffort: (String) -> Unit,
    onHideBetaNoticeChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var appearanceExpanded by remember { mutableStateOf(false) }
    var instructionsExpanded by remember { mutableStateOf(false) }
    var contextExpanded by remember { mutableStateOf(false) }
    var instructionsDraft by remember(settings.customInstructions) { mutableStateOf(settings.customInstructions) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionHeader("Preferences")
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Filled.Contrast,
                    label = "Appearance",
                    value = settings.theme.name.replaceFirstChar { it.uppercase() },
                    trailing = { ExpandChevron(appearanceExpanded) },
                    onClick = { appearanceExpanded = !appearanceExpanded },
                )
                AnimatedVisibility(visible = appearanceExpanded) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        AppTheme.entries.forEachIndexed { index, theme ->
                            SegmentedButton(
                                selected = settings.theme == theme,
                                onClick = { onThemeChange(theme) },
                                shape = SegmentedButtonDefaults.itemShape(index, AppTheme.entries.size),
                            ) {
                                Text(theme.name.replaceFirstChar { it.uppercase() })
                            }
                        }
                    }
                }
                RowDivider()
                SettingsRow(
                    icon = Icons.Filled.AccessTime,
                    label = "Show timestamps",
                    trailing = {
                        Switch(checked = settings.showTimestamps, onCheckedChange = onShowTimestampsChange)
                    },
                    onClick = { onShowTimestampsChange(!settings.showTimestamps) },
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Filled.NewReleases,
                    label = "Hide beta notice",
                    value = "The \"you're using a beta\" popup on launch",
                    trailing = {
                        Switch(checked = settings.hideBetaNotice, onCheckedChange = onHideBetaNoticeChange)
                    },
                    onClick = { onHideBetaNoticeChange(!settings.hideBetaNotice) },
                )
            }

            SectionHeader("Model")
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Filled.Memory,
                    label = "Model",
                    value = ChatModels.byId(settings.model)?.displayName ?: settings.model,
                    onClick = { showModelPicker = true },
                )
            }

            SectionHeader("Chat")
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Filled.EditNote,
                    label = "Custom instructions",
                    value = settings.customInstructions.ifBlank { "Not set" },
                    trailing = { ExpandChevron(instructionsExpanded) },
                    onClick = { instructionsExpanded = !instructionsExpanded },
                )
                AnimatedVisibility(visible = instructionsExpanded) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        OutlinedTextField(
                            value = instructionsDraft,
                            onValueChange = { instructionsDraft = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. Answer concisely, prefer Kotlin examples") },
                            minLines = 2,
                            maxLines = 5,
                        )
                        TextButton(
                            onClick = { onCustomInstructionsChange(instructionsDraft) },
                            modifier = Modifier.align(Alignment.End),
                        ) { Text("Save") }
                    }
                }
                RowDivider()
                SettingsRow(
                    icon = Icons.Filled.History,
                    label = "History sent to model",
                    value = "${settings.contextLimit} messages",
                    trailing = { ExpandChevron(contextExpanded) },
                    onClick = { contextExpanded = !contextExpanded },
                )
                AnimatedVisibility(visible = contextExpanded) {
                    Slider(
                        value = settings.contextLimit.toFloat(),
                        onValueChange = { onContextLimitChange(it.toInt()) },
                        valueRange = 4f..40f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
            }

            SectionHeader("Data")
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Filled.DeleteSweep,
                    label = "Clear all conversations",
                    labelColor = MaterialTheme.colorScheme.error,
                    onClick = { showClearConfirm = true },
                )
            }

            if (versionName != null) {
                SectionHeader("About")
                SettingsGroup {
                    SettingsRow(icon = Icons.Filled.Info, label = "Version", value = versionName, onClick = {})
                }
            }

            Text(
                "Conversations are backed up to your account when signed in.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }

    if (showModelPicker) {
        ModelPickerSheet(
            selectedModel = settings.model,
            selectedEffort = settings.reasoningEffort,
            onSelectModel = onSelectModel,
            onSelectEffort = onSelectEffort,
            onDismiss = { showModelPicker = false },
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear all conversations?") },
            text = { Text("This deletes every conversation on this device. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { onClearConversations(); showClearConfirm = false }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            },
        )
    }
}
