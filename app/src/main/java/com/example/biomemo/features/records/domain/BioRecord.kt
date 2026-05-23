package com.example.biomemo.features.records.domain

data class BioRecord(
    val id: String,
    val userId: String,
    val photoUrl: String?,
    val thumbnailUrl: String?,
    val sourceType: String,
    val observedAt: String?,
    val savedAt: String,
    val latitude: Double?,
    val longitude: Double?,
    val locationLabel: String,
    val notes: String?,
    val confidenceScore: Int?,
    val verificationStatus: String,
    val metadataAvailability: String,
    val identification: BioRecordIdentification?,
    val speciesProfile: BioRecordSpeciesProfile?
)

data class BioRecordIdentification(
    val commonName: String?,
    val scientificName: String,
    val confidenceScore: Int?,
    val reasoning: String?,
    val visibleTraits: String?,
    val uncertaintyNotes: String?
)

data class BioRecordSpeciesProfile(
    val id: String,
    val commonName: String,
    val scientificName: String,
    val taxonomy: String?,
    val habitat: String?,
    val diet: String?,
    val lifespan: String?,
    val distribution: String?,
    val conservationStatus: String?,
    val sourceApi: String?,
    val lastEnrichedAt: String?
)
