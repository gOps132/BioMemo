package com.example.biomemo.screens.profile

class ProfilePresenter(
    private val view: ProfileContract.View
) : ProfileContract.Presenter {

    override fun onLogoutClicked() {
        view.logout()
    }
}
