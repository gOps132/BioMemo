package com.example.biomemo

import com.example.biomemo.data.SpeciesSearchResult
import com.example.biomemo.data.SpeciesEnrichmentPreview
import com.example.biomemo.screens.species.toSpeciesReferenceDetail
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeciesReferenceDetailTest {
    @Test
    fun usesCommonNameWhenAvailable() {
        val detail = species(commonName = "Philippine Eagle").toSpeciesReferenceDetail()

        assertEquals("Philippine Eagle", detail.title)
        assertEquals("Pithecophaga jefferyi", detail.subtitle)
    }

    @Test
    fun fallsBackToCanonicalNameWhenCommonNameMissing() {
        val detail = species(commonName = null, canonicalName = "Rafflesia schadenbergiana").toSpeciesReferenceDetail()

        assertEquals("Rafflesia schadenbergiana", detail.title)
        assertEquals("Rafflesia schadenbergiana", detail.subtitle)
    }

    @Test
    fun buildsReferenceRowsFromUserFacingSpeciesFields() {
        val detail = species(commonName = "Philippine Eagle").toSpeciesReferenceDetail()

        assertEquals(
            listOf(
                "Common name" to "Philippine Eagle",
                "Scientific name" to "Pithecophaga jefferyi Ogilvie-Grant, 1896",
                "Taxonomy" to "Animalia · Chordata · Aves · Accipitriformes · Accipitridae · Pithecophaga",
                "Habitat" to "Not enriched yet",
                "Diet" to "Not enriched yet",
                "Lifespan" to "Not enriched yet",
                "Distribution" to "Not enriched yet",
                "Conservation status" to "Not enriched yet",
                "Photo source" to "Not enriched yet",
                "Source API" to "GBIF",
                "Last enriched" to "Not enriched yet"
            ),
            detail.rows
        )
    }

    @Test
    fun overlaysEnrichedFieldsWhenAvailable() {
        val detail = species(commonName = "Philippine Eagle").toSpeciesReferenceDetail(
            enrichment = SpeciesEnrichmentPreview(
                commonName = "Philippine Eagle",
                scientificName = "Pithecophaga jefferyi Ogilvie-Grant, 1896",
                taxonomy = "Animalia · Chordata · Aves · Accipitriformes · Accipitridae · Pithecophaga",
                habitat = null,
                diet = "Birds, reptiles, and mammals.",
                lifespan = "Life expectancy is estimated at 30 to 60 years.",
                distribution = "Philippines: Luzon, Leyte, Samar, Mindanao",
                conservationStatus = "critically endangered",
                sourceApi = "GBIF, iNaturalist, Wikipedia",
                lastEnrichedDate = "May 6, 2026",
                photoUrl = "https://static.inaturalist.org/photos/eagle/medium.jpg",
                photoAttribution = "Jane Naturalist",
                photoLicense = "CC-BY-NC",
                photoSource = "iNaturalist"
            )
        )

        assertEquals("https://static.inaturalist.org/photos/eagle/medium.jpg", detail.photoUrl)
        assertEquals("Jane Naturalist · CC-BY-NC · iNaturalist", detail.photoCredit)
        assertEquals(
            listOf(
                "Common name" to "Philippine Eagle",
                "Scientific name" to "Pithecophaga jefferyi Ogilvie-Grant, 1896",
                "Taxonomy" to "Animalia · Chordata · Aves · Accipitriformes · Accipitridae · Pithecophaga",
                "Habitat" to "Not enriched yet",
                "Diet" to "Birds, reptiles, and mammals.",
                "Lifespan" to "Life expectancy is estimated at 30 to 60 years.",
                "Distribution" to "Philippines: Luzon, Leyte, Samar, Mindanao",
                "Conservation status" to "critically endangered",
                "Photo source" to "Jane Naturalist · CC-BY-NC · iNaturalist",
                "Source API" to "GBIF, iNaturalist, Wikipedia",
                "Last enriched" to "May 6, 2026"
            ),
            detail.rows
        )
    }

    private fun species(
        commonName: String?,
        canonicalName: String = "Pithecophaga jefferyi"
    ): SpeciesSearchResult {
        return SpeciesSearchResult(
            gbifUsageKey = 2480381,
            scientificName = "$canonicalName Ogilvie-Grant, 1896",
            canonicalName = canonicalName,
            commonName = commonName,
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
}
