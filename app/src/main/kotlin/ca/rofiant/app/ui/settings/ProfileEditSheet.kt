package ca.rofiant.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Bottom sheet opened by tapping the Account avatar (or its pen badge) — edits the
 * display name and profile photo that sync to the same Supabase user_metadata
 * (display_name / custom_avatar_url) rofiant-web and rofiant-desktop read and write.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditSheet(
    initialDisplayName: String,
    avatarUrl: String?,
    avatarLabel: String?,
    onSave: (displayName: String) -> Unit,
    onAvatarPicked: (jpegBytes: ByteArray) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialDisplayName) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val jpegBytes = withContext(Dispatchers.IO) { decodeAndCompressAvatar(context, uri) }
                if (jpegBytes != null) onAvatarPicked(jpegBytes)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Edit profile",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            ProfileAvatar(
                label = avatarLabel,
                avatarUrl = avatarUrl,
                onEditClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            )

            Button(
                onClick = {
                    onSave(name.trim())
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp),
            ) { Text("Save") }
        }
    }
}

// Downscaled + re-encoded so every picked format (HEIC, PNG, huge camera JPEGs)
// lands as the same small image/jpeg the avatars bucket expects.
private fun decodeAndCompressAvatar(context: android.content.Context, source: android.net.Uri): ByteArray? {
    return runCatching {
        val bitmap = context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it) }
            ?: return null
        val maxDim = 512
        val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap
        ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
    }.getOrNull()
}
