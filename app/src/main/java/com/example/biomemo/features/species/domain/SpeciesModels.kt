package com.example.biomemo.features.species.domain

import kotlinx.serialization.Serializable

data class SpeciesSearchResult(
    val gbifUsageKey: Int,
    val scientificName: String,
    val canonicalName: String,
    val commonName: String?,
    val rank: String,
    val taxonomicStatus: String,
    val kingdom: String?,
    val phylum: String?,
    val className: String?,
    val order: String?,
    val family: String?,
    val genus: String?,
    val sourceName: String = "GBIF"
)

@Serializable
data class SpeciesEnrichmentPreview(
    val commonName: String? = null,
    val scientificName: String? = null,
    val taxonomy: String? = null,
    val habitat: String? = null,
    val diet: String? = null,
    val lifespan: String? = null,
    val distribution: String? = null,
    val conservationStatus: String? = null,
    val sourceApi: String? = null,
    val lastEnrichedDate: String? = null,
    val photoUrl: String? = null,
    val photoAttribution: String? = null,
    val photoLicense: String? = null,
    val photoSource: String? = null
)
