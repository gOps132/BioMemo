package com.example.biomemo.screens.search

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.biomemo.R
import com.example.biomemo.navigation.MainBottomNav
import com.example.biomemo.navigation.MainNavDestination
import com.example.biomemo.data.BioEntry
import com.example.biomemo.data.BioRepository
import com.example.biomemo.screens.bio.BioRecordDetailActivity

class SearchActivity : AppCompatActivity() {
    private val repository = BioRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        MainBottomNav.setup(this, MainNavDestination.SEARCH, intent.getStringExtra(MainBottomNav.EXTRA_USERNAME))

        val searchField = findViewById<EditText>(R.id.edittextSearch)
        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderResults(repository.search(s?.toString().orEmpty()))
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        renderResults(repository.getAllEntries())
    }

    private fun renderResults(entries: List<BioEntry>) {
        val countLabel = findViewById<TextView>(R.id.textviewSearchCount)
        val container = findViewById<LinearLayout>(R.id.linearlayoutSearchResults)
        countLabel.text = "${entries.size} results"
        container.removeAllViews()

        if (entries.isEmpty()) {
            container.addView(text("No BioRecords found. Try a species, tag, or location.", 15, R.color.bio_ink_muted, false))
            return
        }

        entries.forEach { entry ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundResource(R.drawable.bg_card_elevated)
                isClickable = true
                isFocusable = true
                setPadding(dp(16), dp(14), dp(16), dp(14))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
                setOnClickListener { openBioRecord(entry) }
            }
            card.addView(thumbnail(entry))
            card.addView(LinearLayout(this@SearchActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
                addView(text(entry.commonName, 17, R.color.bio_ink, true))
                addView(text("${entry.scientificName} · ${entry.category}", 13, R.color.bio_ink_muted, false))
                addView(text(entry.tags.joinToString(" · "), 13, R.color.bio_forest_600, true))
            })
            container.addView(card)
        }
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
        setPadding(dp(13), dp(13), dp(13), dp(13))
        layoutParams = LinearLayout.LayoutParams(dp(68), dp(68)).apply {
            rightMargin = dp(12)
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
}
