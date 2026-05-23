package com.example.biomemo

import com.example.biomemo.config.AppConfig
import com.example.biomemo.features.auth.data.SupabaseAuthGateway
import com.example.biomemo.features.auth.data.SupabaseAuthRepository
import com.example.biomemo.features.auth.domain.AuthUser
import com.example.biomemo.features.auth.domain.SupabaseAuthResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseAuthRepositoryTest {
    @Test
    fun signUpTrimsEmailAndUsernameAndPassesMetadata() = runBlocking {
        val gateway = FakeAuthGateway(signUpUser = AuthUser("user-1", "fern@biomemo.app"))
        val repository = SupabaseAuthRepository(gateway)

        val result = repository.signUp(
            email = "  fern@biomemo.app  ",
            password = "secret123",
            username = "  fernkeeper  "
        )

        assertTrue(result is SupabaseAuthResult.Success)
        assertEquals("fern@biomemo.app", gateway.signUpEmail)
        assertEquals("secret123", gateway.signUpPassword)
        assertEquals(mapOf("username" to "fernkeeper"), gateway.signUpMetadata)
    }

    @Test
    fun signInTrimsEmailBeforeCallingGateway() = runBlocking {
        val gateway = FakeAuthGateway(signInUser = AuthUser("user-2", "trail@biomemo.app"))
        val repository = SupabaseAuthRepository(gateway)

        val result = repository.signIn("  trail@biomemo.app ", "secret123")

        assertTrue(result is SupabaseAuthResult.Success)
        assertEquals("trail@biomemo.app", gateway.signInEmail)
        assertEquals("secret123", gateway.signInPassword)
    }

    @Test
    fun signInResolvesUsernameBeforeCallingGateway() = runBlocking {
        val gateway = FakeAuthGateway(
            signInUser = AuthUser("user-2", "trail@biomemo.app"),
            resolvedLogins = mapOf("trailkeeper" to "trail@biomemo.app")
        )
        val repository = SupabaseAuthRepository(gateway)

        val result = repository.signIn("  trailkeeper ", "secret123")

        assertTrue(result is SupabaseAuthResult.Success)
        assertEquals("trailkeeper", gateway.resolvedIdentifiers.single())
        assertEquals("trail@biomemo.app", gateway.signInEmail)
    }

    @Test
    fun signUpExistingEmailReturnsEmailTakenFailure() = runBlocking {
        val gateway = FakeAuthGateway(
            signUpUser = AuthUser("user-1", "fern@biomemo.app"),
            resolvedLogins = mapOf("fern@biomemo.app" to "existing@biomemo.app")
        )
        val repository = SupabaseAuthRepository(gateway)

        val result = repository.signUp(
            email = "fern@biomemo.app",
            password = "secret123",
            username = "fern"
        )

        assertTrue(result is SupabaseAuthResult.Failure)
        assertEquals("Email already in use", (result as SupabaseAuthResult.Failure).message)
        assertFalse(gateway.signUpCalled)
    }

    @Test
    fun signUpExistingUsernameReturnsUsernameTakenFailure() = runBlocking {
        val gateway = FakeAuthGateway(
            signUpUser = AuthUser("user-1", "fern@biomemo.app"),
            resolvedLogins = mapOf("fern" to "existing@biomemo.app")
        )
        val repository = SupabaseAuthRepository(gateway)

        val result = repository.signUp(
            email = "fern@biomemo.app",
            password = "secret123",
            username = "fern"
        )

        assertTrue(result is SupabaseAuthResult.Failure)
        assertEquals("Username already taken", (result as SupabaseAuthResult.Failure).message)
        assertFalse(gateway.signUpCalled)
    }

    @Test
    fun blankCredentialsReturnFailureWithoutCallingGateway() = runBlocking {
        val gateway = FakeAuthGateway()
        val repository = SupabaseAuthRepository(gateway)

        val result = repository.signIn("", "")

        assertTrue(result is SupabaseAuthResult.Failure)
        assertEquals("Please enter username/email and password", (result as SupabaseAuthResult.Failure).message)
        assertFalse(gateway.signInCalled)
    }

    @Test
    fun gatewayExceptionsBecomeFailureResults() = runBlocking {
        val gateway = FakeAuthGateway(failure = IllegalStateException("Invalid login credentials"))
        val repository = SupabaseAuthRepository(gateway)

        val result = repository.signIn("trail@biomemo.app", "wrong-password")

        assertTrue(result is SupabaseAuthResult.Failure)
        assertEquals("Invalid username/email or password", (result as SupabaseAuthResult.Failure).message)
    }

    @Test
    fun weakPasswordExceptionReturnsSafeRequirementMessage() = runBlocking {
        val gateway = FakeAuthGateway(
            failure = IllegalStateException(
                "weak_password (Password should be at least 6 characters.: weak_password) " +
                    "URL: https://example.supabase.co/auth/v1/signup Headers: [Authorization=[Bearer secret]]"
            )
        )
        val repository = SupabaseAuthRepository(gateway)

        val result = repository.signUp("trail@biomemo.app", "123456", "trail")

        assertTrue(result is SupabaseAuthResult.Failure)
        val message = (result as SupabaseAuthResult.Failure).message
        assertEquals("Password must be at least 6 characters.", message)
        assertFalse(message.contains("URL:"))
        assertFalse(message.contains("Authorization"))
    }

    @Test
    fun invalidEmailExceptionReturnsEmailValidationMessage() = runBlocking {
        val gateway = FakeAuthGateway(
            failure = IllegalStateException(
                "invalid_email (Unable to validate email address: invalid format) " +
                    "URL: https://example.supabase.co/auth/v1/signup Headers: [Authorization=[Bearer secret]]"
            )
        )
        val repository = SupabaseAuthRepository(gateway)

        val result = repository.signUp("not-an-email", "secret123", "trail")

        assertTrue(result is SupabaseAuthResult.Failure)
        val message = (result as SupabaseAuthResult.Failure).message
        assertEquals("Invalid email address.", message)
        assertFalse(message.contains("URL:"))
        assertFalse(message.contains("Authorization"))
    }

    @Test
    fun sessionHelpersDelegateToGateway() {
        val gateway = FakeAuthGateway(
            activeSession = true,
            currentUser = AuthUser("user-3", "field@biomemo.app")
        )
        val repository = SupabaseAuthRepository(gateway)

        assertTrue(repository.hasActiveSession())
        assertEquals("field@biomemo.app", repository.currentUser()?.email)
    }

    @Test
    fun googleSignInDelegatesToGateway() = runBlocking {
        val gateway = FakeAuthGateway()
        val repository = SupabaseAuthRepository(gateway)

        val result = repository.signInWithGoogle()

        assertTrue(result is SupabaseAuthResult.Success)
        assertTrue(gateway.googleSignInCalled)
        assertEquals(AppConfig.authRedirectUrl, gateway.googleSignInRedirectUrl)
    }

    @Test
    fun signOutClearsLocalSessionWhenGatewaySignOutFails() = runBlocking {
        val gateway = FakeAuthGateway(signOutFailure = IllegalStateException("Connection reset"))
        val repository = SupabaseAuthRepository(gateway)

        val result = repository.signOut()

        assertTrue(result is SupabaseAuthResult.Success)
        assertTrue(gateway.signOutCalled)
        assertTrue(gateway.clearLocalSessionCalled)
    }

    @Test
    fun restorePersistedSessionDelegatesToGateway() = runBlocking {
        val gateway = FakeAuthGateway()
        val repository = SupabaseAuthRepository(gateway)

        repository.restorePersistedSession()

        assertTrue(gateway.restorePersistedSessionCalled)
    }
}

