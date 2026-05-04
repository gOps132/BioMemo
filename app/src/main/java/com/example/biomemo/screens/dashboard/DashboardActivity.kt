package com.example.biomemo.screens.dashboard

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.biomemo.R
import com.example.biomemo.data.BioRepository
import com.example.biomemo.screens.bio.BioCollectionActivity
import com.example.biomemo.screens.capture.CaptureActivity
import com.example.biomemo.screens.login.LoginActivity
import com.example.biomemo.screens.map.BioMapActivity
import com.example.biomemo.screens.profile.ProfileActivity
import com.example.biomemo.screens.search.SearchActivity

class DashboardActivity : AppCompatActivity(), DashboardContract.View {
    private lateinit var presenter: DashboardPresenter
    private var currentUsername: String = ""
    private val bioRepository = BioRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        presenter = DashboardPresenter(this)
        currentUsername = intent.getStringExtra("username") ?: "User"

        presenter.start(currentUsername)

        val stats = bioRepository.getStats()
        findViewById<TextView>(R.id.textviewStatSightings).text = stats.sightings.toString()
        findViewById<TextView>(R.id.textviewStatSpecies).text = stats.species.toString()
        findViewById<TextView>(R.id.textviewStatStreak).text = stats.streak

        findViewById<LinearLayout>(R.id.linearlayoutBioCard).setOnClickListener {
            startActivity(Intent(this, BioCollectionActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.linearlayoutSearchCard).setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.linearlayoutBioMapCard).setOnClickListener {
            startActivity(Intent(this, BioMapActivity::class.java))
        }

        val openCapture = {
            startActivity(Intent(this, CaptureActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.linearlayoutCaptureCard).setOnClickListener { openCapture() }
        findViewById<TextView>(R.id.textviewCaptureShortcut).setOnClickListener { openCapture() }

        findViewById<LinearLayout>(R.id.linearlayoutProfileCard).setOnClickListener {
            presenter.onProfileClicked(currentUsername)
        }

        findViewById<LinearLayout>(R.id.linearlayoutLogoutCard).setOnClickListener {
            presenter.onLogoutClicked()
        }
    }

    override fun displayWelcome(username: String) {
        findViewById<TextView>(R.id.textviewDashboardWelcome).text = "Welcome, $username"
    }

    override fun navigateToProfile(username: String) {
        val intent = Intent(this, ProfileActivity::class.java)
        intent.putExtra("username", username)
        startActivity(intent)
    }

    override fun logout() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
