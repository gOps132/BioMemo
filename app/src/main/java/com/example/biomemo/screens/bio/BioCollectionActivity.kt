package com.example.biomemo.screens.bio

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.biomemo.R
import com.example.biomemo.navigation.MainBottomNav
import com.example.biomemo.navigation.MainNavDestination
import com.example.biomemo.data.BioEntry
import com.example.biomemo.data.BioRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BioCollectionActivity : AppCompatActivity() {
    private val repository = BioRepository()
    private val bioScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bio_collection)

        MainBottomNav.setup(this, MainNavDestination.RECORDS, intent.getStringExtra(MainBottomNav.EXTRA_USERNAME))

        bioScope.launch {
            renderEntries(repository.getAllEntries())
        }
    }

    private fun renderEntries(entries: List<BioEntry>) {
        val container = findViewById<LinearLayout>(R.id.linearlayoutBioEntries)
        container.removeAllViews()
        if (entries.isEmpty()) {
            container.addView(text("No BioRecords yet. Add your first observation from the capture tab.", 15, R.color.bio_ink_muted, false))
            return
        }
        entries.forEach { entry -> container.addView(createEntryCard(entry)) }
    }

    private fun createEntryCard(entry: BioEntry): LinearLayout {
        val card = LinearLayout(this).apply {
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
        }

        card.addView(thumbnail(entry))
        card.addView(LinearLayout(this@BioCollectionActivity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
            addView(text(entry.commonName, 18, R.color.bio_ink, true))
            addView(text(entry.scientificName, 13, R.color.bio_ink_muted, false))
            addView(text("${entry.category} · ${entry.confidence}% match", 13, R.color.bio_forest_600, true))
            addView(text("${entry.date} · ${entry.location}", 13, R.color.bio_ink_muted, false))
            addView(text(entry.notes, 14, R.color.bio_ink, false))
        })
        return card
    }

    private fun openBioRecord(entry: BioEntry) {
        startActivity(
            Intent(this, BioRecordDetailActivity::class.java)
                .putExtra(BioRecordDetailActivity.EXTRA_ENTRY_ID, entry.id)
        )
    }

    private fun thumbnail(entry: BioEntry): ImageView = ImageView(this).apply {
        contentDescription = "${entry.commonName} thumbnail"
        setBackgroundResource(R.drawable.bg_bio_thumbnail)
        setImageResource(R.drawable.ic_bio_record_photo)
        setPadding(dp(14), dp(14), dp(14), dp(14))
        layoutParams = LinearLayout.LayoutParams(dp(74), dp(74)).apply {
            rightMargin = dp(14)
        }
    }

    private fun text(value: String, sizeSp: Int, colorRes: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = sizeSp.toFloat()
        setTextColor(getColor(colorRes))
        if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(0, dp(2), 0, dp(2))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        bioScope.cancel()
        super.onDestroy()
    }
}
