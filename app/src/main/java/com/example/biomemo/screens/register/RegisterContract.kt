package com.example.biomemo.screens.register

interface RegisterContract {
    interface View {
        fun showSuccess(message: String)
        fun showError(message: String)
        fun navigateToLogin()
    }

    interface Presenter {
        fun onRegisterClicked(user: String, pass: String, rePass: String)
    }
}