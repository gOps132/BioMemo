package com.example.biomemo.data

import com.example.biomemo.config.AppConfig
import com.example.biomemo.data.remote.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.time.Duration.Companion.hours

interface BioRecordGateway {
    suspend fun fetchBioRecords(limit: Int? = null): List<BioRecordRow>
    suspend fun fetchIdentificationCandidates(): List<IdentificationCandidateRow>
    suspend fun fetchSpeciesProfiles(): List<SpeciesProfileRow>
    suspend fun deleteBioRecords(ids: List<String>, photoPaths: List<String>)
    suspend fun currentUserId(): String?
    suspend fun uploadBioRecordPhoto(path: String, bytes: ByteArray, contentType: String)
    suspend fun insertBioRecordDraft(draft: NewBioRecordDraft): BioRecordRow
    suspend fun insertImageMetadata(metadata: NewImageMetadata)
    suspend fun identifyBioRecordImage(recordId: String): List<IdentificationCandidateRow>
    suspend fun upsertBioRecordSpeciesProfile(profile: BioRecordSpeciesProfileUpsert): SpeciesProfileRow
    suspend fun createSignedPhotoUrl(path: String): String
}

@Serializable
data class BioRecordRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("species_profile_id") val speciesProfileId: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("source_type") val sourceType: String,
    @SerialName("observed_at") val observedAt: String? = null,
    @SerialName("saved_at") val savedAt: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("location_label") val locationLabel: String,
    val notes: String? = null,
    @SerialName("confidence_score") val confidenceScore: Int? = null,
    @SerialName("verification_status") val verificationStatus: String,
    @SerialName("metadata_availability") val metadataAvailability: String
)

@Serializable
data class IdentificationCandidateRow(
    val id: String? = null,
    @SerialName("bio_record_id") val bioRecordId: String,
    @SerialName("common_name") val commonName: String? = null,
    @SerialName("scientific_name") val scientificName: String,
    @SerialName("confidence_score") val confidenceScore: Int? = null,
    val reasoning: String? = null,
    @SerialName("visible_traits") val visibleTraits: String? = null,
    @SerialName("uncertainty_notes") val uncertaintyNotes: String? = null,
    val selected: Boolean = false
)

@Serializable
data class SpeciesProfileRow(
    val id: String,
    @SerialName("common_name") val commonName: String,
    @SerialName("scientific_name") val scientificName: String,
    val taxonomy: String? = null,
    val habitat: String? = null,
    val diet: String? = null,
    val lifespan: String? = null,
    val distribution: String? = null,
    @SerialName("conservation_status") val conservationStatus: String? = null,
    @SerialName("source_api") val sourceApi: String? = null,
    @SerialName("last_enriched_at") val lastEnrichedAt: String? = null
)

data class BioRecordPhotoUpload(
    val bytes: ByteArray,
    val contentType: String,
    val metadata: BioRecordPhotoMetadata = BioRecordPhotoMetadata()
)

data class BioRecordPhotoMetadata(
    val capturedAt: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val orientation: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val metadataAvailability: String = "unknown",
    val raw: Map<String, String> = emptyMap()
)

@Serializable
data class NewBioRecordDraft(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("photo_url") val photoUrl: String,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    @SerialName("source_type") val sourceType: String = "upload",
    @SerialName("observed_at") val observedAt: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    @SerialName("verification_status") val verificationStatus: String = "draft",
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    @SerialName("metadata_availability") val metadataAvailability: String = "unknown"
)

@Serializable
data class NewImageMetadata(
    @SerialName("bio_record_id") val bioRecordId: String,
    @SerialName("captured_at") val capturedAt: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val orientation: Int? = null,
    @SerialName("file_type") val fileType: String,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("metadata_raw") val metadataRaw: JsonObject = buildJsonObject { }
)

