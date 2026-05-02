package com.example.biomemo.screens.register

class RegisterPresenter(
    private val view: RegisterContract.View,
    private val model: RegisterModel
) : RegisterContract.Presenter {

    override fun onRegisterClicked(user: String, pass: String, rePass: String) {
        // Validation Logic
        if (user.isEmpty() || pass.isEmpty() || rePass.isEmpty()) {
            view.showError("Please complete all fields")
            return
        }

        if (pass != rePass) {
            view.showError("Passwords do not match")
            return
        }

        // Logic to save user
        val success = model.registerUser(user, pass)
        if (success) {
            view.showSuccess("Registration successful")
            view.navigateToLogin()
        } else {
            view.showError("User already exists")
        }
    }
}