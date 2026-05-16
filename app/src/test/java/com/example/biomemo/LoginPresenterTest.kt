package com.example.biomemo

import com.example.biomemo.screens.login.LoginContract
import com.example.biomemo.screens.login.LoginAuthModel
import com.example.biomemo.screens.login.LoginPresenter
import com.example.biomemo.data.remote.AuthUser
import com.example.biomemo.data.remote.SupabaseAuthResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginPresenterTest {
    @Test
    fun googleSignInLaunchesSupabaseOAuth() = runBlocking {
        val view = FakeLoginView()
        val presenter = LoginPresenter(view, FakeLoginModel())

        presenter.onGoogleSignInClicked()

        assertTrue(view.googleAuthStarted)
    }

    @Test
    fun emptyCredentialsStillShowValidationError() {
        val view = FakeLoginView()
        val presenter = LoginPresenter(view, FakeLoginModel())

        runBlocking { presenter.onLoginClicked("", "") }

        assertEquals("Please enter username/email and password", view.lastError)
        assertTrue(view.navigatedDashboardUser == null)
    }

    @Test
    fun successfulLoginNavigatesToDashboard() = runBlocking {
        val view = FakeLoginView()
        val presenter = LoginPresenter(
            view,
            FakeLoginModel(SupabaseAuthResult.Success(AuthUser("user-1", "trail@biomemo.app")))
        )

        presenter.onLoginClicked("trail@biomemo.app", "secret123")

        assertEquals("trail@biomemo.app", view.successUser)
        assertEquals("trail@biomemo.app", view.navigatedDashboardUser)
    }

    @Test
    fun failedLoginShowsErrorAndDoesNotNavigate() = runBlocking {
        val view = FakeLoginView()
        val presenter = LoginPresenter(
            view,
            FakeLoginModel(SupabaseAuthResult.Failure("Invalid login credentials"))
        )

        presenter.onLoginClicked("trail@biomemo.app", "wrong")

        assertEquals("Invalid login credentials", view.lastError)
        assertTrue(view.navigatedDashboardUser == null)
    }

    @Test
    fun loginWithoutUserDoesNotNavigateToDashboard() = runBlocking {
        val view = FakeLoginView()
        val presenter = LoginPresenter(
            view,
            FakeLoginModel(SupabaseAuthResult.Success(null))
        )

        presenter.onLoginClicked("trail@biomemo.app", "secret123")

        assertEquals("Sign-in finished without an active session. Please try again.", view.lastError)
        assertTrue(view.navigatedDashboardUser == null)
    }

    private class FakeLoginView : LoginContract.View {
        var lastError: String? = null
        var successUser: String? = null
        var navigatedDashboardUser: String? = null
        var googleAuthStarted = false

        override fun showLoginSuccess(username: String) {
            successUser = username
        }
        override fun showGoogleAuthStarted() {
            googleAuthStarted = true
        }
        override fun showError(message: String) {
            lastError = message
        }
        override fun navigateToRegister() = Unit
        override fun navigateToDashboard(username: String) {
            navigatedDashboardUser = username
        }
    }

    private class FakeLoginModel(
        private val result: SupabaseAuthResult = SupabaseAuthResult.Success(AuthUser("user-1", "user@biomemo.app"))
    ) : LoginAuthModel {
        override suspend fun authenticate(email: String, password: String): SupabaseAuthResult = result
        override suspend fun authenticateWithGoogle(): SupabaseAuthResult = result
    }
}
