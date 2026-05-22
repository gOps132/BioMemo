package com.example.biomemo.screens.bio

import android.content.Intent
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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.biomemo.R
import com.example.biomemo.navigation.MainBottomNav
import com.example.biomemo.navigation.MainNavDestination
import com.example.biomemo.data.BioEntry
import com.example.biomemo.data.BioRecordUseCases
import com.example.biomemo.screens.map.BioMapActivity
import com.example.biomemo.ui.BioImageLoader
import com.example.biomemo.ui.roundedImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BioCollectionActivity : AppCompatActivity() {
    private val bioRecordUseCases = BioRecordUseCases()
    private val bioScope = CoroutineScope(Dispatchers.Main + Job())
    private var entries: List<BioEntry> = emptyList()
    private var sortMode: BioCollectionSort = BioCollectionSort.NEWEST
    private val selectionState = BioCollectionSelectionState()
    private val expandedNoteIds = linkedSetOf<String>()
    private lateinit var refreshProgress: ProgressBar
    private lateinit var entriesList: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var entriesAdapter: BioEntryAdapter
    private var pullStartY: Float? = null
    private var isRefreshing = false
    private var navUsername: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bio_collection)

        navUsername = intent.getStringExtra(MainBottomNav.EXTRA_USERNAME)
        MainBottomNav.setup(this, MainNavDestination.RECORDS, navUsername)
        refreshProgress = findViewById(R.id.progressBioCollectionRefresh)
        emptyText = findViewById(R.id.textviewBioCollectionEmpty)
        entriesAdapter = BioEntryAdapter(
            createViewHolder = { createEntryViewHolder() },
            bindViewHolder = { holder, entry -> populateEntryCard(holder.card, holder, entry) }
        )
        entriesList = findViewById<RecyclerView>(R.id.recyclerviewBioEntries).apply {
            layoutManager = LinearLayoutManager(this@BioCollectionActivity)
            adapter = entriesAdapter
            setHasFixedSize(false)
        }
        setupPullRefresh()

        observeEntries()
    }

    private fun setupPullRefresh() {
        entriesList.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pullStartY = event.y.takeIf { !entriesList.canScrollVertically(-1) && !isRefreshing }
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val startY = pullStartY ?: return@setOnTouchListener false
                    if (!entriesList.canScrollVertically(-1) && event.y - startY > dp(PULL_REFRESH_DISTANCE_DP).toFloat()) {
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
        if (!forceRefresh) return
        if (isRefreshing) return
        isRefreshing = true
        refreshProgress.visibility = View.VISIBLE
        bioScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    bioRecordUseCases.refreshRecords()
                }
            }.onSuccess { refreshedEntries ->
                applyEntries(refreshedEntries)
            }.onFailure { error ->
                Toast.makeText(this@BioCollectionActivity, error.message ?: "Refresh failed", Toast.LENGTH_SHORT).show()
            }
            isRefreshing = false
            refreshProgress.visibility = View.GONE
        }
    }

    private fun observeEntries() {
        bioScope.launch {
            bioRecordUseCases.observeRecords().collectLatest { refreshedEntries ->
                applyEntries(refreshedEntries)
                isRefreshing = false
                refreshProgress.visibility = View.GONE
            }
        }
    }

    private fun applyEntries(refreshedEntries: List<BioEntry>) {
        entries = refreshedEntries
        selectionState.retainVisibleIds(entries.map { it.id })
        renderCollection()
    }

    private fun renderCollection() {
        renderActions()
        val sortedEntries = entries.sortedByMode(sortMode)
        emptyText.visibility = if (sortedEntries.isEmpty()) View.VISIBLE else View.GONE
        entriesList.visibility = if (sortedEntries.isEmpty()) View.GONE else View.VISIBLE
        entriesAdapter.submitEntries(sortedEntries)
    }

    private fun renderActions() {
        findViewById<TextView>(R.id.textviewBioCollectionSubtitle).text = if (selectionState.isEmpty) {
            "${entries.size} records · hold a record to select"
        } else {
            "${selectionState.count} selected"
        }
        findViewById<LinearLayout>(R.id.linearlayoutBioCollectionActions).apply {
            removeAllViews()
            addView(sortRow())
            if (!selectionState.isEmpty) addView(selectionActionRow())
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
                addView(button("BioMap", R.drawable.bg_chip, R.color.bio_forest_900).apply {
                    setOnClickListener { openBioMap() }
                })
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
                    selectionState.selectAll(entries.map { it.id })
                    renderCollection()
                }
            })
            addView(button("Open", R.drawable.bg_chip_outline, R.color.bio_forest_700).apply {
                isEnabled = selectionState.count == 1
                alpha = if (isEnabled) 1f else 0.45f
                setOnClickListener {
                    entries.firstOrNull { it.id == selectionState.singleSelectedId() }?.let(::openBioRecord)
                }
            })
            addView(button("Clear", R.drawable.bg_chip_outline, R.color.bio_forest_700).apply {
                setOnClickListener {
                    selectionState.clear()
                    renderCollection()
                }
            })
            addView(button("Delete", R.drawable.bg_button_danger, R.color.white).apply {
                setOnClickListener { confirmDeleteSelected() }
            })
        }
    }

    private fun createEntryCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isClickable = true
            isFocusable = true
            setPadding(dp(18), dp(16), dp(18), dp(16))
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
    }

    private fun populateEntryCard(card: LinearLayout, holder: BioEntryViewHolder, entry: BioEntry) {
        val isSelected = selectionState.contains(entry.id)
        card.setBackgroundResource(if (isSelected) R.drawable.bg_card_selected else R.drawable.bg_card_elevated)
        card.setOnClickListener {
            if (selectionState.isEmpty) {
                openBioRecord(entry)
            } else {
                toggleSelection(entry)
            }
        }
        card.setOnLongClickListener {
            toggleSelection(entry)
            true
        }
        holder.thumbnail.contentDescription = "${entry.commonName} thumbnail"
        holder.thumbnail.tag = entry.photoUrl
        holder.thumbnail.setImageResource(R.drawable.ic_bio_record_photo)
        holder.thumbnail.setPadding(0, 0, 0, 0)
        holder.thumbnail.scaleType = ImageView.ScaleType.CENTER_INSIDE
        loadThumbnail(entry, holder.thumbnail)
        holder.commonName.text = entry.commonName
        holder.scientificName.text = entry.scientificName
        holder.category.text = "${entry.category} · ${entry.confidence}% match"
        holder.location.text = "${entry.date} · ${entry.location}"
        holder.tags.text = entry.tags.joinToString(" · ")
        val compactNotes = entry.notes.compactText()
        val shouldCollapseNotes = compactNotes.length > NOTE_PREVIEW_MAX_LENGTH
        val notesExpanded = entry.id in expandedNoteIds
        holder.notes.text = if (notesExpanded || !shouldCollapseNotes) compactNotes else compactNotes.previewText()
        holder.notes.maxLines = if (notesExpanded) Int.MAX_VALUE else 3
        holder.notes.ellipsize = if (notesExpanded) null else TextUtils.TruncateAt.END
        holder.more.visibility = if (shouldCollapseNotes) View.VISIBLE else View.GONE
        holder.more.text = if (notesExpanded) "Less" else "More"
        holder.more.setOnClickListener {
            if (notesExpanded) expandedNoteIds.remove(entry.id) else expandedNoteIds.add(entry.id)
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) entriesAdapter.notifyItemChanged(position)
        }
    }

    private fun createEntryViewHolder(): BioEntryViewHolder {
        val card = createEntryCard()
        val thumbnail = thumbnail().also(card::addView)
        val commonName = text("", 18, R.color.bio_ink, true)
        val scientificName = text("", 13, R.color.bio_ink_muted, false)
        val category = text("", 13, R.color.bio_forest_600, true)
        val location = text("", 13, R.color.bio_ink_muted, false)
        val tags = text("", 12, R.color.bio_forest_700, true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val notes = text("", 14, R.color.bio_ink, false).apply {
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
        }
        val more = text("More", 13, R.color.bio_forest_600, true).apply {
            visibility = View.GONE
            isClickable = true
            isFocusable = true
            setPadding(0, dp(4), 0, 0)
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
            addView(category)
            addView(location)
            addView(tags)
            addView(notes)
            addView(more)
        })
        return BioEntryViewHolder(card, thumbnail, commonName, scientificName, category, location, tags, notes, more)
    }

    private fun toggleSelection(entry: BioEntry) {
        selectionState.toggle(entry.id)
        renderCollection()
    }

    private fun confirmDeleteSelected() {
        val deleteIds = selectionState.ids()
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
                bioRecordUseCases.deleteRecords(ids)
            }.onSuccess { count ->
                entries = entries.filterNot { it.id in ids }
                selectionState.clear()
                renderCollection()
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

    private fun openBioMap() {
        startActivity(
            Intent(this, BioMapActivity::class.java)
                .putExtra(MainBottomNav.EXTRA_USERNAME, navUsername)
        )
    }

    private fun thumbnail(): ImageView = roundedImageView(this, dp(10).toFloat()).apply {
        setImageResource(R.drawable.ic_bio_record_photo)
        setPadding(0, 0, 0, 0)
        layoutParams = LinearLayout.LayoutParams(dp(74), dp(74)).apply {
            rightMargin = dp(14)
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

    private fun String.compactText(): String {
        return lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    private fun String.previewText(maxLength: Int = NOTE_PREVIEW_MAX_LENGTH): String {
        return if (length <= maxLength) this else take(maxLength).trimEnd()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        bioScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val NOTE_PREVIEW_MAX_LENGTH = 180
    }
}

private const val PULL_REFRESH_DISTANCE_DP = 96
