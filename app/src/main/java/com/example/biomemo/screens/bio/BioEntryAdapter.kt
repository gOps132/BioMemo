package com.example.biomemo.screens.bio

import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.biomemo.data.BioEntry

class BioEntryAdapter(
    private val createViewHolder: (ViewGroup) -> BioEntryViewHolder,
    private val bindViewHolder: (BioEntryViewHolder, BioEntry) -> Unit
) : RecyclerView.Adapter<BioEntryViewHolder>() {
    private var currentEntries: List<BioEntry> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BioEntryViewHolder = createViewHolder(parent)

    override fun onBindViewHolder(holder: BioEntryViewHolder, position: Int) {
        bindViewHolder(holder, currentEntries[position])
    }

    override fun getItemCount(): Int = currentEntries.size

    fun submitEntries(nextEntries: List<BioEntry>) {
        currentEntries = nextEntries
        notifyDataSetChanged()
    }
}

class BioEntryViewHolder(
    val card: LinearLayout,
    val thumbnail: ImageView,
    val commonName: TextView,
    val scientificName: TextView,
    val category: TextView,
    val location: TextView,
    val tags: TextView,
    val notes: TextView,
    val more: TextView
) : RecyclerView.ViewHolder(card)
