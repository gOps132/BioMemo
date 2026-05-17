package com.example.biomemo.screens.splash

import com.example.biomemo.data.remote.SupabaseAuthRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
        val restoreResult = CompletableDeferred<Boolean>()
        val restoreJob = CoroutineScope(Dispatchers.IO).launch {
            restoreResult.complete(runCatching { restorePersistedSession() }.isSuccess)
        }
        val restored = withTimeoutOrNull(restoreTimeoutMs) { restoreResult.await() } ?: false
        if (!restored) restoreJob.cancel()
        return if (restored && hasActiveSession()) SplashDestination.DASHBOARD else SplashDestination.LOGIN
    }

    private companion object {
        const val DEFAULT_RESTORE_TIMEOUT_MS = 1_500L
    }
}
