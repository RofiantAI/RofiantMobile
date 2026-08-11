package ca.rofiant.app.ui.drawer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.rofiant.app.data.auth.AuthState
import ca.rofiant.app.data.model.Conversation
import ca.rofiant.app.ui.settings.ProfileAvatar
import java.util.Calendar

// Matches the ChatGPT app drawer: flat background (no tonal-elevation tint
// lightening it away from the rest of the app), a collapsed search icon
// instead of an always-open field, and a pinned "+ Chat" pill + avatar
// footer instead of a settings row. Its fixed shortcut rows (Images,
// Library, Projects, ...) aren't ported — those are OpenAI products Rofiant
// doesn't have, and a row that goes nowhere is worse than no row.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawerContent(
    conversations: List<Conversation>,
    activeId: String?,
    authState: AuthState,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(conversations, query) {
        if (query.isBlank()) conversations.sortedByDescending { it.updatedAt }
        else conversations.filter { it.title.contains(query, ignoreCase = true) }.sortedByDescending { it.updatedAt }
    }
    val grouped = remember(filtered) { groupByRecency(filtered) }

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.background,
        drawerContentColor = MaterialTheme.colorScheme.onBackground,
        drawerTonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Rofiant",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Icon(Icons.Filled.Search, contentDescription = "Search chats")
                }
            }

            AnimatedVisibility(visible = searchOpen) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("Search chats") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                )
            }

            if (grouped.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (conversations.isEmpty()) {
                            "Once you start chatting,\nyour conversations will appear here."
                        } else {
                            "No matches"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    for ((label, items) in grouped) {
                        item(key = "header-$label") {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp),
                            )
                        }
                        items(items, key = { it.id }) { conversation ->
                            ConversationRow(
                                conversation = conversation,
                                selected = conversation.id == activeId,
                                onClick = { onSelect(conversation.id) },
                                onDelete = { onDelete(conversation.id) },
                            )
                        }
                    }
                }
            }

            HorizontalDivider()
            BottomBar(authState = authState, onNewChat = onNewChat, onOpenSettings = onOpenSettings)
        }
    }
}

private fun groupByRecency(conversations: List<Conversation>): List<Pair<String, List<Conversation>>> {
    if (conversations.isEmpty()) return emptyList()
    val now = Calendar.getInstance()
    val today = now.clone() as Calendar
    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val weekAgo = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -7) }

    fun sameDay(a: Calendar, ts: Long): Boolean {
        val b = Calendar.getInstance().apply { timeInMillis = ts }
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    val buckets = linkedMapOf(
        "Today" to mutableListOf<Conversation>(),
        "Yesterday" to mutableListOf(),
        "Previous 7 days" to mutableListOf(),
        "Older" to mutableListOf(),
    )
    for (c in conversations) {
        val bucket = when {
            sameDay(today, c.updatedAt) -> "Today"
            sameDay(yesterday, c.updatedAt) -> "Yesterday"
            c.updatedAt >= weekAgo.timeInMillis -> "Previous 7 days"
            else -> "Older"
        }
        buckets.getValue(bucket).add(c)
    }
    return buckets.filterValues { it.isNotEmpty() }.map { it.key to it.value }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationRow(
    conversation: Conversation,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        ListItem(
            headlineContent = {
                Text(conversation.title.ifBlank { "New chat" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            modifier = Modifier.clickable(onClick = onClick),
            colors = ListItemDefaults.colors(
                containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background,
            ),
        )
    }
}

@Composable
private fun BottomBar(authState: AuthState, onNewChat: () -> Unit, onOpenSettings: () -> Unit) {
    val user = (authState as? AuthState.SignedIn)?.session?.user
    val fallbackLabel = when (authState) {
        is AuthState.SignedIn -> user?.email?.take(1)?.uppercase()
            ?: if (user?.isAnonymous == true) "G" else "?"
        is AuthState.MfaRequired -> "?"
        AuthState.SignedOut -> "?"
        AuthState.Loading -> "…"
    }
    val label = user?.displayName?.take(1)?.uppercase()?.takeIf { it.isNotBlank() } ?: fallbackLabel
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onNewChat)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "Chat",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 6.dp),
            )
        }

        Box(modifier = Modifier.clickable(onClick = onOpenSettings)) {
            ProfileAvatar(
                label = label.takeIf { it.length == 1 },
                avatarUrl = user?.avatarUrl,
                size = 40.dp,
            )
        }
    }
}
