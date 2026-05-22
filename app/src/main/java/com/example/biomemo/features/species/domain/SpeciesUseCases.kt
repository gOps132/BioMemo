package com.example.biomemo.features.species.domain

import com.example.biomemo.features.species.data.SpeciesSourceRepository

data class SpeciesUseCases(
    val repository: SpeciesRepository = SpeciesSourceRepository()
) {
    val searchSpecies = SearchSpecies(repository)
    val previewEnrichment = PreviewSpeciesEnrichment(repository)
}

class SearchSpecies(private val repository: SpeciesRepository) {
    suspend operator fun invoke(query: String): List<SpeciesSearchResult> = repository.searchSpecies(query)
}

class PreviewSpeciesEnrichment(private val repository: SpeciesRepository) {
    suspend operator fun invoke(species: SpeciesSearchResult): SpeciesEnrichmentPreview = repository.previewEnrichment(species)
}
