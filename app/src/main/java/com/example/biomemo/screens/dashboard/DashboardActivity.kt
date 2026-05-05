package com.example.biomemo.screens.dashboard

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.biomemo.R
import com.example.biomemo.data.BioRepository
import com.example.biomemo.screens.map.BioMapActivity
import com.example.biomemo.navigation.MainBottomNav
import com.example.biomemo.navigation.MainNavDestination

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
        MainBottomNav.setup(this, MainNavDestination.HOME, currentUsername)

        val stats = bioRepository.getStats()
        findViewById<TextView>(R.id.textviewStatSightings).text = stats.sightings.toString()
        findViewById<TextView>(R.id.textviewStatSpecies).text = stats.species.toString()
        findViewById<TextView>(R.id.textviewStatStreak).text = stats.streak

        findViewById<LinearLayout>(R.id.linearlayoutBioMapCard).setOnClickListener {
            startActivity(Intent(this, BioMapActivity::class.java).putExtra(MainBottomNav.EXTRA_USERNAME, currentUsername))
        }

    }

    override fun displayWelcome(username: String) {
        findViewById<TextView>(R.id.textviewDashboardWelcome).text = "Welcome, $username"
    }

}
