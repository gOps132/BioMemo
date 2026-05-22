package com.example.biomemo.screens.search

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.biomemo.R
import com.example.biomemo.navigation.MainBottomNav
import com.example.biomemo.navigation.MainNavDestination
import com.example.biomemo.data.BioEntry
import com.example.biomemo.data.BioRepository
import com.example.biomemo.data.SpeciesSearchResult
import com.example.biomemo.data.SpeciesSourceRepository
import com.example.biomemo.screens.bio.BioRecordDetailActivity
import com.example.biomemo.screens.species.SpeciesReferenceDetailActivity
import com.example.biomemo.ui.BioImageLoader
import com.example.biomemo.ui.roundedImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
    private lateinit var searchAdapter: SearchResultsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        MainBottomNav.setup(this, MainNavDestination.SEARCH, intent.getStringExtra(MainBottomNav.EXTRA_USERNAME))

        searchAdapter = SearchResultsAdapter()
        findViewById<RecyclerView>(R.id.recyclerviewSearchResults).apply {
            layoutManager = LinearLayoutManager(this@SearchActivity)
            adapter = searchAdapter
        }

        searchField = findViewById(R.id.edittextSearch)
        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                runSearch(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        observeBioRecords()
        runSearch("")
    }

    private fun runSearch(query: String) {
        searchJob?.cancel()
        searchJob = searchScope.launch {
            renderState(presenter.search(query))
        }
    }

    private fun observeBioRecords() {
        searchScope.launch {
            bioRepository.observeAllEntries().collectLatest {
                if (::searchField.isInitialized) {
                    runSearch(searchField.text?.toString().orEmpty())
                }
            }
        }
    }

    private fun renderState(state: SearchUiState) {
        val countLabel = findViewById<TextView>(R.id.textviewSearchCount)
        countLabel.text = if (state.isSpeciesSearchAvailable) {
            "${state.bioRecords.size} BioRecords · ${state.speciesResults.size} species"
        } else {
            "${state.bioRecords.size} BioRecords"
        }

        val items = mutableListOf<SearchResultItem>()
        if (state.query.isEmpty() && state.suggestions.isNotEmpty()) {
            items += SearchResultItem.Section("Suggestions")
            items += SearchResultItem.Suggestions(state.suggestions)
        }

        if (state.bioRecords.isNotEmpty()) {
            items += SearchResultItem.Section("BioRecords")
            items += state.bioRecords.map(SearchResultItem::BioRecord)
        }

        if (state.isSpeciesSearchAvailable) {
            items += SearchResultItem.Section("Species reference")
            state.speciesError?.let { error ->
                items += SearchResultItem.Message(error, R.color.bio_danger)
            }
            items += state.speciesResults.map(SearchResultItem::Species)
            if (state.speciesResults.isEmpty() && state.speciesError == null) {
                items += SearchResultItem.Message("No public species matches yet.", R.color.bio_ink_muted)
            }
        }

        if (state.bioRecords.isEmpty() && !state.isSpeciesSearchAvailable) {
            items += SearchResultItem.Message("No BioRecords found. Try a species, tag, or location.", R.color.bio_ink_muted)
        } else if (state.bioRecords.isEmpty() && state.speciesResults.isEmpty() && state.speciesError == null) {
            items += SearchResultItem.Message("No BioRecords or public species matches found.", R.color.bio_ink_muted)
        }
        searchAdapter.submitItems(items)
    }

    private fun createBioRecordViewHolder(): SearchResultViewHolder.BioRecordRow {
        val card = resultCard(clickable = true)
        val thumbnail = thumbnail().also(card::addView)
        val commonName = text("", 17, R.color.bio_ink, true)
        val meta = text("", 13, R.color.bio_ink_muted, false)
        val tags = text("", 13, R.color.bio_forest_600, true)
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
            addView(commonName)
            addView(meta)
            addView(tags)
        })
        return SearchResultViewHolder.BioRecordRow(card, thumbnail, commonName, meta, tags)
    }

    private fun bindBioRecordRow(holder: SearchResultViewHolder.BioRecordRow, entry: BioEntry) {
        holder.card.setOnClickListener { openBioRecord(entry) }
        holder.thumbnail.contentDescription = "${entry.commonName} thumbnail"
        holder.thumbnail.tag = entry.photoUrl
        holder.thumbnail.setImageResource(R.drawable.ic_bio_record_photo)
        holder.thumbnail.setPadding(0, 0, 0, 0)
        holder.thumbnail.scaleType = ImageView.ScaleType.CENTER_INSIDE
        loadThumbnail(entry, holder.thumbnail)
        holder.commonName.text = entry.commonName
        holder.meta.text = "${entry.scientificName} · ${entry.category}"
        holder.tags.text = entry.tags.joinToString(" · ")
    }

    private fun createSpeciesViewHolder(): SearchResultViewHolder.SpeciesRow {
        val card = resultCard(clickable = true)
        val title = text("", 17, R.color.bio_ink, true)
        val scientificName = text("", 13, R.color.bio_ink_muted, false)
        val taxonomy = text("", 13, R.color.bio_forest_600, true)
        val source = text("", 12, R.color.bio_ink_muted, false)
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(title)
            addView(scientificName)
            addView(taxonomy)
            addView(source)
        })
        return SearchResultViewHolder.SpeciesRow(card, title, scientificName, taxonomy, source)
    }

    private fun bindSpeciesRow(holder: SearchResultViewHolder.SpeciesRow, species: SpeciesSearchResult) {
        holder.card.setOnClickListener { openSpeciesReference(species) }
        holder.title.text = species.commonName ?: species.canonicalName
        holder.scientificName.text = species.scientificName
        holder.taxonomy.text = species.taxonomyLine()
        holder.source.text = "${species.sourceName} · ${species.taxonomicStatus.displayStatus()}"
    }

    private fun suggestionRow(suggestions: List<String>): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = RecyclerView.LayoutParams(
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
        layoutParams = RecyclerView.LayoutParams(
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
            val bitmap = BioImageLoader.loadBitmap(
                photoRef = entry.photoUrl,
                targetWidthPx = dp(136),
                targetHeightPx = dp(136),
                signedUrlResolver = { path -> bioRepository.createSignedPhotoUrl(path) }
            )
            if (bitmap != null && imageView.tag == entry.photoUrl) {
                imageView.setPadding(0, 0, 0, 0)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                imageView.setImageBitmap(bitmap)
            }
        }
    }

    private fun thumbnail(): ImageView = roundedImageView(this, dp(10).toFloat()).apply {
        setImageResource(R.drawable.ic_bio_record_photo)
        setPadding(0, 0, 0, 0)
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

    private inner class SearchResultsAdapter : RecyclerView.Adapter<SearchResultViewHolder>() {
        private var items: List<SearchResultItem> = emptyList()

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is SearchResultItem.Section -> VIEW_TYPE_SECTION
                is SearchResultItem.Suggestions -> VIEW_TYPE_SUGGESTIONS
                is SearchResultItem.BioRecord -> VIEW_TYPE_BIO_RECORD
                is SearchResultItem.Species -> VIEW_TYPE_SPECIES
                is SearchResultItem.Message -> VIEW_TYPE_MESSAGE
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
            return when (viewType) {
                VIEW_TYPE_SECTION -> SearchResultViewHolder.Section(sectionLabel(""))
                VIEW_TYPE_SUGGESTIONS -> SearchResultViewHolder.Suggestions(suggestionRow(emptyList()))
                VIEW_TYPE_BIO_RECORD -> createBioRecordViewHolder()
                VIEW_TYPE_SPECIES -> createSpeciesViewHolder()
                else -> SearchResultViewHolder.Message(text("", 15, R.color.bio_ink_muted, false))
            }
        }

        override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
            when (val item = items[position]) {
                is SearchResultItem.Section -> (holder as SearchResultViewHolder.Section).label.text = item.value
                is SearchResultItem.Suggestions -> bindSuggestionRow(holder as SearchResultViewHolder.Suggestions, item.values)
                is SearchResultItem.BioRecord -> bindBioRecordRow(holder as SearchResultViewHolder.BioRecordRow, item.entry)
                is SearchResultItem.Species -> bindSpeciesRow(holder as SearchResultViewHolder.SpeciesRow, item.species)
                is SearchResultItem.Message -> bindMessage(holder as SearchResultViewHolder.Message, item)
            }
        }

        override fun getItemCount(): Int = items.size

        fun submitItems(nextItems: List<SearchResultItem>) {
            items = nextItems
            notifyDataSetChanged()
        }
    }

    private fun bindSuggestionRow(holder: SearchResultViewHolder.Suggestions, suggestions: List<String>) {
        holder.row.removeAllViews()
        suggestions.take(4).forEach { suggestion ->
            holder.row.addView(text(suggestion, 13, R.color.bio_forest_700, true).apply {
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

    private fun bindMessage(holder: SearchResultViewHolder.Message, item: SearchResultItem.Message) {
        holder.message.text = item.value
        holder.message.setTextColor(getColor(item.colorRes))
    }

    private sealed class SearchResultItem {
        data class Section(val value: String) : SearchResultItem()
        data class Suggestions(val values: List<String>) : SearchResultItem()
        data class BioRecord(val entry: BioEntry) : SearchResultItem()
        data class Species(val species: SpeciesSearchResult) : SearchResultItem()
        data class Message(val value: String, val colorRes: Int) : SearchResultItem()
    }

    private sealed class SearchResultViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        class Section(val label: TextView) : SearchResultViewHolder(label)
        class Suggestions(val row: LinearLayout) : SearchResultViewHolder(row)
        class Message(val message: TextView) : SearchResultViewHolder(message)
        class BioRecordRow(
            val card: LinearLayout,
            val thumbnail: ImageView,
            val commonName: TextView,
            val meta: TextView,
            val tags: TextView
        ) : SearchResultViewHolder(card)
        class SpeciesRow(
            val card: LinearLayout,
            val title: TextView,
            val scientificName: TextView,
            val taxonomy: TextView,
            val source: TextView
        ) : SearchResultViewHolder(card)
    }

    private companion object {
        const val VIEW_TYPE_SECTION = 1
        const val VIEW_TYPE_SUGGESTIONS = 2
        const val VIEW_TYPE_BIO_RECORD = 3
        const val VIEW_TYPE_SPECIES = 4
        const val VIEW_TYPE_MESSAGE = 5
    }
}
