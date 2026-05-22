package com.example.biomemo

import com.example.biomemo.data.BioEntry
import com.example.biomemo.data.BioRecordChangeSource
import com.example.biomemo.data.BioRecordStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class BioRecordStoreTest {
    @Test
    fun observeAllEntriesEmitsInitialAndLocalChanges() = runBlocking {
        val changeSource = FakeChangeSource()
        val loads = mutableListOf(listOf(entry("first")), listOf(entry("second")))
        var loadCount = 0
        val store = BioRecordStore(
            changeSource = changeSource,
            remoteChanges = { MutableSharedFlow<Unit>() },
            loadFreshEntries = {
                loads.removeAt(0)
                    .also { loadCount += 1 }
            }
        )

        val emissions = async {
            store.observeAllEntries()
                .take(2)
                .map { entries -> entries.map { it.id } }
                .toList()
        }
        while (loadCount == 0) yield()

        changeSource.markChanged()
        val collected = withTimeout(5_000) { emissions.await() }

        assertEquals(listOf("first"), collected.first())
        assertEquals(listOf("second"), collected.last())
    }

    @Test
    fun refreshAllEntriesLoadsFreshEntriesAndMarksChanged() = runBlocking {
        val changeSource = FakeChangeSource()
        var loadCount = 0
        val store = BioRecordStore(
            changeSource = changeSource,
            remoteChanges = { MutableSharedFlow<Unit>() },
            loadFreshEntries = {
                loadCount += 1
                listOf(entry("fresh"))
            }
        )

        val entries = store.refreshAllEntries()

        assertEquals(listOf("fresh"), entries.map { it.id })
        assertEquals(1, loadCount)
        assertEquals(1, changeSource.markedChanges)
    }

    private class FakeChangeSource : BioRecordChangeSource {
        private val version = MutableStateFlow(0L)
        var markedChanges = 0
            private set

        override fun versions() = version

        override fun markChanged(): Long {
            markedChanges += 1
            version.value += 1
            return version.value
        }
    }

    private companion object {
        fun entry(id: String) = BioEntry(
            id = id,
            commonName = "Name",
            scientificName = "Scientific name",
            category = "BioRecord",
            date = "May 22, 2026",
            location = "Test Site",
            latitude = null,
            longitude = null,
            confidence = 90,
            notes = "Notes",
            tags = emptyList(),
            userId = "user",
            photoUrl = "",
            sourceType = "upload",
            observedDate = "May 22, 2026",
            savedDate = "May 22, 2026",
            verificationStatus = "identified",
            metadataAvailability = "available",
            taxonomy = "Taxonomy",
            habitat = "Habitat",
            diet = "Diet",
            lifespan = "Lifespan",
            distribution = "Distribution",
            conservationStatus = "Status",
            sourceApi = "Test",
            lastEnrichedDate = "May 22, 2026"
        )
    }
}
