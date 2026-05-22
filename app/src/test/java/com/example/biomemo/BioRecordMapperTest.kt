package com.example.biomemo

import com.example.biomemo.data.BioRecordRow
import com.example.biomemo.data.toBioEntry
import com.example.biomemo.data.toBioRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BioRecordMapperTest {
    @Test
    fun mapsRemoteRowsToDomainWithoutDisplayFallbackCopy() {
        val record = sampleRow(
            verificationStatus = "failed",
            confidenceScore = null
        ).toBioRecord(candidate = null, speciesProfile = null)

        assertEquals("failed", record.verificationStatus)
        assertEquals(null, record.confidenceScore)
        assertNull(record.identification)
    }

    @Test
    fun mapsDomainRecordsToDisplayEntriesWithFailureCopy() {
        val entry = sampleRow(
            verificationStatus = "failed",
            confidenceScore = null
        ).toBioRecord(candidate = null, speciesProfile = null)
            .toBioEntry()

        assertEquals("No organism identified", entry.commonName)
        assertEquals("Not available", entry.scientificName)
        assertEquals(0, entry.confidence)
    }

    private fun sampleRow(
        verificationStatus: String = "draft",
        confidenceScore: Int? = 87
    ) = BioRecordRow(
        id = "record-1",
        userId = "user-1",
        sourceType = "camera",
        observedAt = "2026-05-06T00:00:00Z",
        savedAt = "2026-05-07T00:00:00Z",
        latitude = 14.5995,
        longitude = 120.9842,
        locationLabel = "Mossy Creek",
        notes = "Found near water.",
        confidenceScore = confidenceScore,
        verificationStatus = verificationStatus,
        metadataAvailability = "GPS coordinates available"
    )
}
