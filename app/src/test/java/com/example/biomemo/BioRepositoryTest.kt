package com.example.biomemo

import com.example.biomemo.data.BioRecordGateway
import com.example.biomemo.data.BioRecordRow
import com.example.biomemo.data.BioRecordPhotoUpload
import com.example.biomemo.data.BioRepository
import com.example.biomemo.data.NewBioRecordDraft
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

    @Test
    fun createDraftUploadRecordStoresPhotoUnderPrivateUserPathBeforeInsert() = runBlocking {
        val gateway = FakeBioRecordGateway(emptyList(), userId = "user-123")
        val repository = BioRepository(gateway, recordIdProvider = { "record-abc" })

        val entry = repository.createDraftUploadRecord(
            BioRecordPhotoUpload(
                bytes = byteArrayOf(1, 2, 3),
                contentType = "image/png"
            )
        )

        assertEquals("record-abc", entry.id)
        assertEquals("upload", entry.sourceType)
        assertEquals("draft", entry.verificationStatus)
        assertEquals("user-123/record-abc/original.png", entry.photoUrl)
        assertEquals("user-123/record-abc/original.png", gateway.uploadedPath)
        assertEquals("image/png", gateway.uploadedContentType)
        assertEquals(byteArrayOf(1, 2, 3).toList(), gateway.uploadedBytes?.toList())
        assertEquals(
            NewBioRecordDraft(
                id = "record-abc",
                userId = "user-123",
                photoUrl = "user-123/record-abc/original.png",
                sourceType = "upload",
                verificationStatus = "draft",
                metadataAvailability = "unknown"
            ),
            gateway.insertedDraft
        )
    }

    @Test
    fun createDraftUploadRecordFailsBeforeUploadWhenUserMissing() = runBlocking {
        val gateway = FakeBioRecordGateway(emptyList(), userId = null)
        val repository = BioRepository(gateway, recordIdProvider = { "record-abc" })

        var thrown: Throwable? = null
        try {
            repository.createDraftUploadRecord(
                BioRecordPhotoUpload(
                    bytes = byteArrayOf(1, 2, 3),
                    contentType = "image/jpeg"
                )
            )
        } catch (error: Throwable) {
            thrown = error
        }
        assertTrue(thrown is IllegalStateException)
        assertEquals(null, gateway.uploadedPath)
        assertEquals(null, gateway.insertedDraft)
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
        private val rows: List<BioRecordRow>,
        private val userId: String? = "user-1"
    ) : BioRecordGateway {
        var uploadedPath: String? = null
        var uploadedBytes: ByteArray? = null
        var uploadedContentType: String? = null
        var insertedDraft: NewBioRecordDraft? = null

        override suspend fun fetchBioRecords(limit: Int?): List<BioRecordRow> {
            return limit?.let { rows.take(it) } ?: rows
        }

        override suspend fun currentUserId(): String? = userId

        override suspend fun uploadBioRecordPhoto(path: String, bytes: ByteArray, contentType: String) {
            uploadedPath = path
            uploadedBytes = bytes
            uploadedContentType = contentType
        }

        override suspend fun insertBioRecordDraft(draft: NewBioRecordDraft): BioRecordRow {
            insertedDraft = draft
            return BioRecordRow(
                id = draft.id,
                userId = draft.userId,
                photoUrl = draft.photoUrl,
                thumbnailUrl = null,
                sourceType = draft.sourceType,
                observedAt = null,
                savedAt = "2026-05-07T00:00:00Z",
                latitude = null,
                longitude = null,
                locationLabel = "location unknown",
                notes = null,
                confidenceScore = null,
                verificationStatus = draft.verificationStatus,
                metadataAvailability = draft.metadataAvailability
            )
        }
    }
}
