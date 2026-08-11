package ca.rofiant.app.data.auth

import kotlinx.serialization.Serializable

@Serializable
data class AuthUser(
    val id: String,
    val email: String? = null,
    val isAnonymous: Boolean = false,
    // Mirrors rofiant-web/-desktop's GoTrue user_metadata.display_name /
    // custom_avatar_url — same backend fields, so profile edits show up
    // across all three clients.
    val displayName: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
    val user: AuthUser,
)

@Serializable
data class LinkedDevice(val code: String, val label: String, val createdAt: String)

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class MfaRequired(val session: AuthSession, val factorId: String) : AuthState
    data class SignedIn(val session: AuthSession) : AuthState
}

/** /signup returns a live session only when email confirmation is disabled project-wide. */
sealed interface SignUpResult {
    data class SignedIn(val session: AuthSession) : SignUpResult
    data object ConfirmationRequired : SignUpResult
}

sealed interface AuthError {
    data class Api(val message: String) : AuthError
    data class Network(val message: String) : AuthError
}

class AuthException(message: String) : Exception(message)
