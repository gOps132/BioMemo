package com.example.biomemo.screens.login

import com.example.biomemo.features.auth.domain.AuthUseCases
import com.example.biomemo.features.auth.domain.SupabaseAuthResult

interface LoginAuthModel {
    suspend fun authenticate(email: String, password: String): SupabaseAuthResult
    suspend fun authenticateWithGoogle(): SupabaseAuthResult
}

class LoginModel(
    private val authUseCases: AuthUseCases = AuthUseCases()
) : LoginAuthModel {
    override suspend fun authenticate(email: String, password: String): SupabaseAuthResult {
        return authUseCases.signIn(email, password)
    }

    override suspend fun authenticateWithGoogle(): SupabaseAuthResult {
        return authUseCases.signInWithGoogle()
    }
}
