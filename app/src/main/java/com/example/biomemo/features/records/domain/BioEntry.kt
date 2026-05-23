package com.example.biomemo.features.records.domain

data class BioEntry(
    val id: String,
    val commonName: String,
    val scientificName: String,
    val category: String,
    val date: String,
    val location: String,
    val latitude: Double?,
    val longitude: Double?,
    val confidence: Int,
    val notes: String,
    val tags: List<String>,
    val userId: String,
    val photoUrl: String,
    val sourceType: String,
    val observedDate: String,
    val savedDate: String,
    val verificationStatus: String,
    val metadataAvailability: String,
    val taxonomy: String,
    val habitat: String,
    val diet: String,
    val lifespan: String,
    val distribution: String,
    val conservationStatus: String,
    val sourceApi: String,
    val lastEnrichedDate: String
)

data class BioStats(
    val sightings: Int,
    val species: Int,
    val streak: String
)
