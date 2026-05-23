package com.example.biomemo.screens.splash

import com.example.biomemo.features.auth.domain.AuthUseCases
import com.example.biomemo.features.auth.domain.ProfileResult
import com.example.biomemo.features.auth.domain.ProfileUseCases
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class SplashDestination {
    LOGIN,
    USERNAME_SETUP,
    DASHBOARD
}

class SplashRouteDecider(
    private val restorePersistedSession: suspend () -> Unit = {
        AuthUseCases().restorePersistedSession()
    },
    private val hasActiveSession: () -> Boolean = { AuthUseCases().hasActiveSession() },
    private val needsUsernameSetup: suspend () -> Boolean = {
        when (val result = ProfileUseCases().loadCurrentProfile()) {
            is ProfileResult.Success -> result.profile.username.isNullOrBlank()
            is ProfileResult.Failure -> result.message == PROFILE_NOT_FOUND
        }
    },
    private val restoreTimeoutMs: Long = DEFAULT_RESTORE_TIMEOUT_MS
) {
    suspend fun decideDestination(): SplashDestination {
        val restoreResult = CompletableDeferred<Boolean>()
        val restoreJob = CoroutineScope(Dispatchers.IO).launch {
            restoreResult.complete(runCatching { restorePersistedSession() }.isSuccess)
        }
        val restored = withTimeoutOrNull(restoreTimeoutMs) { restoreResult.await() } ?: false
        if (!restored) restoreJob.cancel()
        if (!restored || !hasActiveSession()) return SplashDestination.LOGIN
        return if (needsUsernameSetup()) SplashDestination.USERNAME_SETUP else SplashDestination.DASHBOARD
    }

    private companion object {
        const val DEFAULT_RESTORE_TIMEOUT_MS = 1_500L
        const val PROFILE_NOT_FOUND = "Profile not found"
    }
}
