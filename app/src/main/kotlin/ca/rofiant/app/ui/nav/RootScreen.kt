package ca.rofiant.app.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ca.rofiant.app.data.auth.AuthState
import ca.rofiant.app.data.local.rememberIsOnline
import ca.rofiant.app.ui.auth.AuthScreen
import ca.rofiant.app.ui.auth.MfaChallengeScreen
import ca.rofiant.app.ui.chat.AppViewModel
import ca.rofiant.app.ui.chat.ChatScreen
import ca.rofiant.app.ui.components.LoadingScreen
import ca.rofiant.app.ui.drawer.DrawerContent
import ca.rofiant.app.ui.settings.AccountScreen
import ca.rofiant.app.ui.settings.SettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val ROUTE_CHAT = "chat"
private const val ROUTE_ACCOUNT = "account"
private const val ROUTE_SETTINGS = "settings"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootScreen(viewModel: AppViewModel) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    // Bootstrap often resolves in well under one animation cycle (no stored
    // session), which would otherwise skip the loading mark's pulse entirely.
    var minDurationElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(900)
        minDurationElapsed = true
    }

    when (val state = authState) {
        AuthState.Loading -> LoadingScreen()
        else -> if (!minDurationElapsed) {
            LoadingScreen()
        } else when (state) {
            AuthState.SignedOut -> AuthScreen(viewModel)
            is AuthState.MfaRequired -> MfaChallengeScreen(viewModel, onCancel = { viewModel.signOut() })
            is AuthState.SignedIn -> MainApp(viewModel)
            AuthState.Loading -> Unit
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainApp(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    // Fresh per entry into MainApp (i.e. once per sign-in), not persisted —
    // dismissing it doesn't suppress it on the next sign-in/app launch.
    var showBetaDialog by remember { mutableStateOf(true) }

    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val activeId by viewModel.activeId.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val isOnline by rememberIsOnline()

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                conversations = conversations,
                activeId = activeId,
                authState = authState,
                onSelect = {
                    viewModel.selectConversation(it)
                    scope.launch { drawerState.close() }
                },
                onDelete = viewModel::deleteConversation,
                onNewChat = {
                    viewModel.newConversation()
                    scope.launch { drawerState.close() }
                },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    navController.navigate(ROUTE_ACCOUNT)
                },
            )
        },
    ) {
        NavHost(navController = navController, startDestination = ROUTE_CHAT) {
            composable(ROUTE_CHAT) {
                ChatScreen(
                    viewModel = viewModel,
                    isOnline = isOnline,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                )
            }
            composable(ROUTE_ACCOUNT) {
                AccountScreen(
                    authState = authState,
                    conversations = conversations,
                    onExportJson = viewModel::exportConversationsJson,
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                    onSignOut = viewModel::signOut,
                    onSignIn = viewModel::signOut,
                    onSaveProfile = viewModel::setDisplayName,
                    onAvatarPicked = viewModel::uploadAvatar,
                    onLinkDevice = { code, onResult -> viewModel.linkDevice(code, onResult) },
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    settings = settings,
                    onBack = { navController.popBackStack() },
                    onThemeChange = viewModel::setTheme,
                    onShowTimestampsChange = viewModel::setShowTimestamps,
                    onCustomInstructionsChange = viewModel::setCustomInstructions,
                    onContextLimitChange = viewModel::setContextLimit,
                    onClearConversations = viewModel::clearAllConversations,
                    onSelectModel = viewModel::setModel,
                    onSelectEffort = viewModel::setEffort,
                )
            }
        }
    }

    if (showBetaDialog) {
        AlertDialog(
            onDismissRequest = { showBetaDialog = false },
            title = { Text("You're using a beta") },
            text = { Text("Rofiant for Android is in beta — some things may be rough around the edges. Thanks for trying it out.") },
            confirmButton = {
                TextButton(onClick = { showBetaDialog = false }) { Text("Got it") }
            },
        )
    }
}
