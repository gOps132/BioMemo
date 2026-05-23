package com.example.biomemo

import com.example.biomemo.features.records.domain.BioEntry
import com.example.biomemo.data.SpeciesSearchResult
import com.example.biomemo.screens.search.SearchPresenter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchPresenterTest {
    @Test
    fun blankQueryLoadsAllBioRecordsAndSkipsSpeciesSearch() = runBlocking {
        var speciesCalls = 0
        val presenter = SearchPresenter(
            loadBioRecords = { listOf(entry("record-1")) },
            searchBioRecords = { error("Should not search BioRecords for blank query") },
            searchSpecies = {
                speciesCalls += 1
                emptyList()
            },
            loadSuggestions = { listOf("Domestic Dog", "Ladybug") }
        )

        val state = presenter.search("   ")

        assertEquals(listOf("record-1"), state.bioRecords.map { it.id })
        assertTrue(state.speciesResults.isEmpty())
        assertEquals(listOf("Domestic Dog", "Ladybug"), state.suggestions)
        assertEquals(0, speciesCalls)
        assertFalse(state.isSpeciesSearchAvailable)
    }

    @Test
    fun shortQuerySearchesBioRecordsOnly() = runBlocking {
        var speciesCalls = 0
        val presenter = SearchPresenter(
            loadBioRecords = { emptyList() },
            searchBioRecords = { query ->
                assertEquals("ra", query)
                listOf(entry("record-short"))
            },
            searchSpecies = {
                speciesCalls += 1
                emptyList()
            }
        )

        val state = presenter.search("ra")

        assertEquals(listOf("record-short"), state.bioRecords.map { it.id })
        assertTrue(state.speciesResults.isEmpty())
        assertEquals(0, speciesCalls)
        assertFalse(state.isSpeciesSearchAvailable)
    }

    @Test
    fun querySearchesBioRecordsAndSpeciesReferences() = runBlocking {
        val presenter = SearchPresenter(
            loadBioRecords = { emptyList() },
            searchBioRecords = { query ->
                assertEquals("Philippine eagle", query)
                listOf(entry("record-2"))
            },
            searchSpecies = { query ->
                assertEquals("Philippine eagle", query)
                listOf(species("Pithecophaga jefferyi"))
            }
        )

        val state = presenter.search("  Philippine eagle ")

        assertEquals(listOf("record-2"), state.bioRecords.map { it.id })
        assertEquals(listOf("Pithecophaga jefferyi"), state.speciesResults.map { it.canonicalName })
        assertTrue(state.isSpeciesSearchAvailable)
        assertEquals(null, state.speciesError)
    }

    @Test
    fun speciesFailureKeepsBioRecordResultsAndShowsError() = runBlocking {
        val presenter = SearchPresenter(
            loadBioRecords = { emptyList() },
            searchBioRecords = { listOf(entry("record-3")) },
            searchSpecies = { throw IllegalStateException("HTTP 502") }
        )

        val state = presenter.search("Rafflesia")

        assertEquals(listOf("record-3"), state.bioRecords.map { it.id })
        assertTrue(state.speciesResults.isEmpty())
        assertEquals("Species reference search unavailable.", state.speciesError)
    }

    private fun species(canonicalName: String): SpeciesSearchResult {
        return SpeciesSearchResult(
            gbifUsageKey = 2480381,
            scientificName = "$canonicalName Ogilvie-Grant, 1896",
            canonicalName = canonicalName,
            commonName = "Philippine Eagle",
            rank = "SPECIES",
            taxonomicStatus = "ACCEPTED",
            kingdom = "Animalia",
            phylum = "Chordata",
            className = "Aves",
            order = "Accipitriformes",
            family = "Accipitridae",
            genus = "Pithecophaga"
        )
    }

    private fun entry(id: String): BioEntry {
        return BioEntry(
            id = id,
            commonName = "Unidentified organism",
            scientificName = "Awaiting identification",
            category = "BioRecord",
            date = "May 6, 2026",
            location = "Mossy Creek",
            latitude = null,
            longitude = null,
            confidence = 0,
            notes = "Field note",
            tags = listOf("draft"),
            userId = "user-1",
            photoUrl = "",
            sourceType = "camera",
            observedDate = "May 6, 2026",
            savedDate = "May 6, 2026",
            verificationStatus = "draft",
            metadataAvailability = "unknown",
            taxonomy = "Not enriched",
            habitat = "Not enriched",
            diet = "Not enriched",
            lifespan = "Not enriched",
            distribution = "Not enriched",
            conservationStatus = "Not enriched",
            sourceApi = "Pending identification",
            lastEnrichedDate = "Not enriched"
        )
    }
}
