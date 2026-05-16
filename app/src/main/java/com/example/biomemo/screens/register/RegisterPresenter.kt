package com.example.biomemo.screens.register

import com.example.biomemo.data.remote.SupabaseAuthResult

class RegisterPresenter(
    private val view: RegisterContract.View,
    private val model: RegisterAuthModel
) : RegisterContract.Presenter {

    override suspend fun onRegisterClicked(email: String, username: String, pass: String, rePass: String) {
        val cleanEmail = email.trim()
        val cleanUsername = username.trim()

        if (cleanEmail.isEmpty() || cleanUsername.isEmpty() || pass.isEmpty() || rePass.isEmpty()) {
            view.showError("Please fill out all fields")
            return
        }

        if (pass.length < 6) {
            view.showError("Password must be at least 6 characters.")
            return
        }

        if (pass != rePass) {
            view.showError("Passwords do not match")
            return
        }

        when (val result = model.registerUser(cleanEmail, pass, cleanUsername)) {
            is SupabaseAuthResult.Success -> {
                view.showSuccess("Registration successful")
                view.navigateToLogin()
            }
            is SupabaseAuthResult.Failure -> view.showError(result.message)
        }
    }

    override suspend fun onGoogleSignInClicked() {
        when (val result = model.continueWithGoogle()) {
            is SupabaseAuthResult.Success -> view.showSuccess("Continue in your browser to finish Google sign-in.")
            is SupabaseAuthResult.Failure -> view.showError(result.message)
        }
    }
}
