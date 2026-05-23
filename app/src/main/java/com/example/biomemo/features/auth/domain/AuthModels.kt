package com.example.biomemo.features.auth.domain

data class AuthUser(
    val id: String,
    val email: String?
)

sealed class SupabaseAuthResult {
    data class Success(val user: AuthUser?) : SupabaseAuthResult()
    data class Failure(val message: String) : SupabaseAuthResult()
}

data class ExplorerProfile(
    val id: String,
    val username: String?,
    val email: String?,
    val avatarUrl: String?
)

sealed class ProfileResult {
    data class Success(val profile: ExplorerProfile) : ProfileResult()
    data class Failure(val message: String) : ProfileResult()
}
