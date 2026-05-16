package com.example.biomemo.data.remote

import com.example.biomemo.config.AppConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class AuthUser(
    val id: String,
    val email: String?
)

sealed class SupabaseAuthResult {
    data class Success(val user: AuthUser?) : SupabaseAuthResult()
    data class Failure(val message: String) : SupabaseAuthResult()
}

interface SupabaseAuthGateway {
    suspend fun signUp(email: String, password: String, metadata: Map<String, String>): AuthUser?
    suspend fun signIn(email: String, password: String): AuthUser?
    suspend fun signInWithGoogle(redirectUrl: String)
    suspend fun restorePersistedSession()
    suspend fun resolveLoginEmail(identifier: String): String?
    suspend fun signOut()
    suspend fun clearLocalSession()
    fun hasActiveSession(): Boolean
    fun currentUser(): AuthUser?
}

class SupabaseAuthRepository(
    private val gateway: SupabaseAuthGateway = SupabaseAuthSdkGateway()
) {
    suspend fun signUp(email: String, password: String, username: String): SupabaseAuthResult {
        val cleanEmail = email.trim()
        val cleanUsername = username.trim()
        if (cleanEmail.isEmpty() || cleanUsername.isEmpty() || password.isEmpty()) {
            return SupabaseAuthResult.Failure("Please fill out all fields")
        }

        val existingEmail = resolveExistingLogin(cleanEmail)
        if (existingEmail != null) {
            return SupabaseAuthResult.Failure("Email already in use")
        }

        val existingUsername = resolveExistingLogin(cleanUsername)
        if (existingUsername != null) {
            return SupabaseAuthResult.Failure("Username already taken")
        }

        return runAuthCall {
            gateway.signUp(
                email = cleanEmail,
                password = password,
                metadata = metadataFor(cleanUsername)
            )
        }
    }

    suspend fun signIn(identifier: String, password: String): SupabaseAuthResult {
        val cleanIdentifier = identifier.trim()
        if (cleanIdentifier.isEmpty() || password.isEmpty()) {
            return SupabaseAuthResult.Failure("Please enter username/email and password")
        }

        val email = if (cleanIdentifier.isEmailLike()) {
            cleanIdentifier
        } else {
            resolveExistingLogin(cleanIdentifier)
        }

        if (email.isNullOrBlank()) {
            return SupabaseAuthResult.Failure("No account found for that username or email. Try your email if needed.")
        }

        return runAuthCall {
            gateway.signIn(email, password)
        }
    }

    suspend fun signOut(): SupabaseAuthResult {
        return try {
            gateway.signOut()
            SupabaseAuthResult.Success(null)
        } catch (_: Throwable) {
            runCatching { gateway.clearLocalSession() }
            SupabaseAuthResult.Success(null)
        }
    }

    suspend fun signInWithGoogle(): SupabaseAuthResult {
        return runAuthCall {
            gateway.signInWithGoogle(AppConfig.authRedirectUrl)
            null
        }
    }

    suspend fun restorePersistedSession() {
        gateway.restorePersistedSession()
    }

    fun hasActiveSession(): Boolean = gateway.hasActiveSession()

    fun currentUser(): AuthUser? = gateway.currentUser()

    private suspend fun runAuthCall(block: suspend () -> AuthUser?): SupabaseAuthResult {
        return try {
            SupabaseAuthResult.Success(block())
        } catch (error: Throwable) {
            SupabaseAuthResult.Failure(friendlyAuthMessage(error.message))
        }
    }

    private fun metadataFor(username: String): Map<String, String> {
        return if (username.isEmpty()) {
            emptyMap()
        } else {
            mapOf("username" to username)
        }
    }

    private suspend fun resolveExistingLogin(identifier: String): String? {
        return try {
            gateway.resolveLoginEmail(identifier.trim())?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun friendlyAuthMessage(message: String?): String {
        val normalized = message.orEmpty().lowercase()
        return when {
            normalized.contains("invalid_email") ||
                normalized.contains("invalid email") ||
                normalized.contains("validate email") ||
                normalized.contains("email address") && normalized.contains("invalid format") -> {
                    "Invalid email address."
                }
            normalized.contains("weak_password") ||
                normalized.contains("password should be at least") ||
                normalized.contains("password must be at least") -> {
                    "Password must be at least 6 characters."
                }
            normalized.contains("already registered") ||
                normalized.contains("user already registered") -> "Email already in use"
            normalized.contains("username") && (
                normalized.contains("already exists") ||
                    normalized.contains("duplicate key") ||
                    normalized.contains("23505")
                ) -> "Username already taken"
            normalized.contains("already exists") ||
                normalized.contains("duplicate key") ||
                normalized.contains("23505") -> "Email or username already in use"
            normalized.contains("invalid login credentials") -> "Invalid username/email or password"
            normalized.contains("email not confirmed") -> "Please confirm your email before signing in"
            normalized.contains("over_email_send_rate_limit") -> {
                "Too many auth emails were sent. Try again in a minute."
            }
            normalized.contains("network") ||
                normalized.contains("timeout") ||
                normalized.contains("unable to resolve host") -> {
                    "Network error. Check your connection and try again."
                }
            else -> "Authentication failed. Please try again."
        }
    }

    private fun String.isEmailLike(): Boolean {
        return contains("@")
    }
}

@Serializable
private data class ResolveLoginIdentifierParams(
    val identifier: String
)

@Serializable
private data class ResolvedLoginEmail(
    val email: String
)

class SupabaseAuthSdkGateway(
    private val client: SupabaseClient = SupabaseClientProvider.client
) : SupabaseAuthGateway {
    override suspend fun signUp(email: String, password: String, metadata: Map<String, String>): AuthUser? {
        val userInfo = client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            data = buildJsonObject {
                metadata.forEach { (key, value) -> put(key, value) }
            }
        }

        return userInfo.toAuthUser()
    }

    override suspend fun signIn(email: String, password: String): AuthUser? {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }

        return currentUser()
    }

    override suspend fun signInWithGoogle(redirectUrl: String) {
        client.auth.signInWith(Google, redirectUrl = redirectUrl.urlEncoded())
    }

    override suspend fun restorePersistedSession() {
        client.auth.awaitInitialization()
    }

    override suspend fun resolveLoginEmail(identifier: String): String? {
        return client.postgrest
            .rpc(
                function = "resolve_login_identifier",
                parameters = ResolveLoginIdentifierParams(identifier = identifier)
            )
            .decodeSingleOrNull<ResolvedLoginEmail>()
            ?.email
    }

    override suspend fun signOut() {
        client.auth.signOut()
    }

    override suspend fun clearLocalSession() {
        client.auth.clearSession()
    }

    override fun hasActiveSession(): Boolean {
        return client.auth.currentSessionOrNull() != null
    }

    override fun currentUser(): AuthUser? {
        return client.auth.currentSessionOrNull()?.user.toAuthUser() ?: client.auth.currentUserOrNull().toAuthUser()
    }

    private fun UserInfo?.toAuthUser(): AuthUser? {
        return this?.let { AuthUser(id = it.id, email = it.email) }
    }

    private fun String.urlEncoded(): String {
        return URLEncoder.encode(this, StandardCharsets.UTF_8.toString())
    }
}
