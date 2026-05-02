package com.example.biomemo.screens.bio

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.biomemo.R
import com.example.biomemo.data.BioEntry
import com.example.biomemo.data.BioRepository

class BioCollectionActivity : AppCompatActivity() {
    private val repository = BioRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bio_collection)

        findViewById<TextView>(R.id.textviewBioBack).setOnClickListener { finish() }
        renderEntries(repository.getAllEntries())
    }

    private fun renderEntries(entries: List<BioEntry>) {
        val container = findViewById<LinearLayout>(R.id.linearlayoutBioEntries)
        container.removeAllViews()
        entries.forEach { entry -> container.addView(createEntryCard(entry)) }
    }

    private fun createEntryCard(entry: BioEntry): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_elevated)
            setPadding(dp(18), dp(16), dp(18), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }

        card.addView(text(entry.commonName, 18, R.color.bio_ink, true))
        card.addView(text(entry.scientificName, 13, R.color.bio_ink_muted, false))
        card.addView(text("${entry.category} · ${entry.confidence}% match", 13, R.color.bio_forest_600, true))
        card.addView(text("${entry.date} · ${entry.location}", 13, R.color.bio_ink_muted, false))
        card.addView(text(entry.notes, 14, R.color.bio_ink, false))
        return card
    }

    private fun text(value: String, sizeSp: Int, colorRes: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = sizeSp.toFloat()
        setTextColor(getColor(colorRes))
        if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(0, dp(2), 0, dp(2))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
