package com.example.biomemo.screens.profile

import com.example.biomemo.features.auth.domain.AuthUseCases
import com.example.biomemo.features.auth.domain.ProfileResult
import com.example.biomemo.features.auth.domain.ProfileUseCases
import com.example.biomemo.features.auth.domain.SupabaseAuthResult

interface ProfileAuthModel {
    suspend fun loadProfile(): ProfileResult
    suspend fun signOut(): SupabaseAuthResult
}

class ProfileModel(
    private val authUseCases: AuthUseCases = AuthUseCases(),
    private val profileUseCases: ProfileUseCases = ProfileUseCases()
) : ProfileAuthModel {
    override suspend fun loadProfile(): ProfileResult = profileUseCases.loadCurrentProfile()

    override suspend fun signOut(): SupabaseAuthResult = authUseCases.signOut()
}

class ProfilePresenter(
    private val view: ProfileContract.View,
    private val model: ProfileAuthModel = ProfileModel()
) : ProfileContract.Presenter {

    override suspend fun onProfileOpened() {
        when (val result = model.loadProfile()) {
            is ProfileResult.Success -> view.showProfile(result.profile)
            is ProfileResult.Failure -> view.showError(result.message)
        }
    }

    override suspend fun onLogoutClicked() {
        when (val result = model.signOut()) {
            is SupabaseAuthResult.Success -> view.logout()
            is SupabaseAuthResult.Failure -> view.showError(result.message)
        }
    }
}
