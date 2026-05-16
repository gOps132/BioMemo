package com.example.biomemo.screens.bio

import android.graphics.BitmapFactory
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.biomemo.R
import com.example.biomemo.data.BioEntry
import com.example.biomemo.data.BioRepository
import com.example.biomemo.data.remote.ProfileResult
import com.example.biomemo.data.remote.SupabaseProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class BioRecordDetailActivity : AppCompatActivity() {
    private val repository = BioRepository()
    private val profileRepository = SupabaseProfileRepository()
    private val bioScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bio_record_detail)

        findViewById<TextView>(R.id.textviewBioRecordBack).setOnClickListener { finish() }

        val entryId = intent.getStringExtra(EXTRA_ENTRY_ID).orEmpty()
        bioScope.launch {
            val entry = repository.getEntryById(entryId)
            if (entry == null) {
                Toast.makeText(this@BioRecordDetailActivity, "BioRecord not found", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            val username = currentUsername()
            val shouldEnrich = entry.taxonomy == NOT_ENRICHED && entry.scientificName != AWAITING_IDENTIFICATION
            renderEntry(entry, username)
            if (shouldEnrich) {
                repository.enrichBioRecordSpecies(entry.id)?.let { enrichedEntry ->
                    renderEntry(enrichedEntry, username)
                }
            }
            bioScope.launch {
                repository.observeEntryById(entryId).collectLatest { updatedEntry ->
                    renderEntry(updatedEntry, username)
                }
            }
        }
    }

    private fun renderEntry(entry: BioEntry, username: String) {
        val heroImage = findViewById<ImageView>(R.id.imageviewBioRecordHero)
        heroImage.contentDescription = "${entry.commonName} photo"
        findViewById<TextView>(R.id.textviewBioRecordCommonName).text = entry.commonName
        findViewById<TextView>(R.id.textviewBioRecordScientificName).text = entry.scientificName
        findViewById<TextView>(R.id.textviewBioRecordHeroMeta).text =
            "${entry.category} · ${entry.confidence}% match"
        findViewById<TextView>(R.id.textviewBioRecordNotes).text = entry.notes
        loadHeroPhoto(entry, heroImage)

        renderRows(
            R.id.linearlayoutObservationDetails,
            "Observation details",
            listOf(
                "Username" to username,
                "Observed date" to entry.observedDate,
                "Saved date" to entry.savedDate,
                "Location label" to entry.location,
                "Coordinates" to coordinates(entry),
                "Metadata" to entry.metadataAvailability
            )
        )

        renderRows(
            R.id.linearlayoutSpeciesDetails,
            "Species details",
            listOf(
                "Taxonomy" to entry.taxonomy,
                "Habitat" to entry.habitat,
                "Diet" to entry.diet,
                "Lifespan" to entry.lifespan,
                "Distribution" to entry.distribution,
                "Conservation status" to entry.conservationStatus,
                "Source API" to entry.sourceApi,
                "Last enriched" to entry.lastEnrichedDate
            ),
            loadingLabels = ENRICHMENT_LOADING_LABELS
        )

        renderTags(entry.tags.filterNot { it == entry.verificationStatus })
    }

    private suspend fun currentUsername(): String {
        return when (val result = profileRepository.loadCurrentProfile()) {
            is ProfileResult.Success -> result.profile.username?.takeIf { it.isNotBlank() } ?: "Unknown user"
            is ProfileResult.Failure -> "Unknown user"
        }
    }

    private fun loadHeroPhoto(entry: BioEntry, imageView: ImageView) {
        if (entry.photoUrl.isBlank()) return
        bioScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val url = if (entry.photoUrl.startsWith("http")) {
                        entry.photoUrl
                    } else {
                        repository.createSignedPhotoUrl(entry.photoUrl)
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

    private fun renderRows(
        containerId: Int,
        title: String,
        rows: List<Pair<String, String>>,
        loadingLabels: Set<String> = emptySet()
    ) {
        val container = findViewById<LinearLayout>(containerId)
        container.removeAllViews()
        container.addView(text(title, 18, R.color.bio_ink, true))
        rows.forEach { (label, value) ->
            container.addView(text(label.uppercase(), 11, R.color.bio_ink_muted, true).apply {
                setPadding(0, dp(12), 0, 0)
            })
            if (label in loadingLabels && value.isPendingEnrichment()) {
                container.addView(loadingValue())
            } else {
                container.addView(text(value, 15, R.color.bio_ink, false))
            }
        }
    }

    private fun loadingValue(): ProgressBar {
        return ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(getColor(R.color.bio_forest_700))
            layoutParams = LinearLayout.LayoutParams(dp(150), dp(8)).apply {
                topMargin = dp(8)
                bottomMargin = dp(6)
            }
            contentDescription = "Loading enrichment"
        }
    }

    private fun String.isPendingEnrichment(): Boolean = trim().equals(NOT_ENRICHED, ignoreCase = true)

    private fun renderTags(tags: List<String>) {
        val container = findViewById<LinearLayout>(R.id.linearlayoutBioRecordTags)
        container.removeAllViews()
        tags.forEach { tag ->
            container.addView(text(tag, 13, R.color.bio_forest_700, true).apply {
                setBackgroundResource(R.drawable.bg_chip)
                setPadding(dp(12), dp(7), dp(12), dp(7))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = dp(8) }
            })
        }
    }

    private fun coordinates(entry: BioEntry): String {
        val latitude = entry.latitude
        val longitude = entry.longitude
        return if (latitude == null || longitude == null) {
            "Location unknown"
        } else {
            "%.4f, %.4f".format(latitude, longitude)
        }
    }

    private fun text(value: String, sizeSp: Int, colorRes: Int, bold: Boolean): TextView {
        return TextView(this).apply {
            text = value
            textSize = sizeSp.toFloat()
            setTextColor(getColor(colorRes))
            if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(2), 0, dp(2))
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        bioScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ENTRY_ID = "bio_record_id"
        private const val NOT_ENRICHED = "Not enriched yet"
        private const val AWAITING_IDENTIFICATION = "Awaiting identification"
        private val ENRICHMENT_LOADING_LABELS = setOf(
            "Taxonomy",
            "Habitat",
            "Diet",
            "Lifespan",
            "Distribution",
            "Conservation status",
            "Last enriched"
        )
    }
}
