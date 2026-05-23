package com.example.biomemo.features.records.domain

import kotlinx.coroutines.flow.Flow

interface BioRecordRepository {
    suspend fun getAllEntries(): List<BioEntry>
    suspend fun refreshAllEntries(): List<BioEntry>
    fun observeAllEntries(): Flow<List<BioEntry>>
    suspend fun getRecentEntries(limit: Int = 2): List<BioEntry>
    suspend fun getEntryById(id: String): BioEntry?
    fun observeEntryById(id: String): Flow<BioEntry>
    suspend fun getStats(): BioStats
    suspend fun search(query: String): List<BioEntry>
    suspend fun getSearchSuggestions(limit: Int = 8): List<String>
    suspend fun deleteEntries(ids: Collection<String>): Int
    suspend fun createDraftUploadRecord(photo: BioRecordPhotoUpload): BioEntry
    suspend fun retryIdentification(recordId: String): BioEntry?
    suspend fun enrichBioRecordSpecies(recordId: String): BioEntry?
    suspend fun createSignedPhotoUrl(path: String): String
}
