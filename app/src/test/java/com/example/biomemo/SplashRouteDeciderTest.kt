package com.example.biomemo

import com.example.biomemo.screens.splash.SplashDestination
import com.example.biomemo.screens.splash.SplashRouteDecider
import org.junit.Assert.assertEquals
import org.junit.Test

class SplashRouteDeciderTest {
    @Test
    fun routesToLoginWhenNoSessionExists() {
        val decider = SplashRouteDecider(hasActiveSession = { false })

        assertEquals(SplashDestination.LOGIN, decider.decideDestination())
    }

    @Test
    fun routesToDashboardWhenSessionExists() {
        val decider = SplashRouteDecider(hasActiveSession = { true })

        assertEquals(SplashDestination.DASHBOARD, decider.decideDestination())
    }
}
