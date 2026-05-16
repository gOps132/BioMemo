package com.example.biomemo

import com.example.biomemo.data.BioRecordGateway
import com.example.biomemo.data.BioRecordPhotoMetadata
import com.example.biomemo.data.BioRecordRow
import com.example.biomemo.data.BioRecordPhotoUpload
import com.example.biomemo.data.BioRepository
import com.example.biomemo.data.BioRecordSpeciesProfileUpsert
import com.example.biomemo.data.GbifSpeciesSearchRow
import com.example.biomemo.data.IdentificationCandidateRow
import com.example.biomemo.data.NewBioRecordDraft
import com.example.biomemo.data.NewImageMetadata
import com.example.biomemo.data.SpeciesEnrichmentPreview
import com.example.biomemo.data.SpeciesProfileRow
import com.example.biomemo.data.SpeciesSearchResult
import com.example.biomemo.data.SpeciesSourceGateway
import com.example.biomemo.data.SpeciesSourceRepository
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
    fun mapsLinkedSpeciesProfileToSpeciesDetails() = runBlocking {
        val repository = BioRepository(
            FakeBioRecordGateway(
                rows = listOf(sampleRow(id = "record-with-profile", speciesProfileId = "species-1")),
                speciesProfiles = listOf(
                    SpeciesProfileRow(
                        id = "species-1",
                        commonName = "Bernese Mountain Dog",
                        scientificName = "Canis familiaris",
                        taxonomy = "Animalia > Chordata > Mammalia",
                        habitat = "Domestic environments.",
                        diet = "Omnivorous domestic diet.",
                        lifespan = "7 to 10 years.",
                        distribution = "Worldwide as a domestic breed.",
                        conservationStatus = "least concern",
                        sourceApi = "GBIF, Wikipedia",
                        lastEnrichedAt = "2026-05-16T00:00:00Z"
                    )
                )
            )
        )

        val entry = repository.getEntryById("record-with-profile")

        assertEquals("Bernese Mountain Dog", entry?.commonName)
        assertEquals("Canis familiaris", entry?.scientificName)
        assertEquals("Domestic environments.", entry?.habitat)
        assertEquals("May 16, 2026", entry?.lastEnrichedDate)
    }

    @Test
    fun enrichBioRecordSpeciesStoresProfileFromBestCandidate() = runBlocking {
        val gateway = FakeBioRecordGateway(
            rows = listOf(sampleRow(id = "record-to-enrich")),
            identificationCandidates = listOf(
                IdentificationCandidateRow(
                    bioRecordId = "record-to-enrich",
                    commonName = "Bernese Mountain Dog",
                    scientificName = "Canis familiaris",
                    confidenceScore = 99,
                    selected = true
                )
            )
        )
        val repository = BioRepository(
            gateway = gateway,
            speciesRepository = SpeciesSourceRepository(FakeSpeciesSourceGateway())
        )

        val entry = repository.enrichBioRecordSpecies("record-to-enrich")

        assertEquals("record-to-enrich", gateway.upsertedSpeciesProfile?.bioRecordId)
        assertEquals("Canis familiaris", gateway.upsertedSpeciesProfile?.scientificName)
        assertEquals("Animalia > Chordata > Mammalia", entry?.taxonomy)
        assertEquals("Domestic environments.", entry?.habitat)
    }

    @Test
    fun searchSuggestionsUseCachedRecordsCandidatesAndSpeciesProfiles() = runBlocking {
        val repository = BioRepository(
            FakeBioRecordGateway(
                rows = listOf(sampleRow(id = "record-with-suggestions", locationLabel = "Mossy Creek")),
                identificationCandidates = listOf(
                    IdentificationCandidateRow(
                        bioRecordId = "record-with-suggestions",
                        commonName = "Asian common toad",
                        scientificName = "Duttaphrynus melanostictus",
                        selected = true
                    )
                ),
                speciesProfiles = listOf(
                    SpeciesProfileRow(
                        id = "species-toad",
                        commonName = "Asian common toad",
                        scientificName = "Duttaphrynus melanostictus"
                    )
                )
            )
        )

        val suggestions = repository.getSearchSuggestions()

        assertTrue(suggestions.contains("Mossy Creek"))
        assertTrue(suggestions.contains("Asian common toad"))
        assertTrue(suggestions.contains("Duttaphrynus melanostictus"))
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
        locationLabel: String = "Mossy Creek",
        speciesProfileId: String? = null
    ): BioRecordRow {
        return BioRecordRow(
            id = id,
            userId = "user-1",
            speciesProfileId = speciesProfileId,
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
        private val identificationCandidates: List<IdentificationCandidateRow> = emptyList(),
        private val speciesProfiles: List<SpeciesProfileRow> = emptyList()
    ) : BioRecordGateway {
        var uploadedPath: String? = null
        var uploadedBytes: ByteArray? = null
        var uploadedContentType: String? = null
        var insertedDraft: NewBioRecordDraft? = null
        var insertedImageMetadata: NewImageMetadata? = null
        var identifiedRecordId: String? = null
        var signedPhotoPath: String? = null
        var upsertedSpeciesProfile: BioRecordSpeciesProfileUpsert? = null
        var upsertedSpeciesProfileRow: SpeciesProfileRow? = null

        override suspend fun fetchBioRecords(limit: Int?): List<BioRecordRow> {
            return limit?.let { rows.take(it) } ?: rows
        }

        override suspend fun fetchIdentificationCandidates(): List<IdentificationCandidateRow> {
            return identificationCandidates
        }

        override suspend fun fetchSpeciesProfiles(): List<SpeciesProfileRow> {
            return speciesProfiles + listOfNotNull(upsertedSpeciesProfileRow)
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
                speciesProfileId = null,
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

        override suspend fun upsertBioRecordSpeciesProfile(profile: BioRecordSpeciesProfileUpsert): SpeciesProfileRow {
            upsertedSpeciesProfile = profile
            return SpeciesProfileRow(
                id = "upserted-species",
                commonName = profile.commonName,
                scientificName = profile.scientificName,
                taxonomy = profile.taxonomy,
                habitat = profile.habitat,
                diet = profile.diet,
                lifespan = profile.lifespan,
                distribution = profile.distribution,
                conservationStatus = profile.conservationStatus,
                sourceApi = profile.sourceApi,
                lastEnrichedAt = "2026-05-16T00:00:00Z"
            ).also {
                upsertedSpeciesProfileRow = it
            }
        }

        override suspend fun createSignedPhotoUrl(path: String): String {
            signedPhotoPath = path
            return signedUrl
        }
    }

    private class FakeSpeciesSourceGateway : SpeciesSourceGateway {
        override suspend fun searchGbifSpecies(query: String): List<GbifSpeciesSearchRow> {
            return listOf(
                GbifSpeciesSearchRow(
                    key = 5219173,
                    scientificName = "Canis familiaris",
                    canonicalName = "Canis familiaris",
                    rank = "SPECIES",
                    taxonomicStatus = "ACCEPTED",
                    kingdom = "Animalia",
                    phylum = "Chordata",
                    className = "Mammalia",
                    order = "Carnivora",
                    family = "Canidae",
                    genus = "Canis"
                )
            )
        }

        override suspend fun previewSpeciesEnrichment(species: SpeciesSearchResult): SpeciesEnrichmentPreview {
            return SpeciesEnrichmentPreview(
                commonName = "Bernese Mountain Dog",
                scientificName = "Canis familiaris",
                taxonomy = "Animalia > Chordata > Mammalia",
                habitat = "Domestic environments.",
                diet = "Omnivorous domestic diet.",
                lifespan = "7 to 10 years.",
                distribution = "Worldwide as a domestic breed.",
                conservationStatus = "least concern",
                sourceApi = "GBIF, Wikipedia",
                lastEnrichedDate = "May 16, 2026"
            )
        }
    }
}
