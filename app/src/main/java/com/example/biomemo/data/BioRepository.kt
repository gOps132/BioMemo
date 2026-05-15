package com.example.biomemo.data

import com.example.biomemo.config.AppConfig
import com.example.biomemo.data.remote.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
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
    suspend fun currentUserId(): String?
    suspend fun uploadBioRecordPhoto(path: String, bytes: ByteArray, contentType: String)
    suspend fun insertBioRecordDraft(draft: NewBioRecordDraft): BioRecordRow
    suspend fun insertImageMetadata(metadata: NewImageMetadata)
    suspend fun identifyBioRecordImage(recordId: String): List<IdentificationCandidateRow>
    suspend fun createSignedPhotoUrl(path: String): String
}

@Serializable
data class BioRecordRow(
    val id: String,
    @SerialName("user_id") val userId: String,
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

class BioRepository(
    private val gateway: BioRecordGateway = SupabaseBioRecordGateway(),
    private val recordIdProvider: () -> String = { UUID.randomUUID().toString() }
) {
    suspend fun getAllEntries(): List<BioEntry> {
        val rows = gateway.fetchBioRecords()
        if (rows.isEmpty()) return emptyList()
        val candidatesByRecord = gateway.fetchIdentificationCandidates().groupBy { it.bioRecordId }
        return rows.map { it.toBioEntry(candidatesByRecord[it.id].bestCandidate()) }
    }

    suspend fun getRecentEntries(limit: Int = 2): List<BioEntry> {
        val rows = gateway.fetchBioRecords(limit)
        if (rows.isEmpty()) return emptyList()
        val candidatesByRecord = gateway.fetchIdentificationCandidates().groupBy { it.bioRecordId }
        return rows.map { it.toBioEntry(candidatesByRecord[it.id].bestCandidate()) }
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

    suspend fun createDraftUploadRecord(photo: BioRecordPhotoUpload): BioEntry {
        val userId = gateway.currentUserId() ?: error("Sign in before uploading BioRecord photos.")
        val recordId = recordIdProvider()
        val photoPath = BioRecordPhotoPath.forOriginal(userId, recordId, photo.contentType)

        gateway.uploadBioRecordPhoto(photoPath, photo.bytes, photo.contentType)
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
        return insertedRow.toBioEntry(candidates.bestCandidate())
    }

    suspend fun createSignedPhotoUrl(path: String): String {
        require(path.isNotBlank()) { "Photo path is missing." }
        return gateway.createSignedPhotoUrl(path)
    }

    private fun BioRecordRow.toBioEntry(candidate: IdentificationCandidateRow? = null): BioEntry {
        val savedLabel = savedAt.toDisplayDate()
        val observedLabel = observedAt?.toDisplayDate() ?: "Not recorded"
        val statusLabel = verificationStatus.ifBlank { "draft" }
        val metadataLabel = metadataAvailability.ifBlank { "unknown" }
        val sourceLabel = sourceType.ifBlank { "unknown" }
        val commonNameLabel = candidate?.commonName?.takeIf { it.isNotBlank() } ?: UNIDENTIFIED_COMMON_NAME
        val scientificNameLabel = candidate?.scientificName?.takeIf { it.isNotBlank() } ?: AWAITING_IDENTIFICATION
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
            taxonomy = NOT_ENRICHED,
            habitat = NOT_ENRICHED,
            diet = NOT_ENRICHED,
            lifespan = NOT_ENRICHED,
            distribution = NOT_ENRICHED,
            conservationStatus = NOT_ENRICHED,
            sourceApi = if (candidate == null) "Pending identification" else "Gemini image identification",
            lastEnrichedDate = NOT_ENRICHED
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
