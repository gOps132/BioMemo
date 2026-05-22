package com.example.biomemo.data

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

@Serializable
data class BioRecordRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("species_profile_id") val speciesProfileId: String? = null,
    @SerialName("species_profiles") val speciesProfile: SpeciesProfileRow? = null,
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
