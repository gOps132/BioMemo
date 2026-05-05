package com.example.biomemo.screens.bio

import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.biomemo.R
import com.example.biomemo.data.BioEntry
import com.example.biomemo.data.BioRepository

class BioRecordDetailActivity : AppCompatActivity() {
    private val repository = BioRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bio_record_detail)

        findViewById<TextView>(R.id.textviewBioRecordBack).setOnClickListener { finish() }

        val entryId = intent.getStringExtra(EXTRA_ENTRY_ID).orEmpty()
        val entry = repository.getEntryById(entryId)
        if (entry == null) {
            Toast.makeText(this, "BioRecord not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        renderEntry(entry)
    }

    private fun renderEntry(entry: BioEntry) {
        findViewById<ImageView>(R.id.imageviewBioRecordHero).contentDescription = "${entry.commonName} photo"
        findViewById<TextView>(R.id.textviewBioRecordCommonName).text = entry.commonName
        findViewById<TextView>(R.id.textviewBioRecordScientificName).text = entry.scientificName
        findViewById<TextView>(R.id.textviewBioRecordHeroMeta).text =
            "${entry.category} · ${entry.confidence}% match · ${entry.verificationStatus}"
        findViewById<TextView>(R.id.textviewBioRecordNotes).text = entry.notes

        renderRows(
            R.id.linearlayoutObservationDetails,
            "Observation details",
            listOf(
                "User ID" to entry.userId,
                "Photo source" to entry.photoUrl,
                "Source type" to entry.sourceType,
                "Observed date" to entry.observedDate,
                "Saved date" to entry.savedDate,
                "Location label" to entry.location,
                "Coordinates" to coordinates(entry),
                "Metadata" to entry.metadataAvailability,
                "Verification" to entry.verificationStatus
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
            )
        )

        renderTags(entry.tags)
    }

    private fun renderRows(containerId: Int, title: String, rows: List<Pair<String, String>>) {
        val container = findViewById<LinearLayout>(containerId)
        container.removeAllViews()
        container.addView(text(title, 18, R.color.bio_ink, true))
        rows.forEach { (label, value) ->
            container.addView(text(label.uppercase(), 11, R.color.bio_ink_muted, true).apply {
                setPadding(0, dp(12), 0, 0)
            })
            container.addView(text(value, 15, R.color.bio_ink, false))
        }
    }

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

    companion object {
        const val EXTRA_ENTRY_ID = "bio_record_id"
    }
}
