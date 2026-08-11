package ca.rofiant.app.data.local

import android.content.Context
import android.net.Uri
import android.util.Base64

/** Same encoding rofiant-desktop's image attach uses (Composer.tsx reads the file straight to a data: URL, no resize). */
fun uriToImageDataUrl(context: Context, uri: Uri): String? {
    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
    return "data:$mimeType;base64,$base64"
}
