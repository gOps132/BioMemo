package com.example.biomemo.screens.dashboard

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.biomemo.R
import com.example.biomemo.data.BioEntry
import com.example.biomemo.data.BioRepository
import com.example.biomemo.navigation.MainBottomNav
import com.example.biomemo.navigation.MainNavDestination
import com.example.biomemo.screens.bio.BioRecordDetailActivity

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

        renderRecentBioRecords(bioRepository.getRecentEntries(3))
    }

    override fun displayWelcome(username: String) {
        findViewById<TextView>(R.id.textviewDashboardWelcome).text = "Welcome, $username"
    }

    private fun renderRecentBioRecords(entries: List<BioEntry>) {
        val container = findViewById<LinearLayout>(R.id.linearlayoutRecentBioRecords)
        container.removeAllViews()
        entries.forEach { entry -> container.addView(createRecentRecordCard(entry)) }
    }

    private fun createRecentRecordCard(entry: BioEntry): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(R.drawable.bg_card_elevated)
            isClickable = true
            isFocusable = true
            setPadding(dp(18), dp(16), dp(18), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
            setOnClickListener { openBioRecord(entry) }

            addView(thumbnail(entry))
            addView(LinearLayout(this@DashboardActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
                addView(text(entry.commonName, 17, R.color.bio_ink, true))
                addView(text(entry.scientificName, 13, R.color.bio_ink_muted, false))
                addView(text("${entry.date} · ${entry.location}", 13, R.color.bio_forest_600, true))
                addView(text(entry.notes, 14, R.color.bio_ink_muted, false))
            })
        }
    }

    private fun openBioRecord(entry: BioEntry) {
        startActivity(
            Intent(this, BioRecordDetailActivity::class.java)
                .putExtra(BioRecordDetailActivity.EXTRA_ENTRY_ID, entry.id)
        )
    }

    private fun thumbnail(entry: BioEntry): ImageView {
        return ImageView(this).apply {
            contentDescription = "${entry.commonName} thumbnail"
            setBackgroundResource(R.drawable.bg_bio_thumbnail)
            setImageResource(R.drawable.ic_bio_record_photo)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams = LinearLayout.LayoutParams(dp(74), dp(74)).apply {
                rightMargin = dp(14)
            }
        }
    }

    private fun text(value: String, sizeSp: Int, colorRes: Int, bold: Boolean): TextView {
        return TextView(this).apply {
            text = value
            textSize = sizeSp.toFloat()
            setTextColor(getColor(colorRes))
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(2), 0, dp(2))
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
