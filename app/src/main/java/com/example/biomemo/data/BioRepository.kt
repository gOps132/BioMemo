package com.example.biomemo.data

import com.example.biomemo.data.remote.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

interface BioRecordGateway {
    suspend fun fetchBioRecords(limit: Int? = null): List<BioRecordRow>
    suspend fun currentUserId(): String?
    suspend fun uploadBioRecordPhoto(path: String, bytes: ByteArray, contentType: String)
    suspend fun insertBioRecordDraft(draft: NewBioRecordDraft): BioRecordRow
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

data class BioRecordPhotoUpload(
    val bytes: ByteArray,
    val contentType: String
)

@Serializable
data class NewBioRecordDraft(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("photo_url") val photoUrl: String,
    @SerialName("source_type") val sourceType: String = "upload",
    @SerialName("verification_status") val verificationStatus: String = "draft",
    @SerialName("metadata_availability") val metadataAvailability: String = "unknown"
)

class BioRepository(
    private val gateway: BioRecordGateway = SupabaseBioRecordGateway(),
    private val recordIdProvider: () -> String = { UUID.randomUUID().toString() }
) {
    suspend fun getAllEntries(): List<BioEntry> = gateway.fetchBioRecords().map { it.toBioEntry() }

    suspend fun getRecentEntries(limit: Int = 2): List<BioEntry> {
        return gateway.fetchBioRecords(limit).map { it.toBioEntry() }
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
        return gateway.insertBioRecordDraft(
            NewBioRecordDraft(
                id = recordId,
                userId = userId,
                photoUrl = photoPath
            )
        ).toBioEntry()
    }

    private fun BioRecordRow.toBioEntry(): BioEntry {
        val savedLabel = savedAt.toDisplayDate()
        val observedLabel = observedAt?.toDisplayDate() ?: "Not recorded"
        val statusLabel = verificationStatus.ifBlank { "draft" }
        val metadataLabel = metadataAvailability.ifBlank { "unknown" }
        val sourceLabel = sourceType.ifBlank { "unknown" }

        return BioEntry(
            id = id,
            commonName = UNIDENTIFIED_COMMON_NAME,
            scientificName = AWAITING_IDENTIFICATION,
            category = "BioRecord",
            date = savedLabel,
            location = locationLabel.ifBlank { "location unknown" },
            latitude = latitude,
            longitude = longitude,
            confidence = confidenceScore ?: 0,
            notes = notes?.takeIf { it.isNotBlank() } ?: "No field notes yet.",
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
            sourceApi = "Pending identification",
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
    private val client: SupabaseClient = SupabaseClientProvider.client
) : BioRecordGateway {
    override suspend fun fetchBioRecords(limit: Int?): List<BioRecordRow> {
        val userId = client.auth.currentUserOrNull()?.id ?: return emptyList()

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

    private companion object {
        const val BIORECORD_PHOTO_BUCKET = "biorecord-photos"
    }
}
