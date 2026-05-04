package com.example.biomemo.screens.dashboard

interface DashboardContract {
    interface View {
        fun displayWelcome(username: String)
    }

    interface Presenter {
        fun start(username: String)
    }
}
