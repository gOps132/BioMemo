package com.example.biomemo.features.auth.data

import com.example.biomemo.data.remote.SupabaseClientProvider
import com.example.biomemo.features.auth.domain.AuthUser
import com.example.biomemo.features.auth.domain.ExplorerProfile
import com.example.biomemo.features.auth.domain.ProfileRepository
import com.example.biomemo.features.auth.domain.ProfileResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface SupabaseProfileGateway {
    fun currentUser(): AuthUser?
    suspend fun fetchProfile(userId: String): SupabaseProfileRow?
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
}
