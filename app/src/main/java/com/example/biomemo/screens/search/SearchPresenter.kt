package com.example.biomemo.screens.search

import com.example.biomemo.features.records.domain.BioEntry
import com.example.biomemo.features.species.domain.SpeciesSearchResult

data class SearchUiState(
    val query: String,
    val bioRecords: List<BioEntry>,
    val speciesResults: List<SpeciesSearchResult>,
    val suggestions: List<String>,
    val speciesError: String?,
    val isSpeciesSearchAvailable: Boolean
)

class SearchPresenter(
    private val loadBioRecords: suspend () -> List<BioEntry>,
    private val searchBioRecords: suspend (String) -> List<BioEntry>,
    private val searchSpecies: suspend (String) -> List<SpeciesSearchResult>,
    private val loadSuggestions: suspend () -> List<String> = { emptyList() }
) {
    suspend fun search(rawQuery: String): SearchUiState {
        val query = rawQuery.trim()
        if (query.isEmpty()) {
            return SearchUiState(
                query = query,
                bioRecords = loadBioRecords(),
                speciesResults = emptyList(),
                suggestions = loadSuggestions(),
                speciesError = null,
                isSpeciesSearchAvailable = false
            )
        }

        val bioRecords = searchBioRecords(query)
        if (query.length < MIN_SPECIES_QUERY_LENGTH) {
            return SearchUiState(
                query = query,
                bioRecords = bioRecords,
                speciesResults = emptyList(),
                suggestions = emptyList(),
                speciesError = null,
                isSpeciesSearchAvailable = false
            )
        }

        val species = try {
            searchSpecies(query)
        } catch (_: Throwable) {
            return SearchUiState(
                query = query,
                bioRecords = bioRecords,
                speciesResults = emptyList(),
                suggestions = emptyList(),
                speciesError = "Species reference search unavailable.",
                isSpeciesSearchAvailable = true
            )
        }

        return SearchUiState(
            query = query,
            bioRecords = bioRecords,
            speciesResults = species,
            suggestions = emptyList(),
            speciesError = null,
            isSpeciesSearchAvailable = true
        )
    }

    private companion object {
        const val MIN_SPECIES_QUERY_LENGTH = 3
    }
}
