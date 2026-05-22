package com.example.biomemo.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun BioRecordRow.toBioEntry(
    candidate: IdentificationCandidateRow? = null,
    speciesProfile: SpeciesProfileRow? = null
): BioEntry {
    val savedLabel = savedAt.toDisplayDate()
    val observedLabel = observedAt?.toDisplayDate() ?: "Not recorded"
    val statusLabel = verificationStatus.ifBlank { "draft" }
    val metadataLabel = metadataAvailability.ifBlank { "unknown" }
    val sourceLabel = sourceType.ifBlank { "unknown" }
    val species = speciesProfile ?: this.speciesProfile
    val identificationFailed = candidate == null && verificationStatus.equals(FAILED_STATUS, ignoreCase = true)
    val commonNameLabel = if (identificationFailed) {
        NO_ORGANISM_COMMON_NAME
    } else {
        species?.commonName?.takeIf { it.isNotBlank() }
            ?: candidate?.commonName?.takeIf { it.isNotBlank() }
            ?: UNIDENTIFIED_COMMON_NAME
    }
    val scientificNameLabel = if (identificationFailed) {
        IDENTIFICATION_NOT_AVAILABLE
    } else {
        species?.scientificName?.takeIf { it.isNotBlank() }
            ?: candidate?.scientificName?.takeIf { it.isNotBlank() }
            ?: AWAITING_IDENTIFICATION
    }
    val confidenceLabel = if (identificationFailed) 0 else candidate?.confidenceScore ?: confidenceScore ?: 0
    val notesLabel = listOfNotNull(
        notes?.takeIf { it.isNotBlank() },
        candidate?.reasoning?.takeIf { it.isNotBlank() },
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
        taxonomy = species?.taxonomy.presentOrNull() ?: if (identificationFailed) ENRICHMENT_UNAVAILABLE else NOT_ENRICHED,
        habitat = species?.habitat.presentOrNull() ?: if (identificationFailed) ENRICHMENT_UNAVAILABLE else NOT_ENRICHED,
        diet = species?.diet.presentOrNull() ?: if (identificationFailed) ENRICHMENT_UNAVAILABLE else NOT_ENRICHED,
        lifespan = species?.lifespan.presentOrNull() ?: if (identificationFailed) ENRICHMENT_UNAVAILABLE else NOT_ENRICHED,
        distribution = species?.distribution.presentOrNull() ?: if (identificationFailed) ENRICHMENT_UNAVAILABLE else NOT_ENRICHED,
        conservationStatus = species?.conservationStatus.presentOrNull() ?: if (identificationFailed) ENRICHMENT_UNAVAILABLE else NOT_ENRICHED,
        sourceApi = species?.sourceApi.presentOrNull()
            ?: if (identificationFailed) {
                IDENTIFICATION_FAILED_SOURCE
            } else if (candidate == null) {
                "Pending identification"
            } else {
                "OpenAI image identification"
            },
        lastEnrichedDate = species?.lastEnrichedAt?.toDisplayDate() ?: if (identificationFailed) ENRICHMENT_UNAVAILABLE else NOT_ENRICHED
    )
}

internal fun IdentificationCandidateRow.matchesSpeciesProfile(profile: SpeciesProfileRow): Boolean {
    return scientificName.sameSpeciesName(profile.scientificName) ||
        commonName.orEmpty().sameSpeciesName(profile.commonName)
}

internal fun String.sameSpeciesName(other: String): Boolean {
    val left = speciesNameKey()
    val right = other.speciesNameKey()
    return left.isNotBlank() && right.isNotBlank() && (left == right || left.startsWith(right) || right.startsWith(left))
}

internal fun String.speciesNameKey(): String {
    return trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

internal fun String?.presentOr(fallback: String): String = this?.takeIf { it.isNotBlank() } ?: fallback

internal fun String?.presentOrNull(): String? = this?.takeIf { it.isNotBlank() }

internal fun String?.storagePathOrNull(): String? = this?.takeIf { it.isNotBlank() && !it.startsWith("http") }

internal fun List<IdentificationCandidateRow>?.bestCandidate(): IdentificationCandidateRow? {
    return this
        ?.sortedWith(
            compareByDescending<IdentificationCandidateRow> { it.selected }
                .thenByDescending { it.confidenceScore ?: -1 }
        )
        ?.firstOrNull()
}

internal fun IdentificationCandidateRow.isUsableForEnrichment(): Boolean {
    val normalizedScientificName = scientificName.speciesNameKey()
    val normalizedCommonName = commonName.orEmpty().speciesNameKey()
    val genericNames = setOf(
        "awaiting identification",
        "unidentified organism",
        "unidentified object",
        "unknown organism",
        "unknown object",
        "not available"
    )
    return normalizedScientificName.isNotBlank() &&
        normalizedScientificName !in genericNames &&
        normalizedCommonName !in genericNames &&
        normalizedScientificName.split(" ").size >= 2
}

internal fun Map<String, String>.toJsonObject(): JsonObject {
    return buildJsonObject {
        forEach { (key, value) -> put(key, value) }
    }
}

private fun String.toDisplayDate(): String {
    return try {
        OffsetDateTime.parse(this).format(DISPLAY_DATE_FORMATTER)
    } catch (_: Throwable) {
        take(10).ifBlank { "Unknown date" }
    }
}

internal const val UNIDENTIFIED_COMMON_NAME = "Unidentified organism"
internal const val AWAITING_IDENTIFICATION = "Awaiting identification"
private const val NOT_ENRICHED = "Not enriched yet"
internal const val FAILED_STATUS = "failed"
private const val NO_ORGANISM_COMMON_NAME = "No organism identified"
private const val IDENTIFICATION_NOT_AVAILABLE = "Not available"
private const val IDENTIFICATION_FAILED_SOURCE = "Identification failed or found no organism."
private const val ENRICHMENT_UNAVAILABLE = "Unavailable until an organism is identified"
private val DISPLAY_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
