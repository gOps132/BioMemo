package com.example.biomemo

import com.example.biomemo.data.BioRecordGateway
import com.example.biomemo.data.BioRecordRow
import com.example.biomemo.data.BioRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BioRepositoryTest {
    @Test
    fun mapsSupabaseRowsToDisplayEntries() = runBlocking {
        val repository = BioRepository(FakeBioRecordGateway(listOf(sampleRow())))

        val entry = repository.getAllEntries().single()

        assertEquals("Unidentified organism", entry.commonName)
        assertEquals("Awaiting identification", entry.scientificName)
        assertEquals("BioRecord", entry.category)
        assertEquals("May 6, 2026", entry.date)
        assertEquals("Mossy Creek", entry.location)
        assertEquals(87, entry.confidence)
        assertEquals("draft", entry.verificationStatus)
        assertEquals("GPS coordinates available", entry.metadataAvailability)
        assertEquals("camera", entry.sourceType)
        assertEquals("Pending identification", entry.sourceApi)
    }

    @Test
    fun searchMatchesMappedRecordFields() = runBlocking {
        val repository = BioRepository(FakeBioRecordGateway(listOf(sampleRow(locationLabel = "Fern Ridge"))))

        assertEquals("Fern Ridge", repository.search("fern").single().location)
        assertEquals(1, repository.search("draft").size)
        assertTrue(repository.search("missing").isEmpty())
    }

    @Test
    fun statsSummarizeFetchedEntries() = runBlocking {
        val repository = BioRepository(
            FakeBioRecordGateway(
                listOf(
                    sampleRow(id = "record-1"),
                    sampleRow(id = "record-2", locationLabel = "Second Site")
                )
            )
        )

        val stats = repository.getStats()

        assertEquals(2, stats.sightings)
        assertEquals(0, stats.species)
        assertEquals("1d", stats.streak)
    }

    @Test
    fun entryLookupUsesFetchedRows() = runBlocking {
        val repository = BioRepository(FakeBioRecordGateway(listOf(sampleRow(id = "target-record"))))

        assertEquals("target-record", repository.getEntryById("target-record")?.id)
        assertEquals(null, repository.getEntryById("other-record"))
    }

    private fun sampleRow(
        id: String = "record-1",
        locationLabel: String = "Mossy Creek"
    ): BioRecordRow {
        return BioRecordRow(
            id = id,
            userId = "user-1",
            photoUrl = "https://example.com/photo.jpg",
            thumbnailUrl = null,
            sourceType = "camera",
            observedAt = "2026-05-06T08:30:00Z",
            savedAt = "2026-05-06T09:45:00Z",
            latitude = 12.34,
            longitude = 56.78,
            locationLabel = locationLabel,
            notes = "Small organism near the waterline.",
            confidenceScore = 87,
            verificationStatus = "draft",
            metadataAvailability = "GPS coordinates available"
        )
    }

    private class FakeBioRecordGateway(
        private val rows: List<BioRecordRow>
    ) : BioRecordGateway {
        override suspend fun fetchBioRecords(limit: Int?): List<BioRecordRow> {
            return limit?.let { rows.take(it) } ?: rows
        }
    }
}
