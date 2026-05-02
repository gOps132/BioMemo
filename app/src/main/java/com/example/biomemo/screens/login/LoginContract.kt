package com.example.biomemo.screens.login

interface LoginContract {
    interface View {
        fun showLoginSuccess(username: String)
        fun showError(message: String)
        fun navigateToRegister()
        fun navigateToDashboard(username: String)
    }

    interface Presenter {
        fun onLoginClicked(user: String, pass: String)
        fun onRegisterClicked()
        fun onGoogleSignInClicked()
    }
}
