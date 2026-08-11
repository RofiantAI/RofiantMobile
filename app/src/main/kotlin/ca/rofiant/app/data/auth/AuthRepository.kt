package ca.rofiant.app.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient

/**
 * Owns the signed-in/out state machine and is the single place that reads
 * or writes the encrypted session. rofiant-desktop leans on supabase-js for
 * all of this (refresh scheduling, MFA gate, PKCE bookkeeping); there is no
 * equivalent single library here so this repository does that bookkeeping
 * directly against the plain GoTrue REST API (see AuthApi).
 */
class AuthRepository(
    private val storage: SecureSessionStorage,
    okHttpClient: OkHttpClient,
) {
    private val api = AuthApi(okHttpClient)

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    suspend fun bootstrap() {
        val session = storage.load()
        if (session == null) {
            _state.value = AuthState.SignedOut
            return
        }
        _state.value = resolveAal(session)
    }

    suspend fun signInWithPassword(email: String, password: String) {
        val session = api.signInWithPassword(email, password)
        storage.save(session)
        _state.value = resolveAal(session)
    }

    fun rememberedEmail(): String? = storage.rememberedEmail()

    fun setRememberedEmail(email: String?) = storage.setRememberedEmail(email)

    /** Returns true once the caller has a live session; false means a confirmation email is on its way. */
    suspend fun signUp(email: String, password: String): Boolean {
        return when (val result = api.signUp(email, password)) {
            is SignUpResult.SignedIn -> {
                storage.save(result.session)
                _state.value = resolveAal(result.session)
                true
            }
            is SignUpResult.ConfirmationRequired -> false
        }
    }

    suspend fun signInAnonymously() {
        val session = api.signInAnonymously()
        storage.save(session)
        _state.value = AuthState.SignedIn(session)
    }

    /** Updates the signed-in user's name/avatar on the backend and refreshes the in-memory session so the UI reflects it immediately. */
    suspend fun updateProfile(displayName: String? = null, avatarUrl: String? = null) {
        val token = validAccessToken() ?: throw AuthException("Not signed in")
        val session = storage.load() ?: throw AuthException("Not signed in")
        val user = api.updateProfile(token, displayName, avatarUrl)
        val updated = session.copy(user = user)
        storage.save(updated)
        _state.value = AuthState.SignedIn(updated)
    }

    /** Uploads the picked photo and points user_metadata.custom_avatar_url at it — mirrors rofiant-web's upload-then-updateUser flow. */
    suspend fun uploadAvatar(jpegBytes: ByteArray) {
        val token = validAccessToken() ?: throw AuthException("Not signed in")
        val session = storage.load() ?: throw AuthException("Not signed in")
        val url = api.uploadAvatar(token, session.user.id, jpegBytes)
        updateProfile(avatarUrl = url)
    }

    /** Approves a desktop's "link a device" QR code, signing that desktop into this same account. */
    suspend fun linkDevice(code: String) {
        val token = validAccessToken() ?: throw AuthException("Not signed in")
        api.linkDevice(token, code)
    }

    suspend fun listLinkedDevices(): List<LinkedDevice> {
        val token = validAccessToken() ?: throw AuthException("Not signed in")
        return api.listLinkedDevices(token)
    }

    /** Signs that desktop out on its next token refresh — see link-device's "revoke" action. */
    suspend fun unlinkDevice(code: String) {
        val token = validAccessToken() ?: throw AuthException("Not signed in")
        api.unlinkDevice(token, code)
    }

    /** Changes the password for an already-signed-in user (vs. confirmPasswordReset's logged-out recovery flow). */
    suspend fun updatePassword(newPassword: String) {
        val token = validAccessToken() ?: throw AuthException("Not signed in")
        api.updatePassword(token, newPassword)
    }

    suspend fun requestPasswordReset(email: String) = api.requestPasswordReset(email)

    /** The code-entry alternative to the reset-link email — verifies the code, sets the new password, and signs the user in. */
    suspend fun confirmPasswordReset(email: String, code: String, newPassword: String) {
        val session = api.verifyRecoveryCode(email, code)
        api.updatePassword(session.accessToken, newPassword)
        storage.save(session)
        _state.value = resolveAal(session)
    }

    /** Returns the Custom Tab URL to launch; caller stores nothing — the PKCE verifier is persisted here. */
    fun startGoogleOAuth(): String {
        val verifier = Pkce.generateVerifier()
        storage.savePkceVerifier(verifier)
        return api.authorizeUrl("google", Pkce.challengeFor(verifier))
    }

    /** Call with the rofiant://auth-callback URI once the Custom Tab redirects back. */
    suspend fun handleOAuthRedirect(uri: android.net.Uri): Boolean {
        val error = uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error")
        if (error != null) throw AuthException(error)
        val code = uri.getQueryParameter("code") ?: return false
        val verifier = storage.takePkceVerifier() ?: throw AuthException("Missing PKCE verifier for this sign-in attempt")
        val session = api.exchangeCodeForSession(code, verifier)
        storage.save(session)
        _state.value = resolveAal(session)
        return true
    }

    suspend fun verifyMfa(code: String) {
        val current = _state.value
        require(current is AuthState.MfaRequired) { "No MFA challenge in progress" }
        val challengeId = api.createMfaChallenge(current.session.accessToken, current.factorId)
        val session = api.verifyMfaChallenge(current.session.accessToken, current.factorId, challengeId, code)
        storage.save(session)
        _state.value = AuthState.SignedIn(session)
    }

    fun signOut() {
        storage.clear()
        _state.value = AuthState.SignedOut
    }

    /** Used by the chat client — refreshes eagerly if the token is near expiry. */
    suspend fun validAccessToken(): String? {
        val session = storage.load() ?: return null
        if (System.currentTimeMillis() < session.expiresAtMillis - REFRESH_SKEW_MS) {
            return session.accessToken
        }
        return try {
            val refreshed = api.refresh(session.refreshToken)
            storage.save(refreshed)
            refreshed.accessToken
        } catch (e: AuthException) {
            storage.clear()
            _state.value = AuthState.SignedOut
            null
        }
    }

    private suspend fun resolveAal(session: AuthSession): AuthState {
        val factorId = try {
            api.findVerifiedFactorId(session.accessToken)
        } catch (e: AuthException) {
            null
        }
        val currentAal = decodeAal(session.accessToken)
        return if (factorId != null && currentAal != "aal2") {
            AuthState.MfaRequired(session, factorId)
        } else {
            AuthState.SignedIn(session)
        }
    }

    private fun decodeAal(jwt: String): String? {
        val parts = jwt.split(".")
        if (parts.size < 2) return null
        return try {
            val payload = android.util.Base64.decode(
                parts[1].replace('-', '+').replace('_', '/'),
                android.util.Base64.DEFAULT,
            )
            val json = Json.parseToJsonElement(String(payload, Charsets.UTF_8))
            json.jsonObject["aal"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val REFRESH_SKEW_MS = 60_000L
    }
}
