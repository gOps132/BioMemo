package com.example.biomemo

import com.example.biomemo.features.records.domain.BioEntry
import com.example.biomemo.features.records.domain.BioRecordPhotoUpload
import com.example.biomemo.features.records.domain.BioRecordRepository
import com.example.biomemo.features.records.domain.BioRecordUseCases
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class BioRecordUseCasesTest {
    @Test
    fun observeBioRecordsDelegatesToRepositoryStream() = runBlocking {
        val repository = FakeBioRecordRepository()
        val useCases = BioRecordUseCases(repository)
        val expected = listOf(entry("record-1"))

        repository.records.emit(expected)

        assertEquals(expected, useCases.observeRecords().first())
    }

    @Test
    fun writeUseCasesDelegateToRepositoryActions() = runBlocking {
        val repository = FakeBioRecordRepository()
        val useCases = BioRecordUseCases(repository)

        useCases.refreshRecords()
        useCases.deleteRecords(listOf("a", "b"))
        useCases.retryIdentification("retry-id")
        useCases.createSignedPhotoUrl("photo/path.jpg")

        assertEquals(1, repository.refreshCount)
        assertEquals(listOf("a", "b"), repository.deletedIds)
        assertEquals("retry-id", repository.retriedId)
        assertEquals("photo/path.jpg", repository.signedPhotoPath)
    }

    @Test
    fun loadBioRecordsDelegatesToRepositorySnapshot() = runBlocking {
        val repository = FakeBioRecordRepository(snapshot = listOf(entry("snapshot")))
        val useCases = BioRecordUseCases(repository)

        assertEquals(listOf("snapshot"), useCases.loadRecords().map { it.id })
    }

    private class FakeBioRecordRepository(
        private val snapshot: List<BioEntry> = emptyList()
    ) : BioRecordRepository {
        val records = MutableSharedFlow<List<BioEntry>>(replay = 1)
        var refreshCount = 0
        var deletedIds: Collection<String> = emptyList()
        var retriedId: String? = null
        var signedPhotoPath: String? = null

        override suspend fun getAllEntries(): List<BioEntry> = snapshot
        override suspend fun refreshAllEntries(): List<BioEntry> {
            refreshCount += 1
            return emptyList()
        }
        override fun observeAllEntries(): Flow<List<BioEntry>> = records
        override suspend fun getRecentEntries(limit: Int): List<BioEntry> = emptyList()
        override suspend fun getEntryById(id: String): BioEntry? = entry(id)
        override fun observeEntryById(id: String): Flow<BioEntry> = MutableSharedFlow(replay = 1)
        override suspend fun getStats() = com.example.biomemo.data.BioStats(0, 0, "0d")
        override suspend fun search(query: String): List<BioEntry> = emptyList()
        override suspend fun getSearchSuggestions(limit: Int): List<String> = emptyList()
        override suspend fun deleteEntries(ids: Collection<String>): Int {
            deletedIds = ids
            return ids.size
        }
        override suspend fun createDraftUploadRecord(photo: BioRecordPhotoUpload): BioEntry = entry("created")
        override suspend fun retryIdentification(recordId: String): BioEntry? {
            retriedId = recordId
            return entry(recordId)
        }
        override suspend fun enrichBioRecordSpecies(recordId: String): BioEntry? = entry(recordId)
        override suspend fun createSignedPhotoUrl(path: String): String {
            signedPhotoPath = path
            return "signed:$path"
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
