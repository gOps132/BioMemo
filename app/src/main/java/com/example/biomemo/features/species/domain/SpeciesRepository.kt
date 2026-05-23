package com.example.biomemo.features.species.domain

interface SpeciesRepository {
    suspend fun searchSpecies(query: String): List<SpeciesSearchResult>
    suspend fun previewEnrichment(species: SpeciesSearchResult): SpeciesEnrichmentPreview
}
