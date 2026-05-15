package com.example.biomemo

import com.example.biomemo.data.BioRecordGateway
import com.example.biomemo.data.BioRecordPhotoMetadata
import com.example.biomemo.data.BioRecordRow
import com.example.biomemo.data.BioRecordPhotoUpload
import com.example.biomemo.data.BioRepository
import com.example.biomemo.data.IdentificationCandidateRow
import com.example.biomemo.data.NewBioRecordDraft
import com.example.biomemo.data.NewImageMetadata
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    fun mapsBestIdentificationCandidateToDisplayEntry() = runBlocking {
        val repository = BioRepository(
            FakeBioRecordGateway(
                rows = listOf(sampleRow(id = "record-with-candidate")),
                identificationCandidates = listOf(
                    IdentificationCandidateRow(
                        bioRecordId = "record-with-candidate",
                        commonName = "Asian common toad",
                        scientificName = "Duttaphrynus melanostictus",
                        confidenceScore = 82,
                        reasoning = "Warty skin and parotoid glands are visible.",
                        visibleTraits = "Brown warty skin; stout body",
                        uncertaintyNotes = "Photo angle hides feet.",
                        selected = true
                    )
                )
            )
        )

        val entry = repository.getEntryById("record-with-candidate")

        assertEquals("Asian common toad", entry?.commonName)
        assertEquals("Duttaphrynus melanostictus", entry?.scientificName)
        assertEquals(82, entry?.confidence)
        assertEquals("Gemini image identification", entry?.sourceApi)
        assertTrue(entry?.notes?.contains("AI reasoning: Warty skin") == true)
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
                contentType = "image/png",
                metadata = BioRecordPhotoMetadata(
                    capturedAt = "2026-05-05T10:15:30Z",
                    latitude = 14.5995,
                    longitude = 120.9842,
                    orientation = 1,
                    width = 1024,
                    height = 768,
                    metadataAvailability = "capture time and GPS available",
                    raw = mapOf("camera" to "field-cam")
                )
            )
        )

        assertEquals("record-abc", entry.id)
        assertEquals("upload", entry.sourceType)
        assertEquals("draft", entry.verificationStatus)
        assertEquals("user-123/record-abc/original.png", entry.photoUrl)
        assertEquals("user-123/record-abc/original.png", gateway.uploadedPath)
        assertEquals("image/png", gateway.uploadedContentType)
        assertEquals("record-abc", gateway.identifiedRecordId)
        assertEquals(byteArrayOf(1, 2, 3).toList(), gateway.uploadedBytes?.toList())
        assertEquals(
            NewBioRecordDraft(
                id = "record-abc",
                userId = "user-123",
                photoUrl = "user-123/record-abc/original.png",
                sourceType = "upload",
                observedAt = "2026-05-05T10:15:30Z",
                latitude = 14.5995,
                longitude = 120.9842,
                verificationStatus = "draft",
                metadataAvailability = "capture time and GPS available"
            ),
            gateway.insertedDraft
        )
        assertEquals("record-abc", gateway.insertedImageMetadata?.bioRecordId)
        assertEquals("2026-05-05T10:15:30Z", gateway.insertedImageMetadata?.capturedAt)
        assertEquals(14.5995, gateway.insertedImageMetadata?.latitude)
        assertEquals(120.9842, gateway.insertedImageMetadata?.longitude)
        assertEquals(1, gateway.insertedImageMetadata?.orientation)
        assertEquals("image/png", gateway.insertedImageMetadata?.fileType)
        assertEquals(1024, gateway.insertedImageMetadata?.width)
        assertEquals(768, gateway.insertedImageMetadata?.height)
        assertEquals("field-cam", gateway.insertedImageMetadata?.metadataRaw?.get("camera")?.toString()?.trim('"'))
    }

    @Test
    fun draftUploadPayloadIncludesRequiredSourceType() {
        val payload = Json.encodeToString(
            NewBioRecordDraft(
                id = "record-abc",
                userId = "user-123",
                photoUrl = "user-123/record-abc/original.png"
            )
        )

        assertTrue(payload.contains("\"source_type\""))
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
        assertEquals(null, gateway.insertedImageMetadata)
        assertEquals(null, gateway.identifiedRecordId)
    }

    @Test
    fun signedPhotoUrlDelegatesToGateway() = runBlocking {
        val gateway = FakeBioRecordGateway(emptyList(), signedUrl = "https://signed.example/photo.jpg")
        val repository = BioRepository(gateway)

        val url = repository.createSignedPhotoUrl("user-123/record-abc/original.jpg")

        assertEquals("https://signed.example/photo.jpg", url)
        assertEquals("user-123/record-abc/original.jpg", gateway.signedPhotoPath)
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
        private val userId: String? = "user-1",
        private val signedUrl: String = "https://signed.example/default.jpg",
        private val identificationCandidates: List<IdentificationCandidateRow> = emptyList()
    ) : BioRecordGateway {
        var uploadedPath: String? = null
        var uploadedBytes: ByteArray? = null
        var uploadedContentType: String? = null
        var insertedDraft: NewBioRecordDraft? = null
        var insertedImageMetadata: NewImageMetadata? = null
        var identifiedRecordId: String? = null
        var signedPhotoPath: String? = null

        override suspend fun fetchBioRecords(limit: Int?): List<BioRecordRow> {
            return limit?.let { rows.take(it) } ?: rows
        }

        override suspend fun fetchIdentificationCandidates(): List<IdentificationCandidateRow> {
            return identificationCandidates
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
                observedAt = draft.observedAt,
                savedAt = "2026-05-07T00:00:00Z",
                latitude = draft.latitude,
                longitude = draft.longitude,
                locationLabel = "location unknown",
                notes = null,
                confidenceScore = null,
                verificationStatus = draft.verificationStatus,
                metadataAvailability = draft.metadataAvailability
            )
        }

        override suspend fun insertImageMetadata(metadata: NewImageMetadata) {
            insertedImageMetadata = metadata
        }

        override suspend fun identifyBioRecordImage(recordId: String): List<IdentificationCandidateRow> {
            identifiedRecordId = recordId
            return identificationCandidates.filter { it.bioRecordId == recordId }
        }

        override suspend fun createSignedPhotoUrl(path: String): String {
            signedPhotoPath = path
            return signedUrl
        }
    }
}
