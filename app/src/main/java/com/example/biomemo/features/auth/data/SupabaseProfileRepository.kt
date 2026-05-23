package com.example.biomemo.features.auth.data

import com.example.biomemo.data.remote.SupabaseClientProvider
import com.example.biomemo.features.auth.domain.AuthUser
import com.example.biomemo.features.auth.domain.ExplorerProfile
import com.example.biomemo.features.auth.domain.ProfileRepository
import com.example.biomemo.features.auth.domain.ProfileResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface SupabaseProfileGateway {
    fun currentUser(): AuthUser?
    suspend fun fetchProfile(userId: String): SupabaseProfileRow?
    suspend fun updateUsername(username: String): SupabaseProfileRow
}

@Serializable
data class SupabaseProfileRow(
    val id: String,
    val username: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null
)

class SupabaseProfileRepository(
    private val gateway: SupabaseProfileGateway = SupabaseProfileSdkGateway()
) : ProfileRepository {
    override suspend fun loadCurrentProfile(): ProfileResult {
        val user = gateway.currentUser()
            ?: return ProfileResult.Failure("Please sign in again to view your profile")

        return try {
            val row = gateway.fetchProfile(user.id)
                ?: return ProfileResult.Failure("Profile not found")

            ProfileResult.Success(
                ExplorerProfile(
                    id = user.id,
                    username = row.username,
                    email = user.email,
                    avatarUrl = row.avatarUrl
                )
            )
        } catch (error: Throwable) {
            ProfileResult.Failure(error.message ?: "Profile unavailable")
        }
    }

    override suspend fun saveUsername(username: String): ProfileResult {
        val cleanUsername = username.trim()
        val user = gateway.currentUser()
            ?: return ProfileResult.Failure("Please sign in again to choose a username")

        val validationError = validateUsername(cleanUsername)
        if (validationError != null) return ProfileResult.Failure(validationError)

        return try {
            val row = gateway.updateUsername(cleanUsername)
            ProfileResult.Success(
                ExplorerProfile(
                    id = user.id,
                    username = row.username,
                    email = user.email,
                    avatarUrl = row.avatarUrl
                )
            )
        } catch (error: Throwable) {
            ProfileResult.Failure(friendlyProfileMessage(error.message))
        }
    }

    private fun validateUsername(username: String): String? {
        return when {
            username.isBlank() -> "Choose a username"
            username.length !in USERNAME_MIN_LENGTH..USERNAME_MAX_LENGTH -> {
                "Username must be 3-24 characters."
            }
            !username.matches(USERNAME_PATTERN) -> {
                "Use letters, numbers, underscores, or hyphens only."
            }
            else -> null
        }
    }

    private fun friendlyProfileMessage(message: String?): String {
        val normalized = message.orEmpty().lowercase()
        return when {
            normalized.contains("username_already_taken") ||
                normalized.contains("duplicate key") ||
                normalized.contains("23505") -> "Username already taken"
            normalized.contains("invalid_username") -> "Username must be 3-24 characters."
            normalized.contains("network") ||
                normalized.contains("timeout") ||
                normalized.contains("unable to resolve host") -> "Network error. Check your connection and try again."
            else -> "Could not save username. Please try again."
        }
    }

    private companion object {
        const val USERNAME_MIN_LENGTH = 3
        const val USERNAME_MAX_LENGTH = 24
        val USERNAME_PATTERN = Regex("^[A-Za-z0-9_-]+$")
    }
}

class SupabaseProfileSdkGateway(
    private val authRepository: SupabaseAuthRepository = SupabaseAuthRepository(),
    private val client: SupabaseClient = SupabaseClientProvider.client
) : SupabaseProfileGateway {
    override fun currentUser(): AuthUser? = authRepository.currentUser()

    override suspend fun fetchProfile(userId: String): SupabaseProfileRow? {
        return client.postgrest["profiles"]
            .select {
                filter {
                    eq("id", userId)
                }
            }
            .decodeSingleOrNull<SupabaseProfileRow>()
    }

    override suspend fun updateUsername(username: String): SupabaseProfileRow {
        return client.postgrest
            .rpc(
                function = "set_current_profile_username",
                parameters = SetProfileUsernameParams(username = username)
            )
            .decodeSingle<SupabaseProfileRow>()
    }
}

@Serializable
private data class SetProfileUsernameParams(
    @SerialName("p_username") val username: String
)
