package com.example.biomemo.data

data class BioEntry(
    val id: String,
    val commonName: String,
    val scientificName: String,
    val category: String,
    val date: String,
    val location: String,
    val confidence: Int,
    val notes: String,
    val tags: List<String>
)

data class BioStats(
    val sightings: Int,
    val species: Int,
    val streak: String
)
