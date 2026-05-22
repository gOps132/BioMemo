package com.example.biomemo

import com.example.biomemo.data.BioEntry
import com.example.biomemo.screens.bio.BioCollectionSort
import com.example.biomemo.screens.bio.sortedByMode
import org.junit.Assert.assertEquals
import org.junit.Test

class BioCollectionSortTest {
    @Test
    fun sortsByNewestSavedDateFirst() {
        val sorted = listOf(
            entry("old", savedDate = "May 1, 2026"),
            entry("new", savedDate = "May 22, 2026")
        ).sortedByMode(BioCollectionSort.NEWEST)

        assertEquals(listOf("new", "old"), sorted.map { it.id })
    }

    @Test
    fun sortsByConfidenceHighestFirst() {
        val sorted = listOf(
            entry("low", confidence = 10),
            entry("high", confidence = 90)
        ).sortedByMode(BioCollectionSort.CONFIDENCE)

        assertEquals(listOf("high", "low"), sorted.map { it.id })
    }

    private fun entry(
        id: String,
        savedDate: String = "May 22, 2026",
        confidence: Int = 50
    ) = BioEntry(
        id = id,
        commonName = id,
        scientificName = id,
        category = "BioRecord",
        date = savedDate,
        location = "Site",
        latitude = null,
        longitude = null,
        confidence = confidence,
        notes = "Notes",
        tags = emptyList(),
        userId = "user",
        photoUrl = "",
        sourceType = "upload",
        observedDate = savedDate,
        savedDate = savedDate,
        verificationStatus = "draft",
        metadataAvailability = "available",
        taxonomy = "Taxonomy",
        habitat = "Habitat",
        diet = "Diet",
        lifespan = "Lifespan",
        distribution = "Distribution",
        conservationStatus = "Status",
        sourceApi = "Test",
        lastEnrichedDate = savedDate
    )
}
