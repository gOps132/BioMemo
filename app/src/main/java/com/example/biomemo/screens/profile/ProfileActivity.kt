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

        presenter = ProfilePresenter(this, ProfileModel())

        val username = intent.getStringExtra(MainBottomNav.EXTRA_USERNAME)
        presenter.start(username)
        MainBottomNav.setup(this, MainNavDestination.PROFILE, username)

        findViewById<TextView>(R.id.textviewProfileLogout).setOnClickListener {
            presenter.onLogoutClicked()
        }
    }

    override fun displayUsername(formattedName: String) {
        findViewById<TextView>(R.id.textviewUsername).text = formattedName
    }

    override fun logout() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
