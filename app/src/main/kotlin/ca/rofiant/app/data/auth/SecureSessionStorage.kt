package ca.rofiant.app.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Session tokens are sensitive (they're bearer credentials for both Supabase
 * and the chat proxy), so they live in an AES-256-GCM encrypted prefs file
 * rather than plain DataStore.
 */
class SecureSessionStorage(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "rofiant_secure_session",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun save(session: AuthSession) {
        prefs.edit().putString(KEY_SESSION, json.encodeToString(session)).apply()
    }

    fun load(): AuthSession? {
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        return runCatching { json.decodeFromString<AuthSession>(raw) }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove(KEY_SESSION).remove(KEY_PKCE_VERIFIER).apply()
    }

    fun rememberedEmail(): String? = prefs.getString(KEY_REMEMBERED_EMAIL, null)

    fun setRememberedEmail(email: String?) {
        if (email == null) {
            prefs.edit().remove(KEY_REMEMBERED_EMAIL).apply()
        } else {
            prefs.edit().putString(KEY_REMEMBERED_EMAIL, email).apply()
        }
    }

    fun savePkceVerifier(verifier: String) {
        prefs.edit().putString(KEY_PKCE_VERIFIER, verifier).apply()
    }

    fun takePkceVerifier(): String? {
        val verifier = prefs.getString(KEY_PKCE_VERIFIER, null)
        prefs.edit().remove(KEY_PKCE_VERIFIER).apply()
        return verifier
    }

    private companion object {
        const val KEY_SESSION = "session"
        const val KEY_PKCE_VERIFIER = "pkce_verifier"
        const val KEY_REMEMBERED_EMAIL = "remembered_email"
    }
}
