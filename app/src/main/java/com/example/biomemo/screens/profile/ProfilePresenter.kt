package com.example.biomemo.screens.profile

class ProfilePresenter(
    private val view: ProfileContract.View,
    private val model: ProfileModel
) : ProfileContract.Presenter {

    override fun start(username: String?) {
        val formattedData = model.getFormattedUsername(username)
        view.displayUsername(formattedData)
    }

    override fun onBackClicked() {
        view.closeProfile()
    }
}