package com.example.biomemo

import com.example.biomemo.features.auth.domain.AuthRepository
import com.example.biomemo.features.auth.domain.AuthUseCases
import com.example.biomemo.features.auth.domain.AuthUser
import com.example.biomemo.features.auth.domain.ExplorerProfile
import com.example.biomemo.features.auth.domain.ProfileRepository
import com.example.biomemo.features.auth.domain.ProfileResult
import com.example.biomemo.features.auth.domain.ProfileUseCases
import com.example.biomemo.features.auth.domain.SupabaseAuthResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthUseCasesTest {
    @Test
    fun authUseCasesDelegateToRepository() = runBlocking {
        val repository = FakeAuthRepository()
        val useCases = AuthUseCases(repository)

        assertTrue(useCases.signUp("fern@biomemo.app", "secret123", "fern") is SupabaseAuthResult.Success)
        assertTrue(useCases.signIn("fern", "secret123") is SupabaseAuthResult.Success)
        assertTrue(useCases.signInWithGoogle() is SupabaseAuthResult.Success)
        assertTrue(useCases.signOut() is SupabaseAuthResult.Success)
        useCases.restorePersistedSession()

        assertEquals("fern@biomemo.app", repository.signUpEmail)
        assertEquals("fern", repository.signInIdentifier)
        assertTrue(repository.googleSignInCalled)
        assertTrue(repository.signOutCalled)
        assertTrue(repository.restoreCalled)
        assertTrue(useCases.hasActiveSession())
        assertEquals("auth-user-1", useCases.currentUser()?.id)
    }

    @Test
    fun profileUseCasesDelegateToRepository() = runBlocking {
        val profile = ExplorerProfile(
            id = "auth-user-1",
            username = "fern",
            email = "fern@biomemo.app",
            avatarUrl = null
        )
        val useCases = ProfileUseCases(FakeProfileRepository(ProfileResult.Success(profile)))

        val result = useCases.loadCurrentProfile()

        assertTrue(result is ProfileResult.Success)
        assertEquals("fern", (result as ProfileResult.Success).profile.username)
    }

    private class FakeAuthRepository : AuthRepository {
        var signUpEmail: String? = null
        var signInIdentifier: String? = null
        var googleSignInCalled = false
        var signOutCalled = false
        var restoreCalled = false

        override suspend fun signUp(email: String, password: String, username: String): SupabaseAuthResult {
            signUpEmail = email
            return SupabaseAuthResult.Success(AuthUser("auth-user-1", email))
        }

        override suspend fun signIn(identifier: String, password: String): SupabaseAuthResult {
            signInIdentifier = identifier
            return SupabaseAuthResult.Success(AuthUser("auth-user-1", "fern@biomemo.app"))
        }

        override suspend fun signOut(): SupabaseAuthResult {
            signOutCalled = true
            return SupabaseAuthResult.Success(null)
        }

        override suspend fun signInWithGoogle(): SupabaseAuthResult {
            googleSignInCalled = true
            return SupabaseAuthResult.Success(null)
        }

        override suspend fun restorePersistedSession() {
            restoreCalled = true
        }

        override fun hasActiveSession(): Boolean = true

        override fun currentUser(): AuthUser = AuthUser("auth-user-1", "fern@biomemo.app")
    }

    private class FakeProfileRepository(
        private val result: ProfileResult
    ) : ProfileRepository {
        override suspend fun loadCurrentProfile(): ProfileResult = result
    }
}
