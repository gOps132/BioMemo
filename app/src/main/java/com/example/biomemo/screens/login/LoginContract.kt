package com.example.biomemo.screens.login

interface LoginContract {
    interface View {
        fun showLoginSuccess(username: String)
        fun showGoogleAuthStarted()
        fun showError(message: String)
        fun navigateToRegister()
        fun navigateToDashboard(username: String)
    }

    interface Presenter {
        suspend fun onLoginClicked(user: String, pass: String)
        fun onRegisterClicked()
        suspend fun onGoogleSignInClicked()
    }
}
