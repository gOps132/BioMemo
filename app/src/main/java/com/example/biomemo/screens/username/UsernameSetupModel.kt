package com.example.biomemo.screens.username

import com.example.biomemo.features.auth.domain.ProfileResult
import com.example.biomemo.features.auth.domain.ProfileUseCases

interface UsernameSetupModel {
    suspend fun saveUsername(username: String): ProfileResult
}

class UsernameSetupProfileModel(
    private val profileUseCases: ProfileUseCases = ProfileUseCases()
) : UsernameSetupModel {
    override suspend fun saveUsername(username: String): ProfileResult {
        return profileUseCases.saveUsername(username)
    }
}
