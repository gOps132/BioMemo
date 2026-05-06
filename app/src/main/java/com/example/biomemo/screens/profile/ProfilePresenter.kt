package com.example.biomemo.screens.profile

import com.example.biomemo.data.remote.ProfileResult
import com.example.biomemo.data.remote.SupabaseAuthRepository
import com.example.biomemo.data.remote.SupabaseAuthResult
import com.example.biomemo.data.remote.SupabaseProfileRepository

interface ProfileAuthModel {
    suspend fun loadProfile(): ProfileResult
    suspend fun signOut(): SupabaseAuthResult
}

class ProfileModel(
    private val authRepository: SupabaseAuthRepository = SupabaseAuthRepository(),
    private val profileRepository: SupabaseProfileRepository = SupabaseProfileRepository()
) : ProfileAuthModel {
    override suspend fun loadProfile(): ProfileResult = profileRepository.loadCurrentProfile()

    override suspend fun signOut(): SupabaseAuthResult = authRepository.signOut()
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
