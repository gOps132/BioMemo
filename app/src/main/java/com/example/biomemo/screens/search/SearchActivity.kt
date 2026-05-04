package com.example.biomemo.screens.search

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.biomemo.R
import com.example.biomemo.navigation.MainBottomNav
import com.example.biomemo.navigation.MainNavDestination
import com.example.biomemo.data.BioEntry
import com.example.biomemo.data.BioRepository

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
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_card_elevated)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
            }
            card.addView(text(entry.commonName, 17, R.color.bio_ink, true))
            card.addView(text("${entry.scientificName} · ${entry.category}", 13, R.color.bio_ink_muted, false))
            card.addView(text(entry.tags.joinToString(" · "), 13, R.color.bio_forest_600, true))
            container.addView(card)
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
