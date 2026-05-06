package com.example.biomemo.screens.profile

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.biomemo.R
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
    private val authScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        presenter = ProfilePresenter(this)

        val username = intent.getStringExtra(MainBottomNav.EXTRA_USERNAME)
        MainBottomNav.setup(this, MainNavDestination.PROFILE, username)

        findViewById<TextView>(R.id.textviewProfileLogout).setOnClickListener {
            authScope.launch { presenter.onLogoutClicked() }
        }
    }

    override fun logout() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    override fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        authScope.cancel()
        super.onDestroy()
    }
}
