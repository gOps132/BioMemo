package com.example.biomemo.screens.profile

import com.example.biomemo.features.auth.domain.ExplorerProfile

interface ProfileContract {
    interface View {
        fun showProfile(profile: ExplorerProfile)
        fun logout()
        fun showError(message: String)
    }

    interface Presenter {
        suspend fun onProfileOpened()
        suspend fun onLogoutClicked()
    }
}
