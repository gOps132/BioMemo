package com.example.biomemo

import com.example.biomemo.features.records.domain.BioEntry
import com.example.biomemo.screens.map.BioMapModel
import org.junit.Assert.assertEquals
import org.junit.Test

class BioMapModelTest {
    @Test
    fun mapsRecordsWithValidCoordinatesToPinsWithLocationMetadata() {
        val state = BioMapModel.fromEntries(
            listOf(
                sampleEntry(
                    id = "record-1",
                    commonName = "Asian common toad",
                    scientificName = "Duttaphrynus melanostictus",
                    location = "Mossy Creek",
                    latitude = 14.5995,
                    longitude = 120.9842,
                    photoUrl = "bio-records/record-1/original.jpg",
                    metadataAvailability = "GPS coordinates available"
                )
            )
        )

        assertEquals(1, state.totalRecords)
        assertEquals(1, state.pins.size)
        assertEquals(0, state.recordsWithoutLocation)
        assertEquals("1 mapped · all BioRecords have GPS", state.summary)
        assertEquals("bio-records/record-1/original.jpg", state.pins.single().photoUrl)
        assertEquals("Mossy Creek · May 6, 2026 · 87% ID confidence", state.pins.single().primaryMetadata)
        assertEquals("GPS coordinates available · 14.59950, 120.98420", state.pins.single().locationMetadata)
        assertEquals("amphibian", state.pins.single().tagsLabel)
    }

    @Test
    fun excludesRecordsMissingOrInvalidCoordinateMetadata() {
        val state = BioMapModel.fromEntries(
            listOf(
                sampleEntry(id = "mapped", latitude = 10.0, longitude = 20.0),
                sampleEntry(id = "missing-lat", latitude = null, longitude = 20.0),
                sampleEntry(id = "missing-lon", latitude = 10.0, longitude = null),
                sampleEntry(id = "bad-lat", latitude = 91.0, longitude = 20.0),
                sampleEntry(id = "bad-lon", latitude = 10.0, longitude = -181.0),
                sampleEntry(id = "null-island-placeholder", latitude = 0.0, longitude = 0.0)
            )
        )

        assertEquals(listOf("mapped"), state.pins.map { it.id })
        assertEquals(5, state.recordsWithoutLocation)
        assertEquals("1 mapped · 5 missing GPS", state.summary)
        assertEquals(null, state.emptyTitle)
        assertEquals(null, state.emptyMessage)
    }

    @Test
    fun explainsEmptyMapWhenRecordsHaveNoUsableGps() {
        val state = BioMapModel.fromEntries(
            listOf(
                sampleEntry(id = "missing", latitude = null, longitude = null),
                sampleEntry(id = "invalid", latitude = 10.0, longitude = 190.0)
            )
        )

        assertEquals(emptyList<String>(), state.pins.map { it.id })
        assertEquals(2, state.recordsWithoutLocation)
        assertEquals("No mapped BioRecords · 2 missing GPS", state.summary)
        assertEquals("No mapped BioRecords", state.emptyTitle)
        assertEquals("2 records need usable GPS metadata before they can appear here.", state.emptyMessage)
    }

    @Test
    fun explainsEmptyMapWhenCollectionHasNoRecords() {
        val state = BioMapModel.fromEntries(emptyList())

        assertEquals("No BioRecords yet", state.emptyTitle)
        assertEquals("Capture BioRecords with location enabled to build your map.", state.emptyMessage)
        assertEquals("No mapped BioRecords yet", state.summary)
    }

    private fun sampleEntry(
        id: String = "record-1",
        commonName: String = "Unidentified organism",
        scientificName: String = "Awaiting identification",
        location: String = "Mossy Creek",
        photoUrl: String = "",
        latitude: Double? = 12.34,
        longitude: Double? = 56.78,
        metadataAvailability: String = "GPS coordinates available"
    ): BioEntry = BioEntry(
        id = id,
        commonName = commonName,
        scientificName = scientificName,
        category = "BioRecord",
        date = "May 6, 2026",
        location = location,
        latitude = latitude,
        longitude = longitude,
        confidence = 87,
        notes = "Found near water.",
        tags = listOf("amphibian"),
        userId = "user-1",
        photoUrl = photoUrl,
        sourceType = "camera",
        observedDate = "May 6, 2026",
        savedDate = "May 7, 2026",
        verificationStatus = "draft",
        metadataAvailability = metadataAvailability,
        taxonomy = "Animalia",
        habitat = "Wetlands",
        diet = "Insects",
        lifespan = "Unknown",
        distribution = "Philippines",
        conservationStatus = "not evaluated",
        sourceApi = "OpenAI image identification",
        lastEnrichedDate = "May 7, 2026"
    )
}
