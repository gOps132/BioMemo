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
import com.example.biomemo.data.SpeciesSearchResult
import com.example.biomemo.data.SpeciesSourceRepository
import com.example.biomemo.screens.bio.BioRecordDetailActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SearchActivity : AppCompatActivity() {
    private val bioRepository = BioRepository()
    private val speciesRepository = SpeciesSourceRepository()
    private val presenter = SearchPresenter(
        loadBioRecords = { bioRepository.getAllEntries() },
        searchBioRecords = { query -> bioRepository.search(query) },
        searchSpecies = { query -> speciesRepository.searchSpecies(query) }
    )
    private val searchScope = CoroutineScope(Dispatchers.Main + Job())
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        MainBottomNav.setup(this, MainNavDestination.SEARCH, intent.getStringExtra(MainBottomNav.EXTRA_USERNAME))

        val searchField = findViewById<EditText>(R.id.edittextSearch)
        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                runSearch(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        runSearch("")
    }

    private fun runSearch(query: String) {
        searchJob?.cancel()
        searchJob = searchScope.launch {
            renderState(presenter.search(query))
        }
    }

    private fun renderState(state: SearchUiState) {
        val countLabel = findViewById<TextView>(R.id.textviewSearchCount)
        val container = findViewById<LinearLayout>(R.id.linearlayoutSearchResults)
        countLabel.text = if (state.isSpeciesSearchAvailable) {
            "${state.bioRecords.size} BioRecords · ${state.speciesResults.size} species"
        } else {
            "${state.bioRecords.size} BioRecords"
        }
        container.removeAllViews()

        if (state.bioRecords.isNotEmpty()) {
            container.addView(sectionLabel("BioRecords"))
            state.bioRecords.forEach { entry ->
                container.addView(bioRecordCard(entry))
            }
        }

        if (state.isSpeciesSearchAvailable) {
            container.addView(sectionLabel("Species reference"))
            state.speciesError?.let { error ->
                container.addView(text(error, 15, R.color.bio_danger, false))
            }
            state.speciesResults.forEach { species ->
                container.addView(speciesCard(species))
            }
            if (state.speciesResults.isEmpty() && state.speciesError == null) {
                container.addView(text("No public species matches yet.", 15, R.color.bio_ink_muted, false))
            }
        }

        if (state.bioRecords.isEmpty() && !state.isSpeciesSearchAvailable) {
            container.addView(text("No BioRecords found. Try a species, tag, or location.", 15, R.color.bio_ink_muted, false))
        } else if (state.bioRecords.isEmpty() && state.speciesResults.isEmpty() && state.speciesError == null) {
            container.addView(text("No BioRecords or public species matches found.", 15, R.color.bio_ink_muted, false))
        }
    }

    private fun bioRecordCard(entry: BioEntry): LinearLayout {
        val card = resultCard(clickable = true).apply {
            setOnClickListener { openBioRecord(entry) }
        }
        card.addView(thumbnail(entry))
        card.addView(LinearLayout(this).apply {
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
        return card
    }

    private fun speciesCard(species: SpeciesSearchResult): LinearLayout {
        val card = resultCard(clickable = false)
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(text(species.commonName ?: species.canonicalName, 17, R.color.bio_ink, true))
            addView(text(species.scientificName, 13, R.color.bio_ink_muted, false))
            addView(text(species.taxonomyLine(), 13, R.color.bio_forest_600, true))
            addView(text("GBIF ${species.gbifUsageKey} · ${species.taxonomicStatus}", 12, R.color.bio_ink_muted, false))
        })
        return card
    }

    private fun SpeciesSearchResult.taxonomyLine(): String {
        return listOfNotNull(family, genus, rank.lowercase().replaceFirstChar { it.uppercase() })
            .joinToString(" · ")
    }

    private fun resultCard(clickable: Boolean): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundResource(R.drawable.bg_card_elevated)
        isClickable = clickable
        isFocusable = clickable
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) }
    }

    private fun sectionLabel(value: String): TextView = text(value, 13, R.color.bio_ink_muted, true).apply {
        setPadding(0, dp(12), 0, dp(6))
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

    override fun onDestroy() {
        searchScope.cancel()
        super.onDestroy()
    }
}
