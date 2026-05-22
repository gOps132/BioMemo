package com.example.biomemo.screens.bio

import com.example.biomemo.R
import com.example.biomemo.data.BioEntry

enum class BioCollectionSort(val label: String) {
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

fun List<BioEntry>.sortedByMode(mode: BioCollectionSort): List<BioEntry> {
    return when (mode) {
        BioCollectionSort.NEWEST -> sortedByDescending { it.savedDate.ifBlank { it.observedDate } }
        BioCollectionSort.COMMON_NAME -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.commonName })
        BioCollectionSort.SCIENTIFIC_NAME -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.scientificName })
        BioCollectionSort.CONFIDENCE -> sortedByDescending { it.confidence }
        BioCollectionSort.LOCATION -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.location })
        BioCollectionSort.TAGS -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.tags.joinToString(" ") })
    }
}
