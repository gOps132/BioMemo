package com.example.biomemo

import com.example.biomemo.data.BioRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BioRepositoryTest {
    @Test
    fun searchMatchesCommonNameScientificNameCategoryLocationAndTags() {
        val repository = BioRepository()

        assertEquals("Red Fox", repository.search("vulpes").single().commonName)
        assertEquals("Monarch Butterfly", repository.search("pollinator").single().commonName)
        assertTrue(repository.search("oregon").map { it.commonName }.contains("Red Fox"))
        assertEquals(repository.getAllEntries().size, repository.search("   ").size)
    }

    @Test
    fun statsSummarizeEntries() {
        val stats = BioRepository().getStats()

        assertEquals(7, stats.sightings)
        assertEquals(7, stats.species)
        assertEquals("5d", stats.streak)
    }
}