private class FakeAuthGateway(
    private val signUpUser: AuthUser? = null,
    private val signInUser: AuthUser? = null,
    private val resolvedLogins: Map<String, String> = emptyMap(),
    private val activeSession: Boolean = false,
    private val currentUser: AuthUser? = null,
    private val failure: Throwable? = null,
    private val signOutFailure: Throwable? = null
) : SupabaseAuthGateway {
    var signUpCalled = false
    var signUpEmail: String? = null
    var signUpPassword: String? = null
    var signUpMetadata: Map<String, String> = emptyMap()
    var signInCalled = false
    var googleSignInCalled = false
    var restorePersistedSessionCalled = false
    var signOutCalled = false
    var clearLocalSessionCalled = false
    var googleSignInRedirectUrl: String? = null
    var signInEmail: String? = null
    var signInPassword: String? = null
    val resolvedIdentifiers = mutableListOf<String>()

    override suspend fun signUp(email: String, password: String, metadata: Map<String, String>): AuthUser? {
        failure?.let { throw it }
        signUpCalled = true
        signUpEmail = email
        signUpPassword = password
        signUpMetadata = metadata
        return signUpUser
    }

    override suspend fun signIn(email: String, password: String): AuthUser? {
        failure?.let { throw it }
        signInCalled = true
        signInEmail = email
        signInPassword = password
        return signInUser
    }

    override suspend fun signInWithGoogle(redirectUrl: String) {
        failure?.let { throw it }
        googleSignInCalled = true
        googleSignInRedirectUrl = redirectUrl
    }

    override suspend fun restorePersistedSession() {
        failure?.let { throw it }
        restorePersistedSessionCalled = true
    }

    override suspend fun resolveLoginEmail(identifier: String): String? {
        resolvedIdentifiers.add(identifier)
        return resolvedLogins[identifier]
    }

    override suspend fun signOut() {
        signOutCalled = true
        signOutFailure?.let { throw it }
    }

    override suspend fun clearLocalSession() {
        clearLocalSessionCalled = true
    }

    override fun hasActiveSession(): Boolean = activeSession

    override fun currentUser(): AuthUser? = currentUser
}
