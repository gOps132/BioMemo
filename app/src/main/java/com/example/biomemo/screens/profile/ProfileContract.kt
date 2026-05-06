package com.example.biomemo.screens.profile

interface ProfileContract {
    interface View {
        fun logout()
        fun showError(message: String)
    }

    interface Presenter {
        suspend fun onLogoutClicked()
    }
}
