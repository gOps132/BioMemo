package com.example.biomemo.features.auth.domain

interface AuthRepository {
    suspend fun signUp(email: String, password: String, username: String): SupabaseAuthResult
    suspend fun signIn(identifier: String, password: String): SupabaseAuthResult
    suspend fun signOut(): SupabaseAuthResult
    suspend fun signInWithGoogle(): SupabaseAuthResult
    suspend fun restorePersistedSession()
    fun hasActiveSession(): Boolean
    fun currentUser(): AuthUser?
}

interface ProfileRepository {
    suspend fun loadCurrentProfile(): ProfileResult
}
