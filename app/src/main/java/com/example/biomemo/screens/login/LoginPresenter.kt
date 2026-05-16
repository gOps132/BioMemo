package com.example.biomemo.screens.login

import com.example.biomemo.data.remote.SupabaseAuthResult

class LoginPresenter(
    private val view: LoginContract.View,
    private val model: LoginAuthModel
) : LoginContract.Presenter {

    override suspend fun onLoginClicked(user: String, pass: String) {
        if (user.isEmpty() || pass.isEmpty()) {
            view.showError("Please enter username/email and password")
            return
        }

        when (val result = model.authenticate(user, pass)) {
            is SupabaseAuthResult.Success -> {
                val authUser = result.user
                if (authUser == null) {
                    view.showError("Sign-in finished without an active session. Please try again.")
                    return
                }
                val email = authUser.email ?: user
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
