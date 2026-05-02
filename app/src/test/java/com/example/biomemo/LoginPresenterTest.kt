package com.example.biomemo

import com.example.biomemo.screens.login.LoginContract
import com.example.biomemo.screens.login.LoginModel
import com.example.biomemo.screens.login.LoginPresenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginPresenterTest {
    @Test
    fun googleSignInShowsDisabledScaffoldMessage() {
        val view = FakeLoginView()
        val presenter = LoginPresenter(view, LoginModel())

        presenter.onGoogleSignInClicked()

        assertEquals("Google sign-in will be enabled with Supabase auth.", view.lastError)
    }

    @Test
    fun emptyCredentialsStillShowValidationError() {
        val view = FakeLoginView()
        val presenter = LoginPresenter(view, LoginModel())

        presenter.onLoginClicked("", "")

        assertEquals("Please enter username and password", view.lastError)
        assertTrue(view.navigatedDashboardUser == null)
    }

    private class FakeLoginView : LoginContract.View {
        var lastError: String? = null
        var navigatedDashboardUser: String? = null

        override fun showLoginSuccess(username: String) = Unit
        override fun showError(message: String) {
            lastError = message
        }
        override fun navigateToRegister() = Unit
        override fun navigateToDashboard(username: String) {
            navigatedDashboardUser = username
        }
    }
}
