package com.example.biomemo.data

import kotlinx.coroutines.flow.Flow

interface BioRecordGateway {
    suspend fun fetchBioRecords(limit: Int? = null): List<BioRecordRow>
    suspend fun fetchIdentificationCandidates(): List<IdentificationCandidateRow>
    suspend fun fetchSpeciesProfiles(): List<SpeciesProfileRow>
    suspend fun deleteBioRecords(ids: List<String>, photoPaths: List<String>)
    suspend fun fetchBioRecordById(id: String): BioRecordRow?
    fun observeBioRecord(id: String): Flow<BioRecordRow>
    fun observeBioRecordChanges(): Flow<Unit>
    suspend fun currentUserId(): String?
    suspend fun uploadBioRecordPhoto(path: String, bytes: ByteArray, contentType: String)
    suspend fun insertBioRecordDraft(draft: NewBioRecordDraft): BioRecordRow
    suspend fun insertImageMetadata(metadata: NewImageMetadata)
    suspend fun identifyBioRecordImage(recordId: String): List<IdentificationCandidateRow>
    suspend fun upsertBioRecordSpeciesProfile(profile: BioRecordSpeciesProfileUpsert): SpeciesProfileRow
    suspend fun createSignedPhotoUrl(path: String): String
}
