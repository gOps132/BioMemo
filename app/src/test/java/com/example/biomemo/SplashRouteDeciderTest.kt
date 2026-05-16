package com.example.biomemo

import com.example.biomemo.screens.splash.SplashDestination
import com.example.biomemo.screens.splash.SplashRouteDecider
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SplashRouteDeciderTest {
    @Test
    fun routesToLoginWhenNoSessionExists() = runBlocking {
        val decider = SplashRouteDecider(
            restorePersistedSession = {},
            hasActiveSession = { false }
        )

        assertEquals(SplashDestination.LOGIN, decider.decideDestination())
    }

    @Test
    fun routesToDashboardWhenSessionExists() = runBlocking {
        val decider = SplashRouteDecider(
            restorePersistedSession = {},
            hasActiveSession = { true }
        )

        assertEquals(SplashDestination.DASHBOARD, decider.decideDestination())
    }

    @Test
    fun waitsForStoredSessionBeforeChoosingDestination() = runBlocking {
        val events = mutableListOf<String>()
        val decider = SplashRouteDecider(
            restorePersistedSession = {
                events.add("restore")
            },
            hasActiveSession = {
                events.add("check")
                true
            }
        )

        val destination = decider.decideDestination()

        assertEquals(SplashDestination.DASHBOARD, destination)
        assertEquals(listOf("restore", "check"), events)
    }

    @Test
    fun routesToLoginWhenSessionRestoreFails() = runBlocking {
        val decider = SplashRouteDecider(
            restorePersistedSession = { error("restore failed") },
            hasActiveSession = { true }
        )

        assertEquals(SplashDestination.LOGIN, decider.decideDestination())
    }

    @Test
    fun routesToLoginWhenSessionRestoreTimesOut() = runBlocking {
        val decider = SplashRouteDecider(
            restorePersistedSession = { delay(50) },
            hasActiveSession = { true },
            restoreTimeoutMs = 1
        )

        assertEquals(SplashDestination.LOGIN, decider.decideDestination())
    }
}
