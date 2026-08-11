package ca.rofiant.app

import android.content.Context
import ca.rofiant.app.data.auth.AuthRepository
import ca.rofiant.app.data.auth.SecureSessionStorage
import ca.rofiant.app.data.local.ConversationsRepository
import ca.rofiant.app.data.local.SettingsRepository
import ca.rofiant.app.data.remote.ChatApi
import ca.rofiant.app.data.remote.ChatSyncApi
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Hand-rolled composition root — the app is small enough (one screen graph,
 * a handful of repositories) that a DI framework would be more ceremony
 * than the wiring it replaces.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val sessionStorage = SecureSessionStorage(appContext)
    val authRepository = AuthRepository(sessionStorage, okHttpClient)
    val settingsRepository = SettingsRepository(appContext)
    val conversationsRepository = ConversationsRepository(appContext)
    val chatApi = ChatApi(okHttpClient)
    val chatSyncApi = ChatSyncApi(okHttpClient)
}
