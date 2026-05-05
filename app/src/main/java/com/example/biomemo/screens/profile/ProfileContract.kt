package com.example.biomemo.screens.profile

interface ProfileContract {
    interface View {
        fun logout()
    }

    interface Presenter {
        fun onLogoutClicked()
    }
}
