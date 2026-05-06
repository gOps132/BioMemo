package com.example.biomemo.screens.profile

import com.example.biomemo.data.remote.SupabaseAuthRepository
import com.example.biomemo.data.remote.SupabaseAuthResult

interface ProfileAuthModel {
    suspend fun signOut(): SupabaseAuthResult
}

class ProfileModel(
    private val authRepository: SupabaseAuthRepository = SupabaseAuthRepository()
) : ProfileAuthModel {
    override suspend fun signOut(): SupabaseAuthResult = authRepository.signOut()
}

class ProfilePresenter(
    private val view: ProfileContract.View,
    private val model: ProfileAuthModel = ProfileModel()
) : ProfileContract.Presenter {

    override suspend fun onLogoutClicked() {
        when (val result = model.signOut()) {
            is SupabaseAuthResult.Success -> view.logout()
            is SupabaseAuthResult.Failure -> view.showError(result.message)
        }
    }
}
