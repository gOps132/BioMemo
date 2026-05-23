package com.example.biomemo.screens.register

import com.example.biomemo.features.auth.domain.AuthUseCases
import com.example.biomemo.features.auth.domain.SupabaseAuthResult

interface RegisterAuthModel {
    suspend fun registerUser(email: String, password: String, username: String): SupabaseAuthResult
    suspend fun continueWithGoogle(): SupabaseAuthResult
}

class RegisterModel(
    private val authUseCases: AuthUseCases = AuthUseCases()
) : RegisterAuthModel {
    override suspend fun registerUser(email: String, password: String, username: String): SupabaseAuthResult {
        return authUseCases.signUp(email, password, username)
    }

    override suspend fun continueWithGoogle(): SupabaseAuthResult {
        return authUseCases.signInWithGoogle()
    }
}
