package com.example.biomemo.screens.register

interface RegisterContract {
    interface View {
        fun showSuccess(message: String)
        fun showError(message: String)
        fun navigateToLogin()
    }

    interface Presenter {
        suspend fun onRegisterClicked(email: String, username: String, pass: String, rePass: String)
        suspend fun onGoogleSignInClicked()
    }
}
