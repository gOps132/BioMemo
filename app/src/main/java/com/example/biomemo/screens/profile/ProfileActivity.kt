package com.example.biomemo.screens.profile

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.biomemo.R
import com.example.biomemo.navigation.MainBottomNav
import com.example.biomemo.navigation.MainNavDestination
import com.example.biomemo.screens.login.LoginActivity

class ProfileActivity : AppCompatActivity(), ProfileContract.View {

    private lateinit var presenter: ProfilePresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        presenter = ProfilePresenter(this)

        val username = intent.getStringExtra(MainBottomNav.EXTRA_USERNAME)
        MainBottomNav.setup(this, MainNavDestination.PROFILE, username)

        findViewById<TextView>(R.id.textviewProfileLogout).setOnClickListener {
            presenter.onLogoutClicked()
        }
    }

    override fun logout() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
