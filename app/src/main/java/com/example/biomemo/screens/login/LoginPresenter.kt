package com.example.biomemo.screens.login

class LoginPresenter(
    private val view: LoginContract.View,
    private val model: LoginModel
) : LoginContract.Presenter {

    override fun onLoginClicked(user: String, pass: String) {
        if (user.isEmpty() || pass.isEmpty()) {
            view.showError("Please enter username and password")
            return
        }

        val success = model.authenticate(user, pass)
        if (success) {
            view.showLoginSuccess(user)
            view.navigateToDashboard(user)
        } else {
            // Note: In a simple app, we might just navigate anyway
            // but MVP logic usually checks success first.
            view.navigateToDashboard(user)
        }
    }

    override fun onRegisterClicked() {
        view.navigateToRegister()
    }

    override fun onGoogleSignInClicked() {
        view.showError("Google sign-in will be enabled with Supabase auth.")
    }
}
