package com.example.biomemo.screens.splash

import com.example.biomemo.data.remote.SupabaseAuthRepository

enum class SplashDestination {
    LOGIN,
    DASHBOARD
}

class SplashRouteDecider(
    private val hasActiveSession: () -> Boolean = { SupabaseAuthRepository().hasActiveSession() }
) {
    fun decideDestination(): SplashDestination {
        return if (hasActiveSession()) SplashDestination.DASHBOARD else SplashDestination.LOGIN
    }
}
