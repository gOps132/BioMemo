package com.example.biomemo.screens.login

import com.example.biomemo.data.remote.SupabaseAuthRepository
import com.example.biomemo.data.remote.SupabaseAuthResult

interface LoginAuthModel {
    suspend fun authenticate(email: String, password: String): SupabaseAuthResult
    suspend fun authenticateWithGoogle(): SupabaseAuthResult
}

class LoginModel(
    private val authRepository: SupabaseAuthRepository = SupabaseAuthRepository()
) : LoginAuthModel {
    override suspend fun authenticate(email: String, password: String): SupabaseAuthResult {
        return authRepository.signIn(email, password)
    }

    override suspend fun authenticateWithGoogle(): SupabaseAuthResult {
        return authRepository.signInWithGoogle()
    }
}
