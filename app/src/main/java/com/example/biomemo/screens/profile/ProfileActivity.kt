package com.example.biomemo.screens.profile

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.biomemo.R
import com.example.biomemo.data.ExplorerProfile
import com.example.biomemo.navigation.MainBottomNav
import com.example.biomemo.navigation.MainNavDestination
import com.example.biomemo.screens.login.LoginActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity(), ProfileContract.View {

    private lateinit var presenter: ProfilePresenter
    private lateinit var username: TextView
    private lateinit var email: TextView
    private val authScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        presenter = ProfilePresenter(this)
        username = findViewById(R.id.textviewUsername)
        email = findViewById(R.id.textviewEmail)

        val navUsername = intent.getStringExtra(MainBottomNav.EXTRA_USERNAME)
        MainBottomNav.setup(this, MainNavDestination.PROFILE, navUsername)

        findViewById<TextView>(R.id.textviewProfileLogout).setOnClickListener {
            authScope.launch { presenter.onLogoutClicked() }
        }

        authScope.launch { presenter.onProfileOpened() }
    }

    override fun showProfile(profile: ExplorerProfile) {
        username.text = profile.username.displayOr("Not set")
        email.text = profile.email.displayOr("Not available")
    }

    override fun logout() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    override fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        authScope.cancel()
        super.onDestroy()
    }

    private fun String?.displayOr(fallback: String): String {
        return this?.trim()?.takeIf { it.isNotEmpty() } ?: fallback
    }
}
