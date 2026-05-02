package com.example.biomemo.screens.profile

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.biomemo.R

class ProfileActivity : AppCompatActivity(), ProfileContract.View {

    private lateinit var presenter: ProfilePresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // Initialize MVP
        presenter = ProfilePresenter(this, ProfileModel())

        val textviewUsername = findViewById<TextView>(R.id.textviewUsername)
        val textviewBackToDashboard = findViewById<TextView>(R.id.textviewBackToDashboard)

        // Get data from Intent and pass to Presenter
        val username = intent.getStringExtra("username")
        presenter.start(username)

        textviewBackToDashboard.setOnClickListener {
            presenter.onBackClicked()
        }
    }

    override fun displayUsername(formattedName: String) {
        findViewById<TextView>(R.id.textviewUsername).text = formattedName
    }

    override fun closeProfile() {
        finish()
    }
}