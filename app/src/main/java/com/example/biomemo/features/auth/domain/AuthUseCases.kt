package com.example.biomemo.features.auth.domain

import com.example.biomemo.features.auth.data.SupabaseAuthRepository
import com.example.biomemo.features.auth.data.SupabaseProfileRepository

data class AuthUseCases(
    val repository: AuthRepository = SupabaseAuthRepository()
) {
    val signUp = SignUp(repository)
    val signIn = SignIn(repository)
    val signOut = SignOut(repository)
    val signInWithGoogle = SignInWithGoogle(repository)
    val restorePersistedSession = RestorePersistedSession(repository)
    val hasActiveSession = HasActiveSession(repository)
    val currentUser = CurrentUser(repository)
}

class SignUp(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String, username: String): SupabaseAuthResult {
        return repository.signUp(email, password, username)
    }
}

class SignIn(private val repository: AuthRepository) {
    suspend operator fun invoke(identifier: String, password: String): SupabaseAuthResult {
        return repository.signIn(identifier, password)
    }
}

class SignOut(private val repository: AuthRepository) {
    suspend operator fun invoke(): SupabaseAuthResult = repository.signOut()
}

class SignInWithGoogle(private val repository: AuthRepository) {
    suspend operator fun invoke(): SupabaseAuthResult = repository.signInWithGoogle()
}

class RestorePersistedSession(private val repository: AuthRepository) {
    suspend operator fun invoke() = repository.restorePersistedSession()
}

class HasActiveSession(private val repository: AuthRepository) {
    operator fun invoke(): Boolean = repository.hasActiveSession()
}

class CurrentUser(private val repository: AuthRepository) {
    operator fun invoke(): AuthUser? = repository.currentUser()
}

data class ProfileUseCases(
    val repository: ProfileRepository = SupabaseProfileRepository()
) {
    val loadCurrentProfile = LoadCurrentProfile(repository)
    val saveUsername = SaveProfileUsername(repository)
}

class LoadCurrentProfile(private val repository: ProfileRepository) {
    suspend operator fun invoke(): ProfileResult = repository.loadCurrentProfile()
}

class SaveProfileUsername(private val repository: ProfileRepository) {
    suspend operator fun invoke(username: String): ProfileResult = repository.saveUsername(username)
}
