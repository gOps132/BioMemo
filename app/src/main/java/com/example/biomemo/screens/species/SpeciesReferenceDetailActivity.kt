package com.example.biomemo.screens.species

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.biomemo.R
import com.example.biomemo.data.SpeciesSearchResult
import com.example.biomemo.data.SpeciesSourceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class SpeciesReferenceDetailActivity : AppCompatActivity() {
    private val speciesRepository = SpeciesSourceRepository()
    private val speciesScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_species_reference_detail)

        findViewById<TextView>(R.id.textviewSpeciesReferenceBack).setOnClickListener { finish() }
        val species = readSpeciesFromIntent()
        renderDetail(species.toSpeciesReferenceDetail(), isEnriching = true)
        loadEnrichment(species)
    }

    private fun loadEnrichment(species: SpeciesSearchResult) {
        speciesScope.launch {
            val enrichment = runCatching { speciesRepository.previewEnrichment(species) }.getOrNull()
            if (enrichment != null) {
                renderDetail(species.toSpeciesReferenceDetail(enrichment))
            } else {
                renderDetail(species.toSpeciesReferenceDetail())
            }
        }
    }

    private fun readSpeciesFromIntent(): SpeciesSearchResult {
        return SpeciesSearchResult(
            gbifUsageKey = intent.getIntExtra(EXTRA_GBIF_USAGE_KEY, 0),
            scientificName = intent.getStringExtra(EXTRA_SCIENTIFIC_NAME).orEmpty(),
            canonicalName = intent.getStringExtra(EXTRA_CANONICAL_NAME).orEmpty(),
            commonName = intent.getStringExtra(EXTRA_COMMON_NAME),
            rank = intent.getStringExtra(EXTRA_RANK).orEmpty(),
            taxonomicStatus = intent.getStringExtra(EXTRA_TAXONOMIC_STATUS).orEmpty(),
            kingdom = intent.getStringExtra(EXTRA_KINGDOM),
            phylum = intent.getStringExtra(EXTRA_PHYLUM),
            className = intent.getStringExtra(EXTRA_CLASS_NAME),
            order = intent.getStringExtra(EXTRA_ORDER),
            family = intent.getStringExtra(EXTRA_FAMILY),
            genus = intent.getStringExtra(EXTRA_GENUS)
        )
    }

    private fun renderDetail(detail: SpeciesReferenceDetail, isEnriching: Boolean = false) {
        findViewById<TextView>(R.id.textviewSpeciesReferenceTitle).text = detail.title
        findViewById<TextView>(R.id.textviewSpeciesReferenceSubtitle).text = detail.subtitle
        findViewById<TextView>(R.id.textviewSpeciesReferenceHeroMeta).text = "Public species reference · read-only"
        renderPhoto(detail)

        val container = findViewById<LinearLayout>(R.id.linearlayoutSpeciesReferenceRows)
        container.removeAllViews()
        detail.rows.forEach { (label, value) ->
            container.addView(text(label.uppercase(), 11, R.color.bio_ink_muted, true).apply {
                setPadding(0, dp(12), 0, 0)
            })
            if (isEnriching && label in ENRICHMENT_LOADING_LABELS && value == NOT_ENRICHED) {
                container.addView(loadingValue())
            } else {
                container.addView(text(value, 15, R.color.bio_ink, false))
            }
        }
    }

    private fun loadingValue(): ProgressBar {
        return ProgressBar(this, null, android.R.attr.progressBarStyleSmall).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            contentDescription = "Loading enrichment"
        }
    }

    private fun renderPhoto(detail: SpeciesReferenceDetail) {
        val imageView = findViewById<ImageView>(R.id.imageviewSpeciesReferencePhoto)
        val creditView = findViewById<TextView>(R.id.textviewSpeciesReferencePhotoCredit)
        val photoUrl = detail.photoUrl

        if (photoUrl.isNullOrBlank()) {
            imageView.visibility = View.GONE
            creditView.visibility = View.GONE
            return
        }

        creditView.text = detail.photoCredit.orEmpty()
        creditView.visibility = if (detail.photoCredit.isNullOrBlank()) View.GONE else View.VISIBLE
        imageView.visibility = View.VISIBLE
        speciesScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { URL(photoUrl).openStream().use(BitmapFactory::decodeStream) }.getOrNull()
            }
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
            }
        }
    }

    private fun text(value: String, sizeSp: Int, colorRes: Int, bold: Boolean): TextView {
        return TextView(this).apply {
            text = value
            textSize = sizeSp.toFloat()
            setTextColor(getColor(colorRes))
            if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(2), 0, dp(2))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        speciesScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_GBIF_USAGE_KEY = "gbif_usage_key"
        const val EXTRA_SCIENTIFIC_NAME = "scientific_name"
        const val EXTRA_CANONICAL_NAME = "canonical_name"
        const val EXTRA_COMMON_NAME = "common_name"
        const val EXTRA_RANK = "rank"
        const val EXTRA_TAXONOMIC_STATUS = "taxonomic_status"
        const val EXTRA_KINGDOM = "kingdom"
        const val EXTRA_PHYLUM = "phylum"
        const val EXTRA_CLASS_NAME = "class_name"
        const val EXTRA_ORDER = "order"
        const val EXTRA_FAMILY = "family"
        const val EXTRA_GENUS = "genus"
        private const val NOT_ENRICHED = "Not enriched yet"
        private val ENRICHMENT_LOADING_LABELS = setOf(
            "Habitat",
            "Diet",
            "Lifespan",
            "Distribution",
            "Conservation status",
            "Photo source",
            "Last enriched"
        )
    }
}
