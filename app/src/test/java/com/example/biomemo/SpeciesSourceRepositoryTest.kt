package com.example.biomemo

import com.example.biomemo.data.GbifSpeciesSearchRow
import com.example.biomemo.data.SpeciesEnrichmentPreview
import com.example.biomemo.data.SpeciesSearchResult
import com.example.biomemo.data.SpeciesSourceGateway
import com.example.biomemo.data.SpeciesSourceRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeciesSourceRepositoryTest {
    @Test
    fun searchReturnsAcceptedSpeciesOnlyAndMapsTaxonomy() = runBlocking {
        val repository = SpeciesSourceRepository(
            FakeSpeciesSourceGateway(
                listOf(
                    row(
                        key = 2480381,
                        scientificName = "Pithecophaga jefferyi Ogilvie-Grant, 1896",
                        canonicalName = "Pithecophaga jefferyi",
                        commonName = "Philippine Eagle",
                        rank = "SPECIES",
                        taxonomicStatus = "ACCEPTED",
                        kingdom = "Animalia",
                        phylum = "Chordata",
                        className = "Aves",
                        order = "Accipitriformes",
                        family = "Accipitridae",
                        genus = "Pithecophaga"
                    ),
                    row(
                        key = 319922968,
                        scientificName = "Huia (frog)",
                        rank = "GENUS",
                        taxonomicStatus = "ACCEPTED"
                    )
                )
            )
        )

        val result = repository.searchSpecies(" Philippine eagle ").single()

        assertEquals(2480381, result.gbifUsageKey)
        assertEquals("Pithecophaga jefferyi Ogilvie-Grant, 1896", result.scientificName)
        assertEquals("Pithecophaga jefferyi", result.canonicalName)
        assertEquals("Philippine Eagle", result.commonName)
        assertEquals("SPECIES", result.rank)
        assertEquals("ACCEPTED", result.taxonomicStatus)
        assertEquals("Animalia", result.kingdom)
        assertEquals("Chordata", result.phylum)
        assertEquals("Aves", result.className)
        assertEquals("Accipitriformes", result.order)
        assertEquals("Accipitridae", result.family)
        assertEquals("Pithecophaga", result.genus)
        assertEquals("GBIF", result.sourceName)
    }

    @Test
    fun searchDedupesByAcceptedUsageKey() = runBlocking {
        val repository = SpeciesSourceRepository(
            FakeSpeciesSourceGateway(
                listOf(
                    row(key = 3941113, canonicalName = "Rafflesia schadenbergiana"),
                    row(
                        key = 8560349,
                        acceptedKey = 3941113,
                        canonicalName = "Rafflesia schadenbergiana",
                        taxonomicStatus = "HOMOTYPIC_SYNONYM"
                    ),
                    row(key = 3941113, canonicalName = "Rafflesia schadenbergiana")
                )
            )
        )

        val results = repository.searchSpecies("Rafflesia schadenbergiana")

        assertEquals(listOf(3941113), results.map { it.gbifUsageKey })
    }

    @Test
    fun searchPrefersBackboneNubKeyOverChecklistKey() = runBlocking {
        val repository = SpeciesSourceRepository(
            FakeSpeciesSourceGateway(
                listOf(
                    row(
                        key = 180176554,
                        nubKey = 2480381,
                        scientificName = "Pithecophaga jefferyi Ogilvie-Grant, 1896",
                        canonicalName = "Pithecophaga jefferyi"
                    )
                )
            )
        )

        val result = repository.searchSpecies("Philippine eagle").single()

        assertEquals(2480381, result.gbifUsageKey)
    }

    @Test
    fun searchRanksCommonNameMatchesBeforeScientificNameMatches() = runBlocking {
        val repository = SpeciesSourceRepository(
            FakeSpeciesSourceGateway(
                listOf(
                    row(
                        key = 1001,
                        scientificName = "Mycobacterium phage Giraffe",
                        canonicalName = "Mycobacterium phage Giraffe",
                        commonName = null,
                        kingdom = "Heunggongvirae",
                        phylum = "Uroviricota",
                        className = "Caudoviricetes",
                        order = null,
                        family = null,
                        genus = "Pegunavirus"
                    ),
                    row(
                        key = 2441176,
                        scientificName = "Giraffa camelopardalis Linnaeus, 1758",
                        canonicalName = "Giraffa camelopardalis",
                        commonName = "Giraffe",
                        kingdom = "Animalia",
                        phylum = "Chordata",
                        className = "Mammalia",
                        order = "Artiodactyla",
                        family = "Giraffidae",
                        genus = "Giraffa"
                    )
                )
            )
        )

        val results = repository.searchSpecies("giraffe")

        assertEquals("Giraffa camelopardalis", results.first().canonicalName)
    }

    @Test
    fun searchRanksCommonNamesEndingWithQueryBeforeNamesStartingWithQuery() = runBlocking {
        val repository = SpeciesSourceRepository(
            FakeSpeciesSourceGateway(
                listOf(
                    row(
                        key = 2552247,
                        scientificName = "Peniophora albobadia (Schwein.) Boidin",
                        canonicalName = "Peniophora albobadia",
                        commonName = "Giraffe Spots",
                        kingdom = "Fungi",
                        phylum = "Basidiomycota",
                        className = "Agaricomycetes",
                        order = "Russulales",
                        family = "Peniophoraceae",
                        genus = "Peniophora"
                    ),
                    row(
                        key = 8959277,
                        scientificName = "Giraffa giraffa (von Schreber, 1784)",
                        canonicalName = "Giraffa giraffa",
                        commonName = "Southern Giraffe",
                        kingdom = "Animalia",
                        phylum = "Chordata",
                        className = "Mammalia",
                        order = "Artiodactyla",
                        family = "Giraffidae",
                        genus = "Giraffa"
                    )
                )
            )
        )

        val results = repository.searchSpecies("giraffe")

        assertEquals("Giraffa giraffa", results.first().canonicalName)
    }

    @Test
    fun blankSearchDoesNotCallGateway() = runBlocking {
        val gateway = FakeSpeciesSourceGateway(emptyList())
        val repository = SpeciesSourceRepository(gateway)

        val results = repository.searchSpecies("   ")

        assertTrue(results.isEmpty())
        assertEquals(0, gateway.calls)
    }

    private fun row(
        key: Int,
        acceptedKey: Int? = null,
        nubKey: Int? = null,
        scientificName: String = "Rafflesia schadenbergiana Göpp. ex Hieron.",
        canonicalName: String = "Rafflesia schadenbergiana",
        commonName: String? = null,
        rank: String = "SPECIES",
        taxonomicStatus: String = "ACCEPTED",
        kingdom: String? = "Plantae",
        phylum: String? = "Tracheophyta",
        className: String? = "Magnoliopsida",
        order: String? = "Malpighiales",
        family: String? = "Rafflesiaceae",
        genus: String? = "Rafflesia"
    ): GbifSpeciesSearchRow {
        return GbifSpeciesSearchRow(
            key = key,
            acceptedKey = acceptedKey,
            nubKey = nubKey,
            scientificName = scientificName,
            canonicalName = canonicalName,
            rank = rank,
            taxonomicStatus = taxonomicStatus,
            kingdom = kingdom,
            phylum = phylum,
            className = className,
            order = order,
            family = family,
            genus = genus,
            vernacularNames = commonName?.let { listOf(GbifVernacularNameForTest(it, "eng").toProduction()) }.orEmpty()
        )
    }

    private data class GbifVernacularNameForTest(
        val name: String,
        val language: String
    ) {
        fun toProduction() = com.example.biomemo.data.GbifVernacularName(
            vernacularName = name,
            language = language
        )
    }

    private class FakeSpeciesSourceGateway(
        private val rows: List<GbifSpeciesSearchRow>
    ) : SpeciesSourceGateway {
        var calls = 0

        override suspend fun searchGbifSpecies(query: String): List<GbifSpeciesSearchRow> {
            calls += 1
            return rows
        }

        override suspend fun previewSpeciesEnrichment(species: SpeciesSearchResult): SpeciesEnrichmentPreview {
            return SpeciesEnrichmentPreview()
        }
    }
}
