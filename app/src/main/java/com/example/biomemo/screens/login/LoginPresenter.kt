package com.example.biomemo.screens.login

import com.example.biomemo.data.remote.SupabaseAuthResult

class LoginPresenter(
    private val view: LoginContract.View,
    private val model: LoginAuthModel
) : LoginContract.Presenter {

    override suspend fun onLoginClicked(user: String, pass: String) {
        val cleanUser = user.trim()

        if (cleanUser.isEmpty() && pass.isEmpty()) {
            view.showError("Please fill out all fields")
            return
        }

        if (cleanUser.isEmpty()) {
            view.showError("Please enter your username or email")
            return
        }

        if (pass.isEmpty()) {
            view.showError("Please enter your password")
            return
        }

        when (val result = model.authenticate(cleanUser, pass)) {
            is SupabaseAuthResult.Success -> {
                val authUser = result.user
                if (authUser == null) {
                    view.showError("Sign-in finished without an active session. Please try again.")
                    return
                }
                val email = authUser.email ?: cleanUser
                view.showLoginSuccess(email)
                view.navigateToDashboard(email)
            }
            is SupabaseAuthResult.Failure -> view.showError(result.message)
        }
    }

    override fun onRegisterClicked() {
        view.navigateToRegister()
    }

    override suspend fun onGoogleSignInClicked() {
        when (val result = model.authenticateWithGoogle()) {
            is SupabaseAuthResult.Success -> view.showGoogleAuthStarted()
            is SupabaseAuthResult.Failure -> view.showError(result.message)
        }
    }
}
