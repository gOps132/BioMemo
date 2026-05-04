package com.example.biomemo.screens.profile

interface ProfileContract {
    interface View {
        fun displayUsername(formattedName: String)
        fun logout()
    }

    interface Presenter {
        fun start(username: String?)
        fun onLogoutClicked()
    }
}
