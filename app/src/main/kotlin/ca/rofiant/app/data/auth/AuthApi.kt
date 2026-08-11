package ca.rofiant.app.data.auth

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

@Serializable
private data class GoTrueFactor(val id: String, val factor_type: String, val status: String)

@Serializable
private data class GoTrueUser(
    val id: String,
    val email: String? = null,
    val is_anonymous: Boolean = false,
    val factors: List<GoTrueFactor>? = null,
    val user_metadata: JsonObject? = null,
)

@Serializable
private data class LinkedDeviceRow(val code: String, val label: String? = null, val created_at: String)

@Serializable
private data class ListDevicesResponse(val devices: List<LinkedDeviceRow>)

@Serializable
private data class TokenResponse(
    val access_token: String,
    val refresh_token: String,
    val expires_in: Long,
    val user: GoTrueUser,
)

/** Talks to Supabase's GoTrue REST API directly — see AuthRepository for why this isn't the JS/Kotlin SDK. */
class AuthApi(private val client: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json".toMediaType()

    suspend fun signInWithPassword(email: String, password: String): AuthSession =
        tokenRequest("password", mapOf("email" to email, "password" to password))

    suspend fun signUp(email: String, password: String): SignUpResult {
        val body = json.encodeToString(
            JsonObject.serializer(),
            JsonObject(mapOf("email" to JsonPrimitive(email), "password" to JsonPrimitive(password))),
        )
        val response = execute(
            Request.Builder()
                .url("${AuthConfig.AUTH_BASE}/signup")
                .header("apikey", AuthConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer ${AuthConfig.SUPABASE_ANON_KEY}")
                .post(body.toRequestBody(jsonMedia))
                .build()
        )
        val obj = json.parseToJsonElement(response).jsonObject()
        return if (obj["access_token"] != null) {
            SignUpResult.SignedIn(parseTokenResponse(response))
        } else {
            SignUpResult.ConfirmationRequired
        }
    }

    suspend fun signInAnonymously(): AuthSession {
        val body = json.encodeToString(JsonObject.serializer(), JsonObject(mapOf("data" to JsonObject(emptyMap()))))
        val response = execute(
            Request.Builder()
                .url("${AuthConfig.AUTH_BASE}/signup")
                .header("apikey", AuthConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer ${AuthConfig.SUPABASE_ANON_KEY}")
                .post(body.toRequestBody(jsonMedia))
                .build()
        )
        return parseTokenResponse(response)
    }

    suspend fun refresh(refreshToken: String): AuthSession =
        tokenRequest("refresh_token", mapOf("refresh_token" to refreshToken))

    suspend fun exchangeCodeForSession(code: String, codeVerifier: String): AuthSession =
        tokenRequest("pkce", mapOf("auth_code" to code, "code_verifier" to codeVerifier))

    suspend fun requestPasswordReset(email: String) {
        val body = json.encodeToString(JsonObject.serializer(), JsonObject(mapOf("email" to JsonPrimitive(email))))
        execute(
            Request.Builder()
                .url("${AuthConfig.AUTH_BASE}/recover")
                .header("apikey", AuthConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer ${AuthConfig.SUPABASE_ANON_KEY}")
                .post(body.toRequestBody(jsonMedia))
                .build()
        )
    }

    /** The 8-digit "Or use this code" alternative GoTrue emails alongside the reset link. */
    suspend fun verifyRecoveryCode(email: String, code: String): AuthSession {
        val body = json.encodeToString(
            JsonObject.serializer(),
            JsonObject(
                mapOf(
                    "email" to JsonPrimitive(email),
                    "token" to JsonPrimitive(code),
                    "type" to JsonPrimitive("recovery"),
                )
            )
        )
        val response = execute(
            Request.Builder()
                .url("${AuthConfig.AUTH_BASE}/verify")
                .header("apikey", AuthConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer ${AuthConfig.SUPABASE_ANON_KEY}")
                .post(body.toRequestBody(jsonMedia))
                .build()
        )
        return parseTokenResponse(response)
    }

    /** Merges into GoTrue's user_metadata — same shape rofiant-web/-desktop write via supabase.auth.updateUser({ data }). */
    suspend fun updateProfile(accessToken: String, displayName: String? = null, avatarUrl: String? = null): AuthUser {
        val fields = buildMap {
            displayName?.let { put("display_name", JsonPrimitive(it)) }
            avatarUrl?.let { put("custom_avatar_url", JsonPrimitive(it)) }
        }
        val body = json.encodeToString(
            JsonObject.serializer(),
            JsonObject(mapOf("data" to JsonObject(fields))),
        )
        val response = execute(
            Request.Builder()
                .url("${AuthConfig.AUTH_BASE}/user")
                .header("apikey", AuthConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $accessToken")
                .put(body.toRequestBody(jsonMedia))
                .build()
        )
        return json.decodeFromString(GoTrueUser.serializer(), response).toAuthUser()
    }

    /** Uploads to the public `avatars` bucket at a per-user fixed path (same layout as rofiant-web) and returns the public URL. */
    suspend fun uploadAvatar(accessToken: String, userId: String, jpegBytes: ByteArray): String {
        val path = "$userId/avatar.jpg"
        execute(
            Request.Builder()
                .url("${AuthConfig.SUPABASE_URL}/storage/v1/object/avatars/$path")
                .header("apikey", AuthConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $accessToken")
                .header("x-upsert", "true")
                .post(jpegBytes.toRequestBody("image/jpeg".toMediaType()))
                .build()
        )
        return "${AuthConfig.SUPABASE_URL}/storage/v1/object/public/avatars/$path?v=${System.currentTimeMillis()}"
    }

    suspend fun updatePassword(accessToken: String, newPassword: String) {
        val body = json.encodeToString(
            JsonObject.serializer(),
            JsonObject(mapOf("password" to JsonPrimitive(newPassword))),
        )
        execute(
            Request.Builder()
                .url("${AuthConfig.AUTH_BASE}/user")
                .header("apikey", AuthConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $accessToken")
                .put(body.toRequestBody(jsonMedia))
                .build()
        )
    }

    /** Approves a "link a device" pairing code scanned from a desktop's QR — see supabase/functions/link-device in rofiant-desktop. */
    suspend fun linkDevice(accessToken: String, code: String) {
        val body = json.encodeToString(
            JsonObject.serializer(),
            JsonObject(mapOf("action" to JsonPrimitive("approve"), "code" to JsonPrimitive(code))),
        )
        execute(
            Request.Builder()
                .url("${AuthConfig.FUNCTIONS_BASE}/link-device")
                .header("apikey", AuthConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $accessToken")
                .post(body.toRequestBody(jsonMedia))
                .build()
        )
    }

    suspend fun listLinkedDevices(accessToken: String): List<LinkedDevice> {
        val body = json.encodeToString(
            JsonObject.serializer(),
            JsonObject(mapOf("action" to JsonPrimitive("list"))),
        )
        val response = execute(
            Request.Builder()
                .url("${AuthConfig.FUNCTIONS_BASE}/link-device")
                .header("apikey", AuthConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $accessToken")
                .post(body.toRequestBody(jsonMedia))
                .build()
        )
        return json.decodeFromString(ListDevicesResponse.serializer(), response).devices.map {
            LinkedDevice(code = it.code, label = it.label ?: "Unknown device", createdAt = it.created_at)
        }
    }

    suspend fun unlinkDevice(accessToken: String, code: String) {
        val body = json.encodeToString(
            JsonObject.serializer(),
            JsonObject(mapOf("action" to JsonPrimitive("revoke"), "code" to JsonPrimitive(code))),
        )
        execute(
            Request.Builder()
                .url("${AuthConfig.FUNCTIONS_BASE}/link-device")
                .header("apikey", AuthConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $accessToken")
                .post(body.toRequestBody(jsonMedia))
                .build()
        )
    }

    fun authorizeUrl(provider: String, codeChallenge: String): String {
        return "${AuthConfig.AUTH_BASE}/authorize" +
            "?provider=$provider" +
            "&redirect_to=${java.net.URLEncoder.encode(AuthConfig.AUTH_REDIRECT_URL, "UTF-8")}" +
            "&code_challenge=$codeChallenge" +
            "&code_challenge_method=s256"
    }

    suspend fun createMfaChallenge(accessToken: String, factorId: String): String {
        val response = execute(
            Request.Builder()
                .url("${AuthConfig.AUTH_BASE}/factors/$factorId/challenge")
                .header("apikey", AuthConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $accessToken")
                .post("{}".toRequestBody(jsonMedia))
                .build()
        )
        return json.parseToJsonElement(response).jsonObject()["id"]?.jsonPrimitive?.content
            ?: throw AuthException("MFA challenge did not return an id")
    }

    suspend fun verifyMfaChallenge(accessToken: String, factorId: String, challengeId: String, code: String): AuthSession {
        val body = json.encodeToString(
            JsonObject.serializer(),
            JsonObject(
                mapOf(
                    "challenge_id" to JsonPrimitive(challengeId),
                    "code" to JsonPrimitive(code),
                )
            )
        )
        val response = execute(
            Request.Builder()
                .url("${AuthConfig.AUTH_BASE}/factors/$factorId/verify")
                .header("apikey", AuthConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $accessToken")
                .post(body.toRequestBody(jsonMedia))
                .build()
        )
        return parseTokenResponse(response)
    }

    /** Returns the id of the first verified MFA factor, or null if none / not required. */
    suspend fun findVerifiedFactorId(accessToken: String): String? {
        val response = execute(
            Request.Builder()
                .url("${AuthConfig.AUTH_BASE}/user")
                .header("apikey", AuthConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
        )
        val user = json.decodeFromString(GoTrueUser.serializer(), response)
        return user.factors?.firstOrNull { it.status == "verified" }?.id
    }

    private suspend fun tokenRequest(grantType: String, fields: Map<String, String>): AuthSession {
        val obj = JsonObject(fields.mapValues { JsonPrimitive(it.value) })
        val body = json.encodeToString(JsonObject.serializer(), obj)
        val response = execute(
            Request.Builder()
                .url("${AuthConfig.AUTH_BASE}/token?grant_type=$grantType")
                .header("apikey", AuthConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer ${AuthConfig.SUPABASE_ANON_KEY}")
                .post(body.toRequestBody(jsonMedia))
                .build()
        )
        return parseTokenResponse(response)
    }

    private fun parseTokenResponse(raw: String): AuthSession {
        val res = json.decodeFromString(TokenResponse.serializer(), raw)
        return AuthSession(
            accessToken = res.access_token,
            refreshToken = res.refresh_token,
            expiresAtMillis = System.currentTimeMillis() + res.expires_in * 1000,
            user = res.user.toAuthUser(),
        )
    }

    // GoTrue sends "" rather than omitting the field for users without an email
    // (anonymous sign-ins) — normalize to null so callers can use ?: directly.
    private fun GoTrueUser.toAuthUser() = AuthUser(
        id = id,
        email = email?.takeIf { it.isNotBlank() },
        isAnonymous = is_anonymous,
        displayName = user_metadata?.get("display_name")?.jsonPrimitive?.content,
        avatarUrl = user_metadata?.get("custom_avatar_url")?.jsonPrimitive?.content,
    )

    private fun JsonElement.jsonObject() = this as JsonObject

    private suspend fun execute(request: Request): String = suspendCancellableCoroutine { cont ->
        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWith(Result.failure(AuthException(e.message ?: "network error")))
            }

            override fun onResponse(call: Call, response: Response) {
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching {
                        json.parseToJsonElement(text).jsonObject()
                    }.getOrNull()?.let { obj ->
                        obj["error_description"]?.jsonPrimitive?.content
                            ?: obj["msg"]?.jsonPrimitive?.content
                            ?: obj["error"]?.jsonPrimitive?.content
                            ?: obj["message"]?.jsonPrimitive?.content
                    } ?: "Request failed (${response.code})"
                    cont.resumeWith(Result.failure(AuthException(message)))
                } else {
                    cont.resumeWith(Result.success(text))
                }
            }
        })
    }
}
