package com.example.biomemo.screens.dashboard

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.biomemo.R
import com.example.biomemo.features.records.domain.BioEntry
import com.example.biomemo.features.records.domain.BioRecordUseCases
import com.example.biomemo.features.records.domain.BioStats
import com.example.biomemo.navigation.MainBottomNav
import com.example.biomemo.navigation.MainNavDestination
import com.example.biomemo.screens.bio.BioRecordDetailActivity
import com.example.biomemo.ui.BioImageLoader
import com.example.biomemo.ui.roundedImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity(), DashboardContract.View {
    private lateinit var presenter: DashboardPresenter
    private var currentUsername: String = ""
    private val bioRecordUseCases = BioRecordUseCases()
    private val bioScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var recentAdapter: RecentBioRecordAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        presenter = DashboardPresenter(this)
        currentUsername = intent.getStringExtra("username") ?: "User"

        presenter.start(currentUsername)
        MainBottomNav.setup(this, MainNavDestination.HOME, currentUsername)
        recentAdapter = RecentBioRecordAdapter()
        findViewById<RecyclerView>(R.id.recyclerviewRecentBioRecords).apply {
            layoutManager = LinearLayoutManager(this@DashboardActivity)
            adapter = recentAdapter
            isNestedScrollingEnabled = false
        }

        observeDashboardData()
    }

    private fun observeDashboardData() {
        bioScope.launch {
            bioRecordUseCases.observeRecords().collectLatest { entries ->
                val stats = entries.toStats()
                findViewById<TextView>(R.id.textviewStatSightings).text = stats.sightings.toString()
                findViewById<TextView>(R.id.textviewStatSpecies).text = stats.species.toString()
                findViewById<TextView>(R.id.textviewStatStreak).text = stats.streak
                renderRecentBioRecords(entries.take(3))
            }
        }
    }

    override fun displayWelcome(username: String) {
        findViewById<TextView>(R.id.textviewDashboardWelcome).text = "BioDashboard"
    }

    private fun renderRecentBioRecords(entries: List<BioEntry>) {
        if (entries.isEmpty()) {
            recentAdapter.submitItems(listOf(RecentBioRecordItem.Message("No BioRecords yet. Capture or upload a photo to start your field journal.")))
            return
        }
        recentAdapter.submitItems(entries.map(RecentBioRecordItem::Record))
    }

    private fun List<BioEntry>.toStats(): BioStats {
        return BioStats(
            sightings = size,
            species = map { it.scientificName }
                .filter { it != AWAITING_IDENTIFICATION && it != IDENTIFICATION_NOT_AVAILABLE }
                .distinct()
                .size,
            streak = if (isEmpty()) "0d" else "1d"
        )
    }

    private fun recentRecordCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(R.drawable.bg_card_elevated)
            isClickable = true
            isFocusable = true
            setPadding(dp(18), dp(16), dp(18), dp(16))
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
    }

    private fun createRecentRecordViewHolder(): RecentBioRecordViewHolder.Record {
        val card = recentRecordCard()
        val thumbnail = thumbnail().also(card::addView)
        val commonName = text("", 17, R.color.bio_ink, true)
        val scientificName = text("", 13, R.color.bio_ink_muted, false)
        val location = text("", 13, R.color.bio_forest_600, true)
        val notes = text("", 14, R.color.bio_ink_muted, false).apply {
            maxLines = 4
            ellipsize = TextUtils.TruncateAt.END
        }
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
            addView(commonName)
            addView(scientificName)
            addView(location)
            addView(notes)
        })
        return RecentBioRecordViewHolder.Record(card, thumbnail, commonName, scientificName, location, notes)
    }

    private fun bindRecentRecord(holder: RecentBioRecordViewHolder.Record, entry: BioEntry) {
        holder.card.setOnClickListener { openBioRecord(entry) }
        holder.thumbnail.contentDescription = "${entry.commonName} thumbnail"
        holder.thumbnail.tag = entry.photoUrl
        holder.thumbnail.setImageResource(R.drawable.ic_bio_record_photo)
        holder.thumbnail.setPadding(0, 0, 0, 0)
        holder.thumbnail.scaleType = ImageView.ScaleType.CENTER_INSIDE
        loadThumbnail(entry, holder.thumbnail)
        holder.commonName.text = entry.commonName
        holder.scientificName.text = entry.scientificName
        holder.location.text = "${entry.date} · ${entry.location}"
        holder.notes.text = entry.notes.previewText()
    }

    private fun openBioRecord(entry: BioEntry) {
        startActivity(
            Intent(this, BioRecordDetailActivity::class.java)
                .putExtra(BioRecordDetailActivity.EXTRA_ENTRY_ID, entry.id)
        )
    }

    private fun thumbnail(): ImageView {
        return roundedImageView(this, dp(10).toFloat()).apply {
            setImageResource(R.drawable.ic_bio_record_photo)
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(dp(74), dp(74)).apply {
                rightMargin = dp(14)
            }
        }
    }

    private fun loadThumbnail(entry: BioEntry, imageView: ImageView) {
        if (entry.photoUrl.isBlank()) return
        bioScope.launch {
            val bitmap = BioImageLoader.loadBitmap(
                photoRef = entry.photoUrl,
                targetWidthPx = dp(148),
                targetHeightPx = dp(148),
                signedUrlResolver = { path -> bioRecordUseCases.createSignedPhotoUrl(path) }
            )
            if (bitmap != null && imageView.tag == entry.photoUrl) {
                imageView.setPadding(0, 0, 0, 0)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                imageView.setImageBitmap(bitmap)
            }
        }
    }

    private fun text(value: String, sizeSp: Int, colorRes: Int, bold: Boolean): TextView {
        return TextView(this).apply {
            text = value
            textSize = sizeSp.toFloat()
            setTextColor(getColor(colorRes))
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(2), 0, dp(2))
        }
    }

    private fun String.previewText(maxLength: Int = 220): String {
        val compact = lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return if (compact.length <= maxLength) compact else compact.take(maxLength - 3).trimEnd() + "..."
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        bioScope.cancel()
        super.onDestroy()
    }

    private inner class RecentBioRecordAdapter : RecyclerView.Adapter<RecentBioRecordViewHolder>() {
        private var items: List<RecentBioRecordItem> = emptyList()

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is RecentBioRecordItem.Message -> VIEW_TYPE_MESSAGE
                is RecentBioRecordItem.Record -> VIEW_TYPE_RECORD
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentBioRecordViewHolder {
            return if (viewType == VIEW_TYPE_RECORD) {
                createRecentRecordViewHolder()
            } else {
                RecentBioRecordViewHolder.Message(text("", 15, R.color.bio_ink_muted, false))
            }
        }

        override fun onBindViewHolder(holder: RecentBioRecordViewHolder, position: Int) {
            when (val item = items[position]) {
                is RecentBioRecordItem.Message -> (holder as RecentBioRecordViewHolder.Message).message.text = item.value
                is RecentBioRecordItem.Record -> bindRecentRecord(holder as RecentBioRecordViewHolder.Record, item.entry)
            }
        }

        override fun getItemCount(): Int = items.size

        fun submitItems(nextItems: List<RecentBioRecordItem>) {
            items = nextItems
            notifyDataSetChanged()
        }
    }

    private sealed class RecentBioRecordItem {
        data class Message(val value: String) : RecentBioRecordItem()
        data class Record(val entry: BioEntry) : RecentBioRecordItem()
    }

    private sealed class RecentBioRecordViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        class Message(val message: TextView) : RecentBioRecordViewHolder(message)
        class Record(
            val card: LinearLayout,
            val thumbnail: ImageView,
            val commonName: TextView,
            val scientificName: TextView,
            val location: TextView,
            val notes: TextView
        ) : RecentBioRecordViewHolder(card)
    }

    private companion object {
        const val VIEW_TYPE_MESSAGE = 1
        const val VIEW_TYPE_RECORD = 2
        const val AWAITING_IDENTIFICATION = "Awaiting identification"
        const val IDENTIFICATION_NOT_AVAILABLE = "Not available"
    }
}
