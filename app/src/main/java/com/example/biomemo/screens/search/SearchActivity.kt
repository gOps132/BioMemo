package com.example.biomemo.screens.search

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
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
import com.example.biomemo.screens.species.SpeciesReferenceDetailActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class SearchActivity : AppCompatActivity() {
    private val bioRepository = BioRepository()
    private val speciesRepository = SpeciesSourceRepository()
    private val presenter = SearchPresenter(
        loadBioRecords = { bioRepository.getAllEntries() },
        searchBioRecords = { query -> bioRepository.search(query) },
        searchSpecies = { query -> speciesRepository.searchSpecies(query) },
        loadSuggestions = { bioRepository.getSearchSuggestions() }
    )
    private val searchScope = CoroutineScope(Dispatchers.Main + Job())
    private var searchJob: Job? = null
    private lateinit var searchField: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        MainBottomNav.setup(this, MainNavDestination.SEARCH, intent.getStringExtra(MainBottomNav.EXTRA_USERNAME))

        searchField = findViewById(R.id.edittextSearch)
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

        if (state.query.isEmpty() && state.suggestions.isNotEmpty()) {
            container.addView(sectionLabel("Suggestions"))
            container.addView(suggestionRow(state.suggestions))
        }

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
        val thumbnail = thumbnail(entry)
        card.addView(thumbnail)
        loadThumbnail(entry, thumbnail)
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
        val card = resultCard(clickable = true).apply {
            setOnClickListener { openSpeciesReference(species) }
        }
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(text(species.commonName ?: species.canonicalName, 17, R.color.bio_ink, true))
            addView(text(species.scientificName, 13, R.color.bio_ink_muted, false))
            addView(text(species.taxonomyLine(), 13, R.color.bio_forest_600, true))
            addView(text("${species.sourceName} · ${species.taxonomicStatus.displayStatus()}", 12, R.color.bio_ink_muted, false))
        })
        return card
    }

    private fun suggestionRow(suggestions: List<String>): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
            suggestions.take(4).forEach { suggestion ->
                addView(text(suggestion, 13, R.color.bio_forest_700, true).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setBackgroundResource(R.drawable.bg_chip)
                    setPadding(dp(12), dp(7), dp(12), dp(7))
                    setOnClickListener { searchField.setText(suggestion) }
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply { rightMargin = dp(8) }
                })
            }
        }
    }

    private fun openSpeciesReference(species: SpeciesSearchResult) {
        startActivity(
            Intent(this, SpeciesReferenceDetailActivity::class.java)
                .putExtra(SpeciesReferenceDetailActivity.EXTRA_GBIF_USAGE_KEY, species.gbifUsageKey)
                .putExtra(SpeciesReferenceDetailActivity.EXTRA_SCIENTIFIC_NAME, species.scientificName)
                .putExtra(SpeciesReferenceDetailActivity.EXTRA_CANONICAL_NAME, species.canonicalName)
                .putExtra(SpeciesReferenceDetailActivity.EXTRA_COMMON_NAME, species.commonName)
                .putExtra(SpeciesReferenceDetailActivity.EXTRA_RANK, species.rank)
                .putExtra(SpeciesReferenceDetailActivity.EXTRA_TAXONOMIC_STATUS, species.taxonomicStatus)
                .putExtra(SpeciesReferenceDetailActivity.EXTRA_KINGDOM, species.kingdom)
                .putExtra(SpeciesReferenceDetailActivity.EXTRA_PHYLUM, species.phylum)
                .putExtra(SpeciesReferenceDetailActivity.EXTRA_CLASS_NAME, species.className)
                .putExtra(SpeciesReferenceDetailActivity.EXTRA_ORDER, species.order)
                .putExtra(SpeciesReferenceDetailActivity.EXTRA_FAMILY, species.family)
                .putExtra(SpeciesReferenceDetailActivity.EXTRA_GENUS, species.genus)
        )
    }

    private fun SpeciesSearchResult.taxonomyLine(): String {
        return listOfNotNull(family, genus, rank.lowercase().replaceFirstChar { it.uppercase() })
            .joinToString(" · ")
    }

    private fun String.displayStatus(): String {
        return lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
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

    private fun loadThumbnail(entry: BioEntry, imageView: ImageView) {
        if (entry.photoUrl.isBlank()) return
        searchScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val url = if (entry.photoUrl.startsWith("http")) {
                        entry.photoUrl
                    } else {
                        bioRepository.createSignedPhotoUrl(entry.photoUrl)
                    }
                    URL(url).openStream().use(BitmapFactory::decodeStream)
                }.getOrNull()
            }
            if (bitmap != null) {
                imageView.setPadding(0, 0, 0, 0)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                imageView.setImageBitmap(bitmap)
            }
        }
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
