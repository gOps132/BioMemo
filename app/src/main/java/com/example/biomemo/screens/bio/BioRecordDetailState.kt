package com.example.biomemo.screens.bio

object BioRecordDetailState {
    private const val NOT_ENRICHED = "Not enriched yet"
    private const val STATUS_ANALYZING = "analyzing"
    private const val STATUS_FAILED = "failed"
    private const val AWAITING_IDENTIFICATION = "Awaiting identification"
    private const val UNIDENTIFIED_ORGANISM = "Unidentified organism"
    private const val NO_ORGANISM_IDENTIFIED = "No organism identified"
    private val enrichmentLoadingLabels = setOf(
        "Taxonomy",
        "Habitat",
        "Diet",
        "Lifespan",
        "Distribution",
        "Conservation status",
        "Last enriched"
    )

    fun shouldShowEnrichmentLoading(label: String, value: String, verificationStatus: String): Boolean {
        return verificationStatus.equals(STATUS_ANALYZING, ignoreCase = true) &&
            label in enrichmentLoadingLabels &&
            value.trim().equals(NOT_ENRICHED, ignoreCase = true)
    }

    fun shouldShowRetryIdentification(verificationStatus: String, scientificName: String, commonName: String): Boolean {
        if (verificationStatus.equals(STATUS_ANALYZING, ignoreCase = true)) return false
        return verificationStatus.equals(STATUS_FAILED, ignoreCase = true) ||
            scientificName.equals(AWAITING_IDENTIFICATION, ignoreCase = true) ||
            commonName.equals(UNIDENTIFIED_ORGANISM, ignoreCase = true) ||
            commonName.equals(NO_ORGANISM_IDENTIFIED, ignoreCase = true)
    }
}
