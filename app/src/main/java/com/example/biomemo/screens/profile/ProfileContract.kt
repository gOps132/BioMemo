package com.example.biomemo.screens.profile

interface ProfileContract {
    interface View {
        fun displayUsername(formattedName: String)
        fun closeProfile()
    }

    interface Presenter {
        fun start(username: String?)
        fun onBackClicked()
    }
}