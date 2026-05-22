package com.example.biomemo

import com.example.biomemo.features.species.domain.SpeciesEnrichmentPreview
import com.example.biomemo.features.species.domain.SpeciesRepository
import com.example.biomemo.features.species.domain.SpeciesSearchResult
import com.example.biomemo.features.species.domain.SpeciesUseCases
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeciesUseCasesTest {
    @Test
    fun delegatesSpeciesSearchAndPreviewToRepository() = runBlocking {
        val repository = FakeSpeciesRepository()
        val useCases = SpeciesUseCases(repository)
        val species = species("Giraffa camelopardalis")

        assertEquals(listOf(species), useCases.searchSpecies("giraffe"))
        assertEquals("Giraffa camelopardalis", useCases.previewEnrichment(species).scientificName)
        assertEquals("giraffe", repository.lastQuery)
        assertEquals(species, repository.lastPreviewSpecies)
    }

    private class FakeSpeciesRepository : SpeciesRepository {
        var lastQuery: String? = null
        var lastPreviewSpecies: SpeciesSearchResult? = null

        override suspend fun searchSpecies(query: String): List<SpeciesSearchResult> {
            lastQuery = query
            return listOf(species("Giraffa camelopardalis"))
        }

        override suspend fun previewEnrichment(species: SpeciesSearchResult): SpeciesEnrichmentPreview {
            lastPreviewSpecies = species
            return SpeciesEnrichmentPreview(scientificName = species.scientificName)
        }
    }

    private companion object {
        fun species(name: String) = SpeciesSearchResult(
            gbifUsageKey = 1,
            scientificName = name,
            canonicalName = name,
            commonName = "Giraffe",
            rank = "SPECIES",
            taxonomicStatus = "ACCEPTED",
            kingdom = "Animalia",
            phylum = "Chordata",
            className = "Mammalia",
            order = "Artiodactyla",
            family = "Giraffidae",
            genus = "Giraffa"
        )
    }
}
