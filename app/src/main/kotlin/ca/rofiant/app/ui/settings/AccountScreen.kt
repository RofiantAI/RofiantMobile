package ca.rofiant.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import ca.rofiant.app.data.auth.AuthConfig
import ca.rofiant.app.data.auth.AuthState
import ca.rofiant.app.data.auth.LinkedDevice
import ca.rofiant.app.data.model.Conversation
import java.io.File

/**
 * The avatar-tap destination — identity/session (profile, email, sign
 * out), account actions (create account, export data), and a doorway into
 * the separate app-preferences Settings page. Matches the ChatGPT app's
 * Account page; Appearance/Chat/Data prefs live on their own screen
 * (SettingsScreen) rather than merged into this one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    authState: AuthState,
    conversations: List<Conversation>,
    onExportJson: (List<Conversation>) -> String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit,
    onSignIn: () -> Unit,
    onSaveProfile: (displayName: String, onResult: (success: Boolean, errorMessage: String?) -> Unit) -> Unit,
    onAvatarPicked: (jpegBytes: ByteArray, onResult: (success: Boolean, errorMessage: String?) -> Unit) -> Unit,
    onLinkDevice: (code: String, onResult: (success: Boolean, errorMessage: String?) -> Unit) -> Unit,
    linkedDevices: List<LinkedDevice> = emptyList(),
    onLoadLinkedDevices: () -> Unit = {},
    onUnlinkDevice: (code: String, onResult: (success: Boolean, errorMessage: String?) -> Unit) -> Unit = { _, _ -> },
    onChangePassword: (newPassword: String, onResult: (success: Boolean, errorMessage: String?) -> Unit) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val user = (authState as? AuthState.SignedIn)?.session?.user
    val email = user?.email
    val isAnonymous = user?.isAnonymous == true
    val fallbackName = email ?: if (isAnonymous) "Guest" else "Not signed in"
    val profileName = user?.displayName?.ifBlank { null } ?: fallbackName
    val avatarLabel = user?.displayName?.take(1)?.uppercase()?.takeIf { it.isNotBlank() }
        ?: email?.take(1)?.uppercase() ?: if (isAnonymous) "G" else null
    var showEditSheet by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var deviceToUnlink by remember { mutableStateOf<LinkedDevice?>(null) }

    LaunchedEffect(Unit) { onLoadLinkedDevices() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding).padding(horizontal = 16.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ProfileAvatar(
                    label = avatarLabel,
                    avatarUrl = user?.avatarUrl,
                    onEditClick = { showEditSheet = true },
                )
                Text(
                    profileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            SectionHeader("Account")
            SettingsGroup {
                if (email != null) {
                    SettingsRow(icon = Icons.Filled.Email, label = "Email", value = email, onClick = {})
                    RowDivider()
                }
                if (isAnonymous) {
                    SettingsRow(
                        icon = Icons.Filled.PersonAdd,
                        label = "Create account",
                        value = "Save this chat history to an email + password",
                        onClick = {
                            CustomTabsIntent.Builder().build()
                                .launchUrl(context, Uri.parse(AuthConfig.SIGNUP_URL))
                        },
                    )
                    RowDivider()
                }
                if (!isAnonymous && authState is AuthState.SignedIn) {
                    SettingsRow(
                        icon = Icons.Filled.Lock,
                        label = "Change password",
                        onClick = { showChangePassword = true },
                    )
                    RowDivider()
                }
                SettingsRow(icon = Icons.Filled.Settings, label = "Settings", onClick = onOpenSettings)
            }

            SectionHeader("Devices")
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Filled.QrCodeScanner,
                    label = "Link a device",
                    value = "Scan a QR code from Rofiant on your computer",
                    onClick = {
                        GmsBarcodeScanning.getClient(context).startScan()
                            .addOnSuccessListener { barcode ->
                                val raw = barcode.rawValue.orEmpty()
                                if (raw.startsWith(LINK_QR_PREFIX)) {
                                    Toast.makeText(context, "Linking device…", Toast.LENGTH_SHORT).show()
                                    onLinkDevice(raw.removePrefix(LINK_QR_PREFIX)) { success, errorMessage ->
                                        val message = if (success) "Device linked" else (errorMessage ?: "Couldn't link device")
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Not a Rofiant link code", Toast.LENGTH_SHORT).show()
                                }
                            }
                    },
                )
                linkedDevices.forEach { device ->
                    RowDivider()
                    SettingsRow(
                        icon = Icons.Filled.Devices,
                        label = device.label,
                        trailing = { TextButton(onClick = { deviceToUnlink = device }) { Text("Unlink") } },
                        onClick = {},
                    )
                }
            }

            SectionHeader("Data")
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Filled.IosShare,
                    label = "Export conversations",
                    value = "${conversations.size} conversation${if (conversations.size == 1) "" else "s"} as JSON",
                    onClick = { exportConversations(context, onExportJson(conversations)) },
                )
            }

            SectionHeader("Session")
            SettingsGroup {
                if (authState is AuthState.SignedIn) {
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        label = "Sign out",
                        labelColor = MaterialTheme.colorScheme.error,
                        onClick = onSignOut,
                    )
                } else {
                    SettingsRow(icon = Icons.AutoMirrored.Filled.Login, label = "Sign in", onClick = onSignIn)
                }
            }
        }
    }

    if (showChangePassword) {
        ChangePasswordDialog(
            onDismiss = { showChangePassword = false },
            onSave = { newPassword ->
                showChangePassword = false
                onChangePassword(newPassword) { success, errorMessage ->
                    val message = if (success) "Password updated" else (errorMessage ?: "Couldn't update password")
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    deviceToUnlink?.let { device ->
        AlertDialog(
            onDismissRequest = { deviceToUnlink = null },
            title = { Text("Unlink this device?") },
            text = { Text("${device.label} will be signed out.") },
            confirmButton = {
                TextButton(onClick = {
                    deviceToUnlink = null
                    onUnlinkDevice(device.code) { success, errorMessage ->
                        val message = if (success) "Device unlinked" else (errorMessage ?: "Couldn't unlink device")
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Unlink") }
            },
            dismissButton = {
                TextButton(onClick = { deviceToUnlink = null }) { Text("Cancel") }
            },
        )
    }

    if (showEditSheet) {
        ProfileEditSheet(
            initialDisplayName = user?.displayName ?: "",
            avatarUrl = user?.avatarUrl,
            avatarLabel = avatarLabel,
            onSave = { name ->
                onSaveProfile(name) { success, errorMessage ->
                    if (!success) Toast.makeText(context, errorMessage ?: "Couldn't save name", Toast.LENGTH_SHORT).show()
                }
            },
            onAvatarPicked = { jpegBytes ->
                onAvatarPicked(jpegBytes) { success, errorMessage ->
                    if (!success) Toast.makeText(context, errorMessage ?: "Couldn't upload photo", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showEditSheet = false },
        )
    }
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit, onSave: (newPassword: String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val mismatch = confirmPassword.isNotEmpty() && password != confirmPassword
    val valid = password.length >= 6 && password == confirmPassword

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change password") },
        text = {
            Column {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("New password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm password") },
                    singleLine = true,
                    isError = mismatch,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                if (mismatch) {
                    Text(
                        "Passwords don't match",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(password) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// Matches rofiant-desktop's LinkDeviceDialog.tsx qrPayload() — no URL scheme,
// just a namespaced literal so a generic QR scanner elsewhere doesn't confuse it with anything else.
private const val LINK_QR_PREFIX = "rofiant-link:"

private fun exportConversations(context: android.content.Context, json: String) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, "rofiant-conversations.json")
    file.writeText(json)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export conversations"))
}
