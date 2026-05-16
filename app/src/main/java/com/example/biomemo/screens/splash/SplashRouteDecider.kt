package com.example.biomemo.screens.splash

import com.example.biomemo.data.remote.SupabaseAuthRepository
import kotlinx.coroutines.withTimeoutOrNull

enum class SplashDestination {
    LOGIN,
    DASHBOARD
}

class SplashRouteDecider(
    private val restorePersistedSession: suspend () -> Unit = {
        SupabaseAuthRepository().restorePersistedSession()
    },
    private val hasActiveSession: () -> Boolean = { SupabaseAuthRepository().hasActiveSession() },
    private val restoreTimeoutMs: Long = DEFAULT_RESTORE_TIMEOUT_MS
) {
    suspend fun decideDestination(): SplashDestination {
        val restored = withTimeoutOrNull(restoreTimeoutMs) {
            runCatching { restorePersistedSession() }.isSuccess
        } ?: false
        return if (restored && hasActiveSession()) SplashDestination.DASHBOARD else SplashDestination.LOGIN
    }

    private companion object {
        const val DEFAULT_RESTORE_TIMEOUT_MS = 1_500L
    }
}
