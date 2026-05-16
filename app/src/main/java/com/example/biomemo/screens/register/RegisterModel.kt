package com.example.biomemo.screens.register

import com.example.biomemo.data.remote.SupabaseAuthRepository
import com.example.biomemo.data.remote.SupabaseAuthResult

interface RegisterAuthModel {
    suspend fun registerUser(email: String, password: String, username: String): SupabaseAuthResult
    suspend fun continueWithGoogle(): SupabaseAuthResult
}

class RegisterModel(
    private val authRepository: SupabaseAuthRepository = SupabaseAuthRepository()
) : RegisterAuthModel {
    override suspend fun registerUser(email: String, password: String, username: String): SupabaseAuthResult {
        return authRepository.signUp(email, password, username)
    }

    override suspend fun continueWithGoogle(): SupabaseAuthResult {
        return authRepository.signInWithGoogle()
    }
}
