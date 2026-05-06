package com.example.biomemo

import com.example.biomemo.data.remote.AuthUser
import com.example.biomemo.data.remote.SupabaseAuthResult
import com.example.biomemo.screens.register.RegisterAuthModel
import com.example.biomemo.screens.register.RegisterContract
import com.example.biomemo.screens.register.RegisterPresenter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegisterPresenterTest {
    @Test
    fun successfulRegistrationPassesEmailAndFieldNameThenReturnsToLogin() = runBlocking {
        val view = FakeRegisterView()
        val model = FakeRegisterModel(SupabaseAuthResult.Success(AuthUser("user-1", "field@biomemo.app")))
        val presenter = RegisterPresenter(view, model)

        presenter.onRegisterClicked(
            email = "field@biomemo.app",
            fieldName = "Field Notes",
            pass = "secret123",
            rePass = "secret123"
        )

        assertEquals("field@biomemo.app", model.email)
        assertEquals("Field Notes", model.fieldName)
        assertEquals("Registration successful", view.successMessage)
        assertTrue(view.navigatedLogin)
    }

    @Test
    fun failedRegistrationShowsSupabaseError() = runBlocking {
        val view = FakeRegisterView()
        val presenter = RegisterPresenter(
            view,
            FakeRegisterModel(SupabaseAuthResult.Failure("User already registered"))
        )

        presenter.onRegisterClicked(
            email = "field@biomemo.app",
            fieldName = "Field Notes",
            pass = "secret123",
            rePass = "secret123"
        )

        assertEquals("User already registered", view.errorMessage)
        assertTrue(!view.navigatedLogin)
    }

    @Test
    fun emptyEmailFieldNameOrPasswordShowsValidationError() = runBlocking {
        val view = FakeRegisterView()
        val presenter = RegisterPresenter(view, FakeRegisterModel())

        presenter.onRegisterClicked("", "", "", "")

        assertEquals("Please complete all fields", view.errorMessage)
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
        var fieldName: String? = null

        override suspend fun registerUser(email: String, password: String, fieldName: String): SupabaseAuthResult {
            this.email = email
            this.fieldName = fieldName
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
