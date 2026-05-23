package com.example.biomemo

import com.example.biomemo.features.auth.domain.AuthUser
import com.example.biomemo.features.auth.domain.SupabaseAuthResult
import com.example.biomemo.screens.register.RegisterAuthModel
import com.example.biomemo.screens.register.RegisterContract
import com.example.biomemo.screens.register.RegisterPresenter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegisterPresenterTest {
    @Test
    fun successfulRegistrationPassesEmailAndUsernameThenReturnsToLogin() = runBlocking {
        val view = FakeRegisterView()
        val model = FakeRegisterModel(SupabaseAuthResult.Success(AuthUser("user-1", "field@biomemo.app")))
        val presenter = RegisterPresenter(view, model)

        presenter.onRegisterClicked(
            email = "field@biomemo.app",
            username = "fieldnotes",
            pass = "secret123",
            rePass = "secret123"
        )

        assertEquals("field@biomemo.app", model.email)
        assertEquals("fieldnotes", model.username)
        assertEquals("Registration successful", view.successMessage)
        assertTrue(view.navigatedLogin)
    }

    @Test
    fun failedRegistrationShowsFriendlyError() = runBlocking {
        val view = FakeRegisterView()
        val presenter = RegisterPresenter(
            view,
            FakeRegisterModel(SupabaseAuthResult.Failure("Email already in use"))
        )

        presenter.onRegisterClicked(
            email = "field@biomemo.app",
            username = "fieldnotes",
            pass = "secret123",
            rePass = "secret123"
        )

        assertEquals("Email already in use", view.errorMessage)
        assertTrue(!view.navigatedLogin)
    }

    @Test
    fun emptyEmailUsernameOrPasswordShowsValidationError() = runBlocking {
        val view = FakeRegisterView()
        val presenter = RegisterPresenter(view, FakeRegisterModel())

        presenter.onRegisterClicked("", "", "", "")

        assertEquals("Please fill out all fields", view.errorMessage)
    }

    @Test
    fun shortPasswordShowsPasswordRequirement() = runBlocking {
        val view = FakeRegisterView()
        val model = FakeRegisterModel()
        val presenter = RegisterPresenter(view, model)

        presenter.onRegisterClicked(
            email = "field@biomemo.app",
            username = "fieldnotes",
            pass = "12345",
            rePass = "12345"
        )

        assertEquals("Password must be at least 6 characters.", view.errorMessage)
        assertTrue(!model.registerCalled)
    }

    @Test
    fun invalidEmailShowsEmailValidationError() = runBlocking {
        val view = FakeRegisterView()
        val model = FakeRegisterModel()
        val presenter = RegisterPresenter(view, model)

        presenter.onRegisterClicked(
            email = "not-an-email",
            username = "fieldnotes",
            pass = "secret123",
            rePass = "secret123"
        )

        assertEquals("Invalid email address.", view.errorMessage)
        assertTrue(!model.registerCalled)
    }

    @Test
    fun googleSignInLaunchesSupabaseOAuth() = runBlocking {
        val view = FakeRegisterView()
        val presenter = RegisterPresenter(view, FakeRegisterModel())

        presenter.onGoogleSignInClicked()

        assertEquals("Continue in your browser to finish Google sign-in.", view.successMessage)
    }

    private class FakeRegisterModel(
        private val result: SupabaseAuthResult = SupabaseAuthResult.Success(AuthUser("user-1", "field@biomemo.app"))
    ) : RegisterAuthModel {
        var email: String? = null
        var username: String? = null
        var registerCalled = false

        override suspend fun registerUser(email: String, password: String, username: String): SupabaseAuthResult {
            registerCalled = true
            this.email = email
            this.username = username
            return result
        }

        override suspend fun continueWithGoogle(): SupabaseAuthResult = result
    }

    private class FakeRegisterView : RegisterContract.View {
        var successMessage: String? = null
        var errorMessage: String? = null
        var navigatedLogin = false

        override fun showSuccess(message: String) {
            successMessage = message
        }

        override fun showError(message: String) {
            errorMessage = message
        }

        override fun navigateToLogin() {
            navigatedLogin = true
        }
    }
}
