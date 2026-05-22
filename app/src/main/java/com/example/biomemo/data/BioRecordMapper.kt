package com.example.biomemo.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun BioRecordRow.toBioRecord(
    candidate: IdentificationCandidateRow? = null,
    speciesProfile: SpeciesProfileRow? = null
): BioRecord {
    return BioRecord(
        id = id,
        userId = userId,
        photoUrl = photoUrl,
        thumbnailUrl = thumbnailUrl,
        sourceType = sourceType,
        observedAt = observedAt,
        savedAt = savedAt,
        latitude = latitude,
        longitude = longitude,
        locationLabel = locationLabel,
        notes = notes,
        confidenceScore = confidenceScore,
        verificationStatus = verificationStatus,
        metadataAvailability = metadataAvailability,
        identification = candidate?.toDomainIdentification(),
        speciesProfile = (speciesProfile ?: this.speciesProfile)?.toDomainSpeciesProfile()
    )
}

private fun IdentificationCandidateRow.toDomainIdentification(): BioRecordIdentification {
    return BioRecordIdentification(
        commonName = commonName,
        scientificName = scientificName,
        confidenceScore = confidenceScore,
        reasoning = reasoning,
        visibleTraits = visibleTraits,
        uncertaintyNotes = uncertaintyNotes
    )
}

private fun SpeciesProfileRow.toDomainSpeciesProfile(): BioRecordSpeciesProfile {
    return BioRecordSpeciesProfile(
        id = id,
        commonName = commonName,
        scientificName = scientificName,
        taxonomy = taxonomy,
        habitat = habitat,
        diet = diet,
        lifespan = lifespan,
        distribution = distribution,
        conservationStatus = conservationStatus,
        sourceApi = sourceApi,
        lastEnrichedAt = lastEnrichedAt
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
