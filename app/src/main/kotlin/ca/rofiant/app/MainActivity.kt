package ca.rofiant.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.rofiant.app.data.auth.AuthState
import ca.rofiant.app.ui.chat.AppViewModel
import ca.rofiant.app.ui.nav.RootScreen
import ca.rofiant.app.ui.theme.RofiantTheme
import ca.rofiant.app.data.model.AppTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels {
        AppViewModel.Factory((application as RofiantApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { viewModel.authState.value == AuthState.Loading }
        enableEdgeToEdge()
        handleAuthDeepLink(intent)

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val darkTheme = when (settings.theme) {
                AppTheme.light -> false
                AppTheme.dark -> true
                AppTheme.system -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            RofiantTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RootScreen(viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthDeepLink(intent)
    }

    private fun handleAuthDeepLink(intent: Intent?) {
        val uri: Uri = intent?.data ?: return
        if (uri.scheme == "rofiant" && uri.host == "auth-callback") {
            viewModel.handleOAuthRedirect(uri)
        }
    }

}
