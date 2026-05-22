package com.example.biomemo.data

import com.example.biomemo.features.species.data.SpeciesSourceRepository
import com.example.biomemo.features.species.domain.SpeciesRepository
import com.example.biomemo.features.species.domain.SpeciesSearchResult
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class BioRepository(
    private val gateway: BioRecordGateway = SupabaseBioRecordGateway(),
    private val speciesRepository: SpeciesRepository = SpeciesSourceRepository(),
    private val cache: BioRepositoryCache = if (gateway is SupabaseBioRecordGateway) {
        BioRepositoryCache.shared
    } else {
        BioRepositoryCache()
    },
    private val recordIdProvider: () -> String = { UUID.randomUUID().toString() }
) : BioRecordRepository {
    private val store by lazy {
        BioRecordStore(
            remoteChanges = { gateway.observeBioRecordChanges() },
            loadFreshEntries = { loadAllEntriesFresh() }
        )
    }

    override suspend fun getAllEntries(): List<BioEntry> {
        cache.entries?.let { return it }
        val rows = fetchBioRecords()
        if (rows.isEmpty()) return emptyList()
        val candidatesByRecord = fetchIdentificationCandidates().groupBy { it.bioRecordId }
        val speciesProfilesById = fetchSpeciesProfiles().associateBy { it.id }
        return rows.map { row -> row.toBioRecord(candidatesByRecord[row.id].bestCandidate(), speciesProfilesById[row.speciesProfileId]).toBioEntry() }
            .also { cache.entries = it }
    }

    override suspend fun refreshAllEntries(): List<BioEntry> {
        return store.refreshAllEntries()
    }

    override fun observeAllEntries(): Flow<List<BioEntry>> {
        return store.observeAllEntries()
    }

    override suspend fun getRecentEntries(limit: Int): List<BioEntry> {
        val rows = fetchBioRecords(limit)
        if (rows.isEmpty()) return emptyList()
        val candidatesByRecord = fetchIdentificationCandidates().groupBy { it.bioRecordId }
        val speciesProfilesById = fetchSpeciesProfiles().associateBy { it.id }
        return rows.map { row -> row.toBioRecord(candidatesByRecord[row.id].bestCandidate(), speciesProfilesById[row.speciesProfileId]).toBioEntry() }
    }

    override suspend fun getEntryById(id: String): BioEntry? {
        val row = gateway.fetchBioRecordById(id) ?: return null
        val candidate = fetchIdentificationCandidates()
            .filter { it.bioRecordId == id }
            .bestCandidate()
        val profile = row.speciesProfileId?.let { profileId ->
            fetchSpeciesProfiles().firstOrNull { it.id == profileId }
        }
        return row.toBioRecord(candidate, profile).toBioEntry()
    }

    override fun observeEntryById(id: String): Flow<BioEntry> {
        return kotlinx.coroutines.flow.flow {
            gateway.observeBioRecord(id).collect { row ->
                cache.identificationCandidates = null
                cache.speciesProfiles = null
                emit(row.toBioEntryWithLookups())
            }
        }
    }

    override suspend fun getStats(): BioStats {
        val entries = getAllEntries()
        val identifiedSpecies = entries
            .map { it.scientificName }
            .filter { it != AWAITING_IDENTIFICATION }
            .distinct()
            .size

        return BioStats(
            sightings = entries.size,
            species = identifiedSpecies,
            streak = if (entries.isEmpty()) "0d" else "1d"
        )
    }

    override suspend fun search(query: String): List<BioEntry> {
        val entries = getAllEntries()
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isEmpty()) return entries

        return entries.filter { entry ->
            listOf(
                entry.commonName,
                entry.scientificName,
                entry.category,
                entry.location,
                entry.notes,
                entry.sourceType,
                entry.verificationStatus,
                entry.metadataAvailability,
                entry.tags.joinToString(" ")
            ).any { value -> value.lowercase().contains(normalizedQuery) }
        }
    }

    override suspend fun getSearchSuggestions(limit: Int): List<String> {
        val entries = getAllEntries()
        val candidates = fetchIdentificationCandidates()
        val profiles = fetchSpeciesProfiles()
        return (entries.flatMap { listOf(it.commonName, it.scientificName, it.location) } +
            candidates.flatMap { listOfNotNull(it.commonName, it.scientificName) } +
            profiles.flatMap { listOf(it.commonName, it.scientificName, it.taxonomy.orEmpty()) })
            .map { it.trim() }
            .filter { it.isNotBlank() && it != UNIDENTIFIED_COMMON_NAME && it != AWAITING_IDENTIFICATION }
            .distinctBy { it.lowercase() }
            .take(limit)
    }

    override suspend fun deleteEntries(ids: Collection<String>): Int {
        val distinctIds = ids.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (distinctIds.isEmpty()) return 0
        val rowsToDelete = fetchBioRecords().filter { it.id in distinctIds }
        val photoPaths = rowsToDelete.mapNotNull { it.photoUrl.storagePathOrNull() }
        gateway.deleteBioRecords(distinctIds, photoPaths)
        cache.bioRecords = cache.bioRecords?.filterNot { it.id in distinctIds }
        cache.identificationCandidates = cache.identificationCandidates?.filterNot { it.bioRecordId in distinctIds }
        cache.entries = cache.entries?.filterNot { it.id in distinctIds }
        BioRecordChangeTracker.markChanged()
        return distinctIds.size
    }

    override suspend fun createDraftUploadRecord(photo: BioRecordPhotoUpload): BioEntry {
        val userId = gateway.currentUserId() ?: error("Sign in before uploading BioRecord photos.")
        val recordId = recordIdProvider()
        val photoPath = BioRecordPhotoPath.forOriginal(userId, recordId, photo.contentType)

        gateway.uploadBioRecordPhoto(photoPath, photo.bytes, photo.contentType)
        invalidateRecords()
        val insertedRow = gateway.insertBioRecordDraft(
            NewBioRecordDraft(
                id = recordId,
                userId = userId,
                photoUrl = photoPath,
                observedAt = photo.metadata.capturedAt,
                latitude = photo.metadata.latitude,
                longitude = photo.metadata.longitude,
                metadataAvailability = photo.metadata.metadataAvailability
            )
        )
        gateway.insertImageMetadata(
            NewImageMetadata(
                bioRecordId = recordId,
                capturedAt = photo.metadata.capturedAt,
                latitude = photo.metadata.latitude,
                longitude = photo.metadata.longitude,
                orientation = photo.metadata.orientation,
                fileType = photo.contentType,
                width = photo.metadata.width,
                height = photo.metadata.height,
                metadataRaw = photo.metadata.raw.toJsonObject()
            )
        )
        val identificationResult = runCatching { gateway.identifyBioRecordImage(recordId) }
        val candidates = identificationResult.getOrDefault(emptyList())
        if (candidates.isNotEmpty()) cache.identificationCandidates = null
        val bestCandidate = candidates.bestCandidate()
        val speciesProfile = enrichCandidate(recordId, bestCandidate)
        val displayRow = if (identificationResult.isFailure || bestCandidate == null) {
            insertedRow.copy(verificationStatus = FAILED_STATUS, confidenceScore = null)
        } else {
            insertedRow
        }
        return displayRow.toBioRecord(bestCandidate, speciesProfile).toBioEntry()
            .also { BioRecordChangeTracker.markChanged() }
    }

    override suspend fun retryIdentification(recordId: String): BioEntry? {
        val candidates = runCatching { gateway.identifyBioRecordImage(recordId) }.getOrDefault(emptyList())
        invalidateRecords(includeLookups = true)
        val row = gateway.fetchBioRecordById(recordId) ?: return null
        val bestCandidate = candidates.bestCandidate()
        val speciesProfile = enrichCandidate(recordId, bestCandidate)
        return row.toBioRecord(bestCandidate, speciesProfile).toBioEntry()
            .also { BioRecordChangeTracker.markChanged() }
    }

    override suspend fun enrichBioRecordSpecies(recordId: String): BioEntry? {
        val row = fetchBioRecords().firstOrNull { it.id == recordId } ?: return null
        val existingProfile = row.speciesProfileId?.let { id -> fetchSpeciesProfiles().firstOrNull { it.id == id } }
        if (existingProfile != null) {
            val candidate = fetchIdentificationCandidates()
                .filter { it.bioRecordId == recordId }
                .bestCandidate()
            return row.toBioRecord(candidate, existingProfile).toBioEntry()
        }
        val candidate = fetchIdentificationCandidates()
            .filter { it.bioRecordId == recordId }
            .bestCandidate()
            ?: return row.toBioRecord(null, null).toBioEntry()
        val profile = enrichCandidate(recordId, candidate)
        return row.copy(speciesProfileId = profile?.id ?: row.speciesProfileId)
            .toBioRecord(candidate, profile)
            .toBioEntry()
            .also { if (profile != null) BioRecordChangeTracker.markChanged() }
    }

    override suspend fun createSignedPhotoUrl(path: String): String {
        require(path.isNotBlank()) { "Photo path is missing." }
        cache.signedPhotoUrls[path]
            ?.takeIf { it.isFresh() }
            ?.let { return it.value }
        return gateway.createSignedPhotoUrl(path)
            .also { cache.signedPhotoUrls[path] = CachedValue(it) }
    }

    private suspend fun enrichCandidate(recordId: String, candidate: IdentificationCandidateRow?): SpeciesProfileRow? {
        if (candidate == null || !candidate.isUsableForEnrichment()) return null
        cachedSpeciesProfileFor(candidate)?.let { profile ->
            linkCachedSpeciesProfile(recordId, candidate, profile)?.let { return it }
        }
        val species = runCatching {
            speciesRepository.searchSpecies(candidate.scientificName).firstOrNull()
                ?: candidate.commonName?.let { speciesRepository.searchSpecies(it).firstOrNull() }
        }.getOrNull() ?: return null
        cachedSpeciesProfileFor(species)?.let { profile ->
            linkCachedSpeciesProfile(recordId, candidate, profile)?.let { return it }
        }
        val enrichment = runCatching { speciesRepository.previewEnrichment(species) }.getOrNull() ?: return null
        return runCatching {
            gateway.upsertBioRecordSpeciesProfile(
                BioRecordSpeciesProfileUpsert(
                    bioRecordId = recordId,
                    commonName = enrichment.commonName.presentOr(candidate.commonName ?: species.commonName ?: species.canonicalName),
                    scientificName = enrichment.scientificName.presentOr(species.scientificName),
                    taxonomy = enrichment.taxonomy.presentOrNull(),
                    habitat = enrichment.habitat.presentOrNull(),
                    diet = enrichment.diet.presentOrNull(),
                    lifespan = enrichment.lifespan.presentOrNull(),
                    distribution = enrichment.distribution.presentOrNull(),
                    conservationStatus = enrichment.conservationStatus.presentOrNull(),
                    sourceApi = enrichment.sourceApi.presentOrNull()
                )
            )
        }.getOrNull()
            ?.also { profile ->
                cache.speciesProfiles = (fetchSpeciesProfiles().filterNot { it.id == profile.id } + profile)
                invalidateRecords()
            }
    }

    private suspend fun linkCachedSpeciesProfile(
        recordId: String,
        candidate: IdentificationCandidateRow,
        profile: SpeciesProfileRow
    ): SpeciesProfileRow? {
        return runCatching {
            gateway.upsertBioRecordSpeciesProfile(
                BioRecordSpeciesProfileUpsert(
                    bioRecordId = recordId,
                    commonName = profile.commonName,
                    scientificName = profile.scientificName,
                    taxonomy = profile.taxonomy,
                    habitat = profile.habitat,
                    diet = profile.diet,
                    lifespan = profile.lifespan,
                    distribution = profile.distribution,
                    conservationStatus = profile.conservationStatus,
                    sourceApi = profile.sourceApi ?: "Cached species profile"
                )
            )
        }.getOrNull()
            ?.also { linkedProfile ->
                cache.speciesProfiles = (fetchSpeciesProfiles().filterNot { it.id == linkedProfile.id } + linkedProfile)
                invalidateRecords()
            }
            ?: profile.takeIf { candidate.matchesSpeciesProfile(it) }
    }

    private suspend fun cachedSpeciesProfileFor(candidate: IdentificationCandidateRow): SpeciesProfileRow? {
        return fetchSpeciesProfiles().firstOrNull { candidate.matchesSpeciesProfile(it) }
    }

    private suspend fun cachedSpeciesProfileFor(species: SpeciesSearchResult): SpeciesProfileRow? {
        return fetchSpeciesProfiles().firstOrNull { profile ->
            profile.scientificName.sameSpeciesName(species.scientificName) ||
                profile.scientificName.sameSpeciesName(species.canonicalName) ||
                profile.commonName.sameSpeciesName(species.commonName.orEmpty())
        }
    }

    private suspend fun fetchBioRecords(limit: Int? = null): List<BioRecordRow> {
        val rows = cache.bioRecords ?: gateway.fetchBioRecords().also { cache.bioRecords = it }
        return limit?.let { rows.take(it) } ?: rows
    }

    private suspend fun loadAllEntriesFresh(): List<BioEntry> {
        invalidateRecords(includeLookups = true)
        return getAllEntries()
    }

    private suspend fun fetchIdentificationCandidates(): List<IdentificationCandidateRow> {
        return cache.identificationCandidates
            ?: gateway.fetchIdentificationCandidates().also { cache.identificationCandidates = it }
    }

    private suspend fun fetchSpeciesProfiles(): List<SpeciesProfileRow> {
        return cache.speciesProfiles
            ?: gateway.fetchSpeciesProfiles().also { cache.speciesProfiles = it }
    }

    private suspend fun BioRecordRow.toBioEntryWithLookups(): BioEntry {
        val candidate = fetchIdentificationCandidates()
            .filter { it.bioRecordId == id }
            .bestCandidate()
        val profile = speciesProfile ?: speciesProfileId?.let { profileId ->
            fetchSpeciesProfiles().firstOrNull { it.id == profileId }
        }
        return toBioRecord(candidate, profile).toBioEntry()
    }

    private fun invalidateRecords(includeLookups: Boolean = false) {
        cache.bioRecords = null
        cache.entries = null
        if (includeLookups) {
            cache.identificationCandidates = null
            cache.speciesProfiles = null
        }
    }
}
