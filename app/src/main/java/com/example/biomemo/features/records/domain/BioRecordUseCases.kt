package com.example.biomemo.features.records.domain

import com.example.biomemo.features.records.data.BioRepository
import kotlinx.coroutines.flow.Flow

data class BioRecordUseCases(
    val repository: BioRecordRepository = BioRepository()
) {
    val loadRecords = LoadBioRecords(repository)
    val observeRecords = ObserveBioRecords(repository)
    val refreshRecords = RefreshBioRecords(repository)
    val getRecordDetail = GetBioRecordDetail(repository)
    val observeRecordDetail = ObserveBioRecordDetail(repository)
    val retryIdentification = RetryBioRecordIdentification(repository)
    val deleteRecords = DeleteBioRecords(repository)
    val createDraftRecord = CreateBioRecordDraft(repository)
    val createSignedPhotoUrl = CreateSignedBioRecordPhotoUrl(repository)
    val searchRecords = SearchBioRecords(repository)
    val getSearchSuggestions = GetBioRecordSearchSuggestions(repository)
    val enrichSpecies = EnrichBioRecordSpecies(repository)
}

class LoadBioRecords(private val repository: BioRecordRepository) {
    suspend operator fun invoke(): List<BioEntry> = repository.getAllEntries()
}

class ObserveBioRecords(private val repository: BioRecordRepository) {
    operator fun invoke(): Flow<List<BioEntry>> = repository.observeAllEntries()
}

class RefreshBioRecords(private val repository: BioRecordRepository) {
    suspend operator fun invoke(): List<BioEntry> = repository.refreshAllEntries()
}

class GetBioRecordDetail(private val repository: BioRecordRepository) {
    suspend operator fun invoke(id: String): BioEntry? = repository.getEntryById(id)
}

class ObserveBioRecordDetail(private val repository: BioRecordRepository) {
    operator fun invoke(id: String): Flow<BioEntry> = repository.observeEntryById(id)
}

class RetryBioRecordIdentification(private val repository: BioRecordRepository) {
    suspend operator fun invoke(id: String): BioEntry? = repository.retryIdentification(id)
}

class DeleteBioRecords(private val repository: BioRecordRepository) {
    suspend operator fun invoke(ids: Collection<String>): Int = repository.deleteEntries(ids)
}

class CreateBioRecordDraft(private val repository: BioRecordRepository) {
    suspend operator fun invoke(photo: BioRecordPhotoUpload): BioEntry = repository.createDraftUploadRecord(photo)
}

class CreateSignedBioRecordPhotoUrl(private val repository: BioRecordRepository) {
    suspend operator fun invoke(path: String): String = repository.createSignedPhotoUrl(path)
}

class SearchBioRecords(private val repository: BioRecordRepository) {
    suspend operator fun invoke(query: String): List<BioEntry> = repository.search(query)
}

class GetBioRecordSearchSuggestions(private val repository: BioRecordRepository) {
    suspend operator fun invoke(limit: Int = 8): List<String> = repository.getSearchSuggestions(limit)
}

class EnrichBioRecordSpecies(private val repository: BioRecordRepository) {
    suspend operator fun invoke(recordId: String): BioEntry? = repository.enrichBioRecordSpecies(recordId)
}
