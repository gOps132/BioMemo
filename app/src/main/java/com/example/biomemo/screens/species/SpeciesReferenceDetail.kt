package com.example.biomemo.screens.species

import com.example.biomemo.data.SpeciesSearchResult
import com.example.biomemo.data.SpeciesEnrichmentPreview

data class SpeciesReferenceDetail(
    val title: String,
    val subtitle: String,
    val photoUrl: String? = null,
    val photoCredit: String? = null,
    val rows: List<Pair<String, String>>
)

fun SpeciesSearchResult.toSpeciesReferenceDetail(enrichment: SpeciesEnrichmentPreview? = null): SpeciesReferenceDetail {
    val photoCredit = listOfNotNull(
        enrichment?.photoAttribution.presentOrNull(),
        enrichment?.photoLicense.presentOrNull()?.uppercase(),
        enrichment?.photoSource.presentOrNull()
    ).joinToString(" · ").presentOrNull()

    return SpeciesReferenceDetail(
        title = enrichment?.commonName.presentOrNull() ?: commonName.presentOrNull() ?: canonicalName,
        subtitle = canonicalName,
        photoUrl = enrichment?.photoUrl.presentOrNull(),
        photoCredit = photoCredit,
        rows = listOf(
            "Common name" to (enrichment?.commonName.presentOrNull() ?: commonName.presentOrNull() ?: canonicalName),
            "Scientific name" to (enrichment?.scientificName.presentOrNull() ?: scientificName),
            "Taxonomy" to (enrichment?.taxonomy.presentOrNull() ?: taxonomyLine()),
            "Habitat" to (enrichment?.habitat.presentOrNull() ?: NOT_ENRICHED),
            "Diet" to (enrichment?.diet.presentOrNull() ?: NOT_ENRICHED),
            "Lifespan" to (enrichment?.lifespan.presentOrNull() ?: NOT_ENRICHED),
            "Distribution" to (enrichment?.distribution.presentOrNull() ?: NOT_ENRICHED),
            "Conservation status" to (enrichment?.conservationStatus.presentOrNull() ?: NOT_ENRICHED),
            "Photo source" to (photoCredit ?: NOT_ENRICHED),
            "Source API" to (enrichment?.sourceApi.presentOrNull() ?: sourceName),
            "Last enriched" to (enrichment?.lastEnrichedDate.presentOrNull() ?: NOT_ENRICHED)
        )
    )
}

private fun SpeciesSearchResult.taxonomyLine(): String {
    return listOfNotNull(kingdom, phylum, className, order, family, genus)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .ifBlank { NOT_ENRICHED }
}

private fun String?.presentOrNull(): String? = this?.takeIf { it.isNotBlank() }

private const val NOT_ENRICHED = "Not enriched yet"
