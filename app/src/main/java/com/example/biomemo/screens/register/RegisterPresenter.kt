package com.example.biomemo.screens.register

import com.example.biomemo.data.remote.SupabaseAuthResult

class RegisterPresenter(
    private val view: RegisterContract.View,
    private val model: RegisterAuthModel
) : RegisterContract.Presenter {

    override suspend fun onRegisterClicked(email: String, username: String, pass: String, rePass: String) {
        if (email.isEmpty() || username.isEmpty() || pass.isEmpty() || rePass.isEmpty()) {
            view.showError("Please complete all fields")
            return
        }

        if (pass != rePass) {
            view.showError("Passwords do not match")
            return
        }

        when (val result = model.registerUser(email, pass, username)) {
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
