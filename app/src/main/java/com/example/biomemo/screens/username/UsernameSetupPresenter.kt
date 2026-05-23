package com.example.biomemo.screens.username

import com.example.biomemo.features.auth.domain.ProfileResult

class UsernameSetupPresenter(
    private val view: UsernameSetupContract.View,
    private val model: UsernameSetupModel
) : UsernameSetupContract.Presenter {
    override suspend fun onSaveClicked(username: String) {
        val cleanUsername = username.trim()
        if (cleanUsername.isEmpty()) {
            view.showError("Choose a username")
            return
        }

        view.showSaving(true)
        when (val result = model.saveUsername(cleanUsername)) {
            is ProfileResult.Success -> {
                view.showSaving(false)
                view.navigateToDashboard(result.profile.username ?: cleanUsername)
            }
            is ProfileResult.Failure -> {
                view.showSaving(false)
                view.showError(result.message)
            }
        }
    }
}
