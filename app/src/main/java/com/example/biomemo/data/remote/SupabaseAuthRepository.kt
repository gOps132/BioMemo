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
    suspend fun resolveLoginEmail(identifier: String): String?
    suspend fun signOut()
    fun hasActiveSession(): Boolean
    fun currentUser(): AuthUser?
}

class SupabaseAuthRepository(
    private val gateway: SupabaseAuthGateway = SupabaseAuthSdkGateway()
) {
    suspend fun signUp(email: String, password: String, fieldName: String): SupabaseAuthResult {
        val cleanEmail = email.trim()
        val cleanFieldName = fieldName.trim()
        if (cleanEmail.isEmpty() || cleanFieldName.isEmpty() || password.isEmpty()) {
            return SupabaseAuthResult.Failure("Please enter email, username, and password")
        }

        val existingEmail = resolveExistingLogin(cleanEmail)
        val existingUsername = resolveExistingLogin(cleanFieldName)
        if (existingEmail != null || existingUsername != null) {
            return SupabaseAuthResult.Failure("Username or email already exists")
        }

        return runAuthCall {
            gateway.signUp(
                email = cleanEmail,
                password = password,
                metadata = metadataFor(cleanFieldName)
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
        return runAuthCall {
            gateway.signOut()
            null
        }
    }

    suspend fun signInWithGoogle(): SupabaseAuthResult {
        return runAuthCall {
            gateway.signInWithGoogle(AppConfig.authRedirectUrl)
            null
        }
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

    private fun metadataFor(fieldName: String): Map<String, String> {
        return if (fieldName.isEmpty()) {
            emptyMap()
        } else {
            mapOf(
                "field_name" to fieldName,
                "username" to fieldName
            )
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
        val fallback = message ?: "Authentication failed"
        val normalized = fallback.lowercase()
        return when {
            normalized.contains("already registered") ||
                normalized.contains("already exists") ||
                normalized.contains("duplicate key") ||
                normalized.contains("23505") -> "Username or email already exists"
            normalized.contains("invalid login credentials") -> "Invalid username/email or password"
            normalized.contains("email not confirmed") -> "Please confirm your email before signing in"
            normalized.contains("over_email_send_rate_limit") -> {
                "Too many auth emails were sent. Try again in a minute."
            }
            else -> fallback
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

    override fun hasActiveSession(): Boolean {
        return client.auth.currentSessionOrNull() != null
    }

    override fun currentUser(): AuthUser? {
        return client.auth.currentUserOrNull().toAuthUser()
    }

    private fun UserInfo?.toAuthUser(): AuthUser? {
        return this?.let { AuthUser(id = it.id, email = it.email) }
    }

    private fun String.urlEncoded(): String {
        return URLEncoder.encode(this, StandardCharsets.UTF_8.toString())
    }
}
