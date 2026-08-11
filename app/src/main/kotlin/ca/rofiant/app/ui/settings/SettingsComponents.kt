package ca.rofiant.app.ui.settings

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Shared building blocks for AccountScreen and SettingsScreen — grouped
// rounded-row sections, matching the ChatGPT app's settings row style.

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp, start = 4.dp),
    )
}

@Composable
internal fun SettingsGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(bottom = 4.dp),
    ) {
        content()
    }
}

@Composable
internal fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.background, thickness = 1.dp)
}

@Composable
internal fun ExpandChevron(expanded: Boolean) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Icon(
        Icons.Filled.ExpandMore,
        contentDescription = null,
        modifier = Modifier.size(20.dp).rotate(rotation),
    )
}

@Composable
internal fun SettingsRow(
    icon: ImageVector,
    label: String,
    value: String? = null,
    labelColor: Color = Color.Unspecified,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, contentDescription = null, tint = labelColor.takeOrElse(), modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(label, color = labelColor.takeOrElse())
                if (value != null) {
                    Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun Color.takeOrElse(): Color = if (this == Color.Unspecified) MaterialTheme.colorScheme.onSurface else this

/**
 * Circular avatar used on the Account header — the synced profile photo if
 * [avatarUrl] is set, else the initial letter, else a person icon. [onEditClick]
 * shows a small pen badge in the bottom-right corner (the Account screen's
 * "open the edit sheet" affordance); omit it to render a plain, non-editable
 * avatar elsewhere.
 */
@Composable
fun ProfileAvatar(
    label: String?,
    avatarUrl: String? = null,
    size: androidx.compose.ui.unit.Dp = 72.dp,
    onEditClick: (() -> Unit)? = null,
) {
    Box {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .let { if (onEditClick != null) it.clickable(onClick = onEditClick) else it },
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = rememberAvatarBitmap(avatarUrl)
            when {
                bitmap != null -> Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                label != null -> Text(
                    label,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                else -> Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(size * 0.5f),
                )
            }
        }
        if (onEditClick != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.32f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                    .clickable(onClick = onEditClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Edit profile",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(size * 0.18f),
                )
            }
        }
    }
}

// ponytail: re-fetches/decodes on every new url, no disk/memory cache — fine
// for one small profile photo; swap for Coil if avatars show up in lists.
@Composable
internal fun rememberAvatarBitmap(url: String?): android.graphics.Bitmap? {
    val state = produceState<android.graphics.Bitmap?>(initialValue = null, url) {
        value = url?.let {
            runCatching {
                java.net.URL(it).openStream().use { stream -> BitmapFactory.decodeStream(stream) }
            }.getOrNull()
        }
    }
    return state.value
}
