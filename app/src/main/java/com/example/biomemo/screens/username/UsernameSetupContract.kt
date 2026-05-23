package com.example.biomemo.screens.username

interface UsernameSetupContract {
    interface View {
        fun showSaving(isSaving: Boolean)
        fun showError(message: String)
        fun navigateToDashboard(username: String)
    }

    interface Presenter {
        suspend fun onSaveClicked(username: String)
    }
}
