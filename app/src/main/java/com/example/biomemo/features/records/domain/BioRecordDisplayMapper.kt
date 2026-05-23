package com.example.biomemo.features.records.domain

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun BioRecord.toBioEntry(): BioEntry {
    val savedLabel = savedAt.toDisplayDate()
    val observedLabel = observedAt?.toDisplayDate() ?: "Not recorded"
    val statusLabel = verificationStatus.ifBlank { "draft" }
    val metadataLabel = metadataAvailability.ifBlank { "unknown" }
    val sourceLabel = sourceType.ifBlank { "unknown" }
    val species = speciesProfile
    val identificationFailed = identification == null && verificationStatus.equals(FAILED_STATUS, ignoreCase = true)
    val commonNameLabel = if (identificationFailed) {
        NO_ORGANISM_COMMON_NAME
    } else {
        species?.commonName?.takeIf { it.isNotBlank() }
            ?: identification?.commonName?.takeIf { it.isNotBlank() }
            ?: UNIDENTIFIED_COMMON_NAME
    }
    val scientificNameLabel = if (identificationFailed) {
        IDENTIFICATION_NOT_AVAILABLE
    } else {
        species?.scientificName?.takeIf { it.isNotBlank() }
            ?: identification?.scientificName?.takeIf { it.isNotBlank() }
            ?: AWAITING_IDENTIFICATION
    }
    val confidenceLabel = if (identificationFailed) 0 else identification?.confidenceScore ?: confidenceScore ?: 0
    val notesLabel = listOfNotNull(
        notes?.takeIf { it.isNotBlank() },
        identification?.reasoning?.takeIf { it.isNotBlank() },
        identification?.visibleTraits?.takeIf { it.isNotBlank() }?.let { "Visible traits: $it" },
        identification?.uncertaintyNotes?.takeIf { it.isNotBlank() }?.let { "Uncertainty: $it" }
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
            } else if (identification == null) {
                "Pending identification"
            } else {
                "OpenAI image identification"
            },
        lastEnrichedDate = species?.lastEnrichedAt?.toDisplayDate() ?: if (identificationFailed) ENRICHMENT_UNAVAILABLE else NOT_ENRICHED
    )
}

private fun String.toDisplayDate(): String {
    return try {
        OffsetDateTime.parse(this).format(DISPLAY_DATE_FORMATTER)
    } catch (_: Throwable) {
        take(10).ifBlank { "Unknown date" }
    }
}

private fun String?.presentOrNull(): String? = this?.takeIf { it.isNotBlank() }

internal const val UNIDENTIFIED_COMMON_NAME = "Unidentified organism"
internal const val AWAITING_IDENTIFICATION = "Awaiting identification"
private const val NOT_ENRICHED = "Not enriched yet"
internal const val FAILED_STATUS = "failed"
private const val NO_ORGANISM_COMMON_NAME = "No organism identified"
private const val IDENTIFICATION_NOT_AVAILABLE = "Not available"
private const val IDENTIFICATION_FAILED_SOURCE = "Identification failed or found no organism."
private const val ENRICHMENT_UNAVAILABLE = "Unavailable until an organism is identified"
private val DISPLAY_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
