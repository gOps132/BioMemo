package com.example.biomemo.screens.splash

import android.content.Intent
import android.os.Bundle
import android.app.Activity.OVERRIDE_TRANSITION_OPEN
import androidx.appcompat.app.AppCompatActivity
import com.example.biomemo.R
import com.example.biomemo.data.remote.SupabaseClientProvider
import com.example.biomemo.screens.dashboard.DashboardActivity
import com.example.biomemo.screens.login.LoginActivity
import com.example.biomemo.screens.username.UsernameSetupActivity
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {
    private val routeDecider = SplashRouteDecider()
    private val splashScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAuthCallback(intent)
        setContentView(R.layout.activity_splash)

        splashScope.launch {
            delay(SPLASH_DELAY_MS)
            navigateNext()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthCallback(intent)
    }

    override fun onDestroy() {
        splashScope.cancel()
        super.onDestroy()
    }

    private suspend fun navigateNext() {
        val nextActivity = when (routeDecider.decideDestination()) {
            SplashDestination.LOGIN -> LoginActivity::class.java
            SplashDestination.USERNAME_SETUP -> UsernameSetupActivity::class.java
            SplashDestination.DASHBOARD -> DashboardActivity::class.java
        }

        startActivity(Intent(this, nextActivity))
        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    private fun handleAuthCallback(intent: Intent) {
        if (intent.data == null) return
        SupabaseClientProvider.client.handleDeeplinks(intent)
    }

    private companion object {
        const val SPLASH_DELAY_MS = 2000L
    }
}