@Serializable
data class BioRecordSpeciesProfileUpsert(
    @SerialName("p_bio_record_id") val bioRecordId: String,
    @SerialName("p_common_name") val commonName: String,
    @SerialName("p_scientific_name") val scientificName: String,
    @SerialName("p_taxonomy") val taxonomy: String? = null,
    @SerialName("p_habitat") val habitat: String? = null,
    @SerialName("p_diet") val diet: String? = null,
    @SerialName("p_lifespan") val lifespan: String? = null,
    @SerialName("p_distribution") val distribution: String? = null,
    @SerialName("p_conservation_status") val conservationStatus: String? = null,
    @SerialName("p_source_api") val sourceApi: String? = null
)

class BioRepository(
    private val gateway: BioRecordGateway = SupabaseBioRecordGateway(),
    private val speciesRepository: SpeciesSourceRepository = SpeciesSourceRepository(),
    private val cache: BioRepositoryCache = if (gateway is SupabaseBioRecordGateway) {
        BioRepositoryCache.shared
    } else {
        BioRepositoryCache()
    },
    private val recordIdProvider: () -> String = { UUID.randomUUID().toString() }
) {
    suspend fun getAllEntries(): List<BioEntry> {
        cache.entries?.let { return it }
        val rows = fetchBioRecords()
        if (rows.isEmpty()) return emptyList()
        val candidatesByRecord = fetchIdentificationCandidates().groupBy { it.bioRecordId }
        val speciesProfilesById = fetchSpeciesProfiles().associateBy { it.id }
        return rows.map { row -> row.toBioEntry(candidatesByRecord[row.id].bestCandidate(), speciesProfilesById[row.speciesProfileId]) }
            .also { cache.entries = it }
    }

    suspend fun getRecentEntries(limit: Int = 2): List<BioEntry> {
        val rows = fetchBioRecords(limit)
        if (rows.isEmpty()) return emptyList()
        val candidatesByRecord = fetchIdentificationCandidates().groupBy { it.bioRecordId }
        val speciesProfilesById = fetchSpeciesProfiles().associateBy { it.id }
        return rows.map { row -> row.toBioEntry(candidatesByRecord[row.id].bestCandidate(), speciesProfilesById[row.speciesProfileId]) }
    }

    suspend fun getEntryById(id: String): BioEntry? {
        return getAllEntries().firstOrNull { it.id == id }
    }

    suspend fun getStats(): BioStats {
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

    suspend fun search(query: String): List<BioEntry> {
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

    suspend fun getSearchSuggestions(limit: Int = 8): List<String> {
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

    suspend fun deleteEntries(ids: Collection<String>): Int {
        val distinctIds = ids.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (distinctIds.isEmpty()) return 0
        val rowsToDelete = fetchBioRecords().filter { it.id in distinctIds }
        val photoPaths = rowsToDelete.mapNotNull { it.photoUrl.storagePathOrNull() }
        gateway.deleteBioRecords(distinctIds, photoPaths)
        cache.bioRecords = cache.bioRecords?.filterNot { it.id in distinctIds }
        cache.identificationCandidates = cache.identificationCandidates?.filterNot { it.bioRecordId in distinctIds }
        cache.entries = cache.entries?.filterNot { it.id in distinctIds }
        return distinctIds.size
    }

    suspend fun createDraftUploadRecord(photo: BioRecordPhotoUpload): BioEntry {
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
        val candidates = runCatching { gateway.identifyBioRecordImage(recordId) }.getOrDefault(emptyList())
        if (candidates.isNotEmpty()) cache.identificationCandidates = null
        val bestCandidate = candidates.bestCandidate()
        val speciesProfile = enrichCandidate(recordId, bestCandidate)
        return insertedRow.toBioEntry(bestCandidate, speciesProfile)
    }

    suspend fun enrichBioRecordSpecies(recordId: String): BioEntry? {
        val row = fetchBioRecords().firstOrNull { it.id == recordId } ?: return null
        val existingProfile = row.speciesProfileId?.let { id -> fetchSpeciesProfiles().firstOrNull { it.id == id } }
        if (existingProfile != null) {
            val candidate = fetchIdentificationCandidates()
                .filter { it.bioRecordId == recordId }
                .bestCandidate()
            return row.toBioEntry(candidate, existingProfile)
        }
        val candidate = fetchIdentificationCandidates()
            .filter { it.bioRecordId == recordId }
            .bestCandidate()
            ?: return row.toBioEntry(null, null)
        val profile = enrichCandidate(recordId, candidate)
        return row.copy(speciesProfileId = profile?.id ?: row.speciesProfileId).toBioEntry(candidate, profile)
    }

    suspend fun createSignedPhotoUrl(path: String): String {
        require(path.isNotBlank()) { "Photo path is missing." }
        cache.signedPhotoUrls[path]
            ?.takeIf { it.isFresh() }
            ?.let { return it.value }
        return gateway.createSignedPhotoUrl(path)
            .also { cache.signedPhotoUrls[path] = CachedValue(it) }
    }

    private suspend fun enrichCandidate(recordId: String, candidate: IdentificationCandidateRow?): SpeciesProfileRow? {
        if (candidate == null || candidate.scientificName == AWAITING_IDENTIFICATION) return null
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

    private suspend fun fetchIdentificationCandidates(): List<IdentificationCandidateRow> {
        return cache.identificationCandidates
            ?: gateway.fetchIdentificationCandidates().also { cache.identificationCandidates = it }
    }

    private suspend fun fetchSpeciesProfiles(): List<SpeciesProfileRow> {
        return cache.speciesProfiles
            ?: gateway.fetchSpeciesProfiles().also { cache.speciesProfiles = it }
    }

    private fun invalidateRecords() {
        cache.bioRecords = null
        cache.entries = null
    }

    private fun BioRecordRow.toBioEntry(candidate: IdentificationCandidateRow? = null, speciesProfile: SpeciesProfileRow? = null): BioEntry {
        val savedLabel = savedAt.toDisplayDate()
        val observedLabel = observedAt?.toDisplayDate() ?: "Not recorded"
        val statusLabel = verificationStatus.ifBlank { "draft" }
        val metadataLabel = metadataAvailability.ifBlank { "unknown" }
        val sourceLabel = sourceType.ifBlank { "unknown" }
        val commonNameLabel = speciesProfile?.commonName?.takeIf { it.isNotBlank() }
            ?: candidate?.commonName?.takeIf { it.isNotBlank() }
            ?: UNIDENTIFIED_COMMON_NAME
        val scientificNameLabel = speciesProfile?.scientificName?.takeIf { it.isNotBlank() }
            ?: candidate?.scientificName?.takeIf { it.isNotBlank() }
            ?: AWAITING_IDENTIFICATION
        val confidenceLabel = candidate?.confidenceScore ?: confidenceScore ?: 0
        val notesLabel = listOfNotNull(
            notes?.takeIf { it.isNotBlank() },
            candidate?.reasoning?.takeIf { it.isNotBlank() }?.let { "AI reasoning: $it" },
            candidate?.visibleTraits?.takeIf { it.isNotBlank() }?.let { "Visible traits: $it" },
            candidate?.uncertaintyNotes?.takeIf { it.isNotBlank() }?.let { "Uncertainty: $it" }
        ).joinToString("\n\n").ifBlank { "No field notes yet." }

        return BioEntry(
            id = id,
            commonName = commonNameLabel,
            scientificName = scientificNameLabel,
            category = "BioRecord",
            date = savedLabel,
            location = locationLabel.ifBlank { "location unknown" },
            latitude = latitude,
            longitude = longitude,
            confidence = confidenceLabel,
            notes = notesLabel,
            tags = listOf(statusLabel, metadataLabel, sourceLabel),
            userId = userId,
            photoUrl = thumbnailUrl ?: photoUrl.orEmpty(),
            sourceType = sourceLabel,
            observedDate = observedLabel,
            savedDate = savedLabel,
            verificationStatus = statusLabel,
            metadataAvailability = metadataLabel,
            taxonomy = speciesProfile?.taxonomy.presentOr(NOT_ENRICHED),
            habitat = speciesProfile?.habitat.presentOr(NOT_ENRICHED),
            diet = speciesProfile?.diet.presentOr(NOT_ENRICHED),
            lifespan = speciesProfile?.lifespan.presentOr(NOT_ENRICHED),
            distribution = speciesProfile?.distribution.presentOr(NOT_ENRICHED),
            conservationStatus = speciesProfile?.conservationStatus.presentOr(NOT_ENRICHED),
            sourceApi = speciesProfile?.sourceApi.presentOr(if (candidate == null) "Pending identification" else "Gemini image identification"),
            lastEnrichedDate = speciesProfile?.lastEnrichedAt?.toDisplayDate() ?: NOT_ENRICHED
        )
    }

    private fun String.toDisplayDate(): String {
        return try {
            OffsetDateTime.parse(this).format(DISPLAY_DATE_FORMATTER)
        } catch (_: Throwable) {
            take(10).ifBlank { "Unknown date" }
        }
    }

    private companion object {
        const val UNIDENTIFIED_COMMON_NAME = "Unidentified organism"
        const val AWAITING_IDENTIFICATION = "Awaiting identification"
        const val NOT_ENRICHED = "Not enriched yet"
        val DISPLAY_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    }
}

class BioRepositoryCache {
    var bioRecords: List<BioRecordRow>? = null
    var identificationCandidates: List<IdentificationCandidateRow>? = null
    var speciesProfiles: List<SpeciesProfileRow>? = null
    var entries: List<BioEntry>? = null
    val signedPhotoUrls: MutableMap<String, CachedValue<String>> = mutableMapOf()

    companion object {
        val shared = BioRepositoryCache()
    }
}

data class CachedValue<T>(
    val value: T,
    val cachedAtMillis: Long = System.currentTimeMillis()
) {
    fun isFresh(maxAgeMillis: Long = 50 * 60 * 1000L): Boolean {
        return System.currentTimeMillis() - cachedAtMillis < maxAgeMillis
    }
}

private fun IdentificationCandidateRow.matchesSpeciesProfile(profile: SpeciesProfileRow): Boolean {
    return scientificName.sameSpeciesName(profile.scientificName) ||
        commonName.orEmpty().sameSpeciesName(profile.commonName)
}

private fun String.sameSpeciesName(other: String): Boolean {
    val left = speciesNameKey()
    val right = other.speciesNameKey()
    return left.isNotBlank() && right.isNotBlank() && (left == right || left.startsWith(right) || right.startsWith(left))
}

private fun String.speciesNameKey(): String {
    return trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun String?.presentOr(fallback: String): String = this?.takeIf { it.isNotBlank() } ?: fallback

private fun String?.presentOrNull(): String? = this?.takeIf { it.isNotBlank() }

private fun String?.storagePathOrNull(): String? = this?.takeIf { it.isNotBlank() && !it.startsWith("http") }

private fun List<IdentificationCandidateRow>?.bestCandidate(): IdentificationCandidateRow? {
    return this
        ?.sortedWith(
            compareByDescending<IdentificationCandidateRow> { it.selected }
                .thenByDescending { it.confidenceScore ?: -1 }
        )
        ?.firstOrNull()
}

private fun Map<String, String>.toJsonObject(): JsonObject {
    return buildJsonObject {
        forEach { (key, value) -> put(key, value) }
    }
}

object BioRecordPhotoPath {
    fun forOriginal(userId: String, recordId: String, contentType: String): String {
        val extension = when (contentType.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            else -> "jpg"
        }
        return "$userId/$recordId/original.$extension"
    }
}

class SupabaseBioRecordGateway(
    private val client: SupabaseClient = SupabaseClientProvider.client,
    private val identifyEndpointUrl: String = AppConfig.supabaseUrl.trimEnd('/') + "/functions/v1/identify-biorecord-image",
    private val anonKey: String = AppConfig.supabaseAnonKey,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : BioRecordGateway {
    override suspend fun fetchBioRecords(limit: Int?): List<BioRecordRow> {
        val userId = currentUserId() ?: return emptyList()

        return client.from("bio_records")
            .select {
                filter {
                    eq("user_id", userId)
                }
                order("saved_at", Order.DESCENDING)
                limit?.let { limit(it.toLong()) }
            }
            .decodeList<BioRecordRow>()
    }

    override suspend fun fetchIdentificationCandidates(): List<IdentificationCandidateRow> {
        return client.from("identification_candidates")
            .select()
            .decodeList<IdentificationCandidateRow>()
    }

    override suspend fun fetchSpeciesProfiles(): List<SpeciesProfileRow> {
        return client.from("species_profiles")
            .select()
            .decodeList<SpeciesProfileRow>()
    }

    override suspend fun deleteBioRecords(ids: List<String>, photoPaths: List<String>) {
        if (ids.isEmpty()) return
        val userId = currentUserId() ?: error("Sign in before deleting BioRecords.")
        client.from("bio_records")
            .delete {
                filter {
                    eq("user_id", userId)
                    isIn("id", ids)
                }
            }
        if (photoPaths.isNotEmpty()) {
            runCatching {
                client.storage.from(BIORECORD_PHOTO_BUCKET).delete(photoPaths)
            }
        }
    }

    override suspend fun currentUserId(): String? {
        return client.auth.currentSessionOrNull()?.user?.id ?: client.auth.currentUserOrNull()?.id
    }

    override suspend fun uploadBioRecordPhoto(path: String, bytes: ByteArray, contentType: String) {
        client.storage.from(BIORECORD_PHOTO_BUCKET)
            .upload(path, bytes) {
                upsert = false
                this.contentType = ContentType.parse(contentType)
            }
    }

    override suspend fun insertBioRecordDraft(draft: NewBioRecordDraft): BioRecordRow {
        return client.from("bio_records")
            .insert(draft) {
                select()
            }
            .decodeSingle<BioRecordRow>()
    }

    override suspend fun insertImageMetadata(metadata: NewImageMetadata) {
        client.from("image_metadata")
            .insert(metadata)
    }

    override suspend fun identifyBioRecordImage(recordId: String): List<IdentificationCandidateRow> = withContext(Dispatchers.IO) {
        val accessToken = client.auth.currentSessionOrNull()?.accessToken
            ?: error("Sign in before identifying BioRecord photos.")
        val connection = (URL(identifyEndpointUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = IDENTIFY_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Authorization", "Bearer $accessToken")
        }

        OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
            writer.write(json.encodeToString(IdentifyBioRecordRequest(recordId)))
        }

        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        connection.disconnect()

        if (responseCode !in 200..299) {
            throw IllegalStateException("Image identification failed: HTTP $responseCode")
        }

        json.decodeFromString<IdentifyBioRecordResponse>(body).candidates
    }

    override suspend fun upsertBioRecordSpeciesProfile(profile: BioRecordSpeciesProfileUpsert): SpeciesProfileRow {
        return client.postgrest
            .rpc(
                function = "upsert_biorecord_species_profile",
                parameters = profile.toJsonObject()
            )
            .decodeSingle<SpeciesProfileRow>()
    }

    override suspend fun createSignedPhotoUrl(path: String): String {
        return client.storage.from(BIORECORD_PHOTO_BUCKET)
            .createSignedUrl(path, expiresIn = 1.hours)
    }

    private companion object {
        const val BIORECORD_PHOTO_BUCKET = "biorecord-photos"
        const val TIMEOUT_MS = 15_000
        const val IDENTIFY_TIMEOUT_MS = 45_000
    }
}

@Serializable
private data class IdentifyBioRecordRequest(
    val bioRecordId: String
)

@Serializable
private data class IdentifyBioRecordResponse(
    val candidates: List<IdentificationCandidateRow> = emptyList()
)

private fun BioRecordSpeciesProfileUpsert.toJsonObject(): JsonObject {
    return buildJsonObject {
        put("p_bio_record_id", bioRecordId)
        put("p_common_name", commonName)
        put("p_scientific_name", scientificName)
        put("p_taxonomy", taxonomy)
        put("p_habitat", habitat)
        put("p_diet", diet)
        put("p_lifespan", lifespan)
        put("p_distribution", distribution)
        put("p_conservation_status", conservationStatus)
        put("p_source_api", sourceApi)
    }
}
