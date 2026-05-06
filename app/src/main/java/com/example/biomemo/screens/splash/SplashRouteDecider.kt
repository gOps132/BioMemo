package com.example.biomemo.screens.splash

import com.example.biomemo.data.remote.SupabaseAuthRepository

enum class SplashDestination {
    LOGIN,
    DASHBOARD
}

class SplashRouteDecider(
    private val restorePersistedSession: suspend () -> Unit = {
        SupabaseAuthRepository().restorePersistedSession()
    },
    private val hasActiveSession: () -> Boolean = { SupabaseAuthRepository().hasActiveSession() }
) {
    suspend fun decideDestination(): SplashDestination {
        restorePersistedSession()
        return if (hasActiveSession()) SplashDestination.DASHBOARD else SplashDestination.LOGIN
    }
}
