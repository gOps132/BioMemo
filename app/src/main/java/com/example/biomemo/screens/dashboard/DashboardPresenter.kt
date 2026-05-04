package com.example.biomemo.screens.dashboard

class DashboardPresenter(private val view: DashboardContract.View) : DashboardContract.Presenter {
    override fun start(username: String) {
        view.displayWelcome(username)
    }
}
