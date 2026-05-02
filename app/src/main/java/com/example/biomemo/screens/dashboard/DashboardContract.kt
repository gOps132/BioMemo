package com.example.biomemo.screens.dashboard

interface DashboardContract {
    interface View {
        fun displayWelcome(username: String)
        fun navigateToProfile(username: String)
        fun logout()
    }
    interface Presenter {
        fun start(username: String)
        fun onProfileClicked(username: String)
        fun onLogoutClicked()
    }
}