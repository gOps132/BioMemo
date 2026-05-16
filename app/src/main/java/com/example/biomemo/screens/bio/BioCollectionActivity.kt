package com.example.biomemo.screens.bio

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.biomemo.R
import com.example.biomemo.navigation.MainBottomNav
import com.example.biomemo.navigation.MainNavDestination
import com.example.biomemo.data.BioEntry
import com.example.biomemo.data.BioRecordChangeTracker
import com.example.biomemo.data.BioRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class BioCollectionActivity : AppCompatActivity() {
    private val repository = BioRepository()
    private val bioScope = CoroutineScope(Dispatchers.Main + Job())
    private var entries: List<BioEntry> = emptyList()
    private var sortMode: BioCollectionSort = BioCollectionSort.NEWEST
    private val selectedEntryIds = linkedSetOf<String>()
    private lateinit var refreshProgress: ProgressBar
    private lateinit var collectionScroll: ScrollView
    private var pullStartY: Float? = null
    private var isRefreshing = false
    private var observedBioRecordVersion = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bio_collection)

        MainBottomNav.setup(this, MainNavDestination.RECORDS, intent.getStringExtra(MainBottomNav.EXTRA_USERNAME))
        refreshProgress = findViewById(R.id.progressBioCollectionRefresh)
        collectionScroll = findViewById(R.id.scrollviewBioCollection)
        setupPullRefresh()

        loadEntries(forceRefresh = false)
    }

    override fun onResume() {
        super.onResume()
        if (observedBioRecordVersion != BioRecordChangeTracker.currentVersion()) {
            loadEntries(forceRefresh = false)
        }
    }

    private fun setupPullRefresh() {
        collectionScroll.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pullStartY = event.y.takeIf { collectionScroll.scrollY == 0 && !isRefreshing }
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val startY = pullStartY ?: return@setOnTouchListener false
                    if (collectionScroll.scrollY == 0 && event.y - startY > dp(PULL_REFRESH_DISTANCE_DP).toFloat()) {
                        pullStartY = null
                        loadEntries(forceRefresh = true)
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_CANCEL,
                MotionEvent.ACTION_UP -> {
                    pullStartY = null
                    false
                }
                else -> false
            }
        }
    }

    private fun loadEntries(forceRefresh: Boolean) {
        if (isRefreshing) return
        isRefreshing = true
        refreshProgress.visibility = if (forceRefresh) View.VISIBLE else View.GONE
        bioScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (forceRefresh) repository.refreshAllEntries() else repository.getAllEntries()
                }
            }.onSuccess { refreshedEntries ->
                entries = refreshedEntries
                selectedEntryIds.retainAll(entries.map { it.id }.toSet())
                renderCollection()
                observedBioRecordVersion = BioRecordChangeTracker.currentVersion()
            }.onFailure { error ->
                Toast.makeText(this@BioCollectionActivity, error.message ?: "Refresh failed", Toast.LENGTH_SHORT).show()
            }
            isRefreshing = false
            refreshProgress.visibility = View.GONE
        }
    }

    private fun renderCollection() {
        renderActions()
        val container = findViewById<LinearLayout>(R.id.linearlayoutBioEntries)
        container.removeAllViews()
        val sortedEntries = entries.sortedByMode(sortMode)
        if (sortedEntries.isEmpty()) {
            container.addView(text("No BioRecords yet. Add your first observation from the capture tab.", 15, R.color.bio_ink_muted, false))
            return
        }
        sortedEntries.forEach { entry -> container.addView(createEntryCard(entry)) }
    }

    private fun renderActions() {
        findViewById<TextView>(R.id.textviewBioCollectionSubtitle).text = if (selectedEntryIds.isEmpty()) {
            "${entries.size} records · hold a record to select"
        } else {
            "${selectedEntryIds.size} selected"
        }
        findViewById<LinearLayout>(R.id.linearlayoutBioCollectionActions).apply {
            removeAllViews()
            addView(sortRow())
            if (selectedEntryIds.isNotEmpty()) addView(selectionActionRow())
        }
    }

    private fun sortRow(): HorizontalScrollView {
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
            addView(LinearLayout(this@BioCollectionActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                BioCollectionSort.entries.forEach { mode ->
                    addView(button(mode.label, mode.background(sortMode), mode.textColor(sortMode)).apply {
                        setOnClickListener {
                            sortMode = mode
                            renderCollection()
                        }
                    })
                }
            })
        }
    }

    private fun selectionActionRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
            addView(button("Select all", R.drawable.bg_chip, R.color.bio_forest_700).apply {
                setOnClickListener {
                    selectedEntryIds.clear()
                    selectedEntryIds += entries.map { it.id }
                    renderCollection()
                }
            })
            addView(button("Open", R.drawable.bg_chip_outline, R.color.bio_forest_700).apply {
                isEnabled = selectedEntryIds.size == 1
                alpha = if (isEnabled) 1f else 0.45f
                setOnClickListener {
                    entries.firstOrNull { it.id == selectedEntryIds.firstOrNull() }?.let(::openBioRecord)
                }
            })
            addView(button("Clear", R.drawable.bg_chip_outline, R.color.bio_forest_700).apply {
                setOnClickListener {
                    selectedEntryIds.clear()
                    renderCollection()
                }
            })
            addView(button("Delete", R.drawable.bg_button_danger, R.color.white).apply {
                setOnClickListener { confirmDeleteSelected() }
            })
        }
    }

    private fun createEntryCard(entry: BioEntry): LinearLayout {
        val isSelected = entry.id in selectedEntryIds
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(if (isSelected) R.drawable.bg_card_selected else R.drawable.bg_card_elevated)
            isClickable = true
            isFocusable = true
            setPadding(dp(18), dp(16), dp(18), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
            setOnClickListener {
                if (selectedEntryIds.isEmpty()) {
                    openBioRecord(entry)
                } else {
                    toggleSelection(entry)
                }
            }
            setOnLongClickListener {
                toggleSelection(entry)
                true
            }
        }

        val thumbnail = thumbnail(entry)
        card.addView(thumbnail)
        loadThumbnail(entry, thumbnail)
        card.addView(LinearLayout(this@BioCollectionActivity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
            addView(text(entry.commonName, 18, R.color.bio_ink, true))
            addView(text(entry.scientificName, 13, R.color.bio_ink_muted, false))
            addView(text("${entry.category} · ${entry.confidence}% match", 13, R.color.bio_forest_600, true))
            addView(text("${entry.date} · ${entry.location}", 13, R.color.bio_ink_muted, false))
            addView(text(entry.tags.joinToString(" · "), 12, R.color.bio_forest_700, true).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(text(entry.notes.previewText(), 14, R.color.bio_ink, false).apply {
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
            })
        })
        return card
    }

    private fun toggleSelection(entry: BioEntry) {
        if (entry.id in selectedEntryIds) selectedEntryIds.remove(entry.id) else selectedEntryIds.add(entry.id)
        renderCollection()
    }

    private fun confirmDeleteSelected() {
        val deleteIds = selectedEntryIds.toList()
        if (deleteIds.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Delete BioRecords?")
            .setMessage("Delete ${deleteIds.size} selected BioRecord${if (deleteIds.size == 1) "" else "s"} from your collection?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ -> deleteSelected(deleteIds) }
            .show()
    }

    private fun deleteSelected(ids: List<String>) {
        bioScope.launch {
            runCatching {
                repository.deleteEntries(ids)
            }.onSuccess { count ->
                entries = entries.filterNot { it.id in ids }
                selectedEntryIds.clear()
                renderCollection()
                observedBioRecordVersion = BioRecordChangeTracker.currentVersion()
                Toast.makeText(this@BioCollectionActivity, "Deleted $count BioRecord${if (count == 1) "" else "s"}", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(this@BioCollectionActivity, error.message ?: "Delete failed", Toast.LENGTH_SHORT).show()
            }
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
        setPadding(dp(14), dp(14), dp(14), dp(14))
        layoutParams = LinearLayout.LayoutParams(dp(74), dp(74)).apply {
            rightMargin = dp(14)
        }
    }

    private fun loadThumbnail(entry: BioEntry, imageView: ImageView) {
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

    private fun text(value: String, sizeSp: Int, colorRes: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = sizeSp.toFloat()
        setTextColor(getColor(colorRes))
        if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(0, dp(2), 0, dp(2))
    }

    private fun button(value: String, backgroundRes: Int, colorRes: Int): TextView = text(value, 13, colorRes, true).apply {
        gravity = Gravity.CENTER
        minHeight = dp(44)
        minWidth = dp(64)
        setBackgroundResource(backgroundRes)
        setPadding(dp(14), dp(8), dp(14), dp(8))
        isClickable = true
        isFocusable = true
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { rightMargin = dp(8) }
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
}

private enum class BioCollectionSort(val label: String) {
    NEWEST("Newest"),
    COMMON_NAME("Name"),
    SCIENTIFIC_NAME("Scientific"),
    CONFIDENCE("Match"),
    LOCATION("Location"),
    TAGS("Tags");

    fun background(activeMode: BioCollectionSort): Int {
        return if (this == activeMode) R.drawable.bg_chip else R.drawable.bg_chip_outline
    }

    fun textColor(activeMode: BioCollectionSort): Int {
        return if (this == activeMode) R.color.bio_forest_900 else R.color.bio_forest_700
    }
}

private const val PULL_REFRESH_DISTANCE_DP = 96

private fun List<BioEntry>.sortedByMode(mode: BioCollectionSort): List<BioEntry> {
    return when (mode) {
        BioCollectionSort.NEWEST -> sortedByDescending { it.savedDate.ifBlank { it.observedDate } }
        BioCollectionSort.COMMON_NAME -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.commonName })
        BioCollectionSort.SCIENTIFIC_NAME -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.scientificName })
        BioCollectionSort.CONFIDENCE -> sortedByDescending { it.confidence }
        BioCollectionSort.LOCATION -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.location })
        BioCollectionSort.TAGS -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.tags.joinToString(" ") })
    }
}
