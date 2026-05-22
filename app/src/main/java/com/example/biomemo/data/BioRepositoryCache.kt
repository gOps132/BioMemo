package com.example.biomemo.data

class BioRepositoryCache {
    var bioRecords: List<BioRecordRow>? = null
    var identificationCandidates: List<IdentificationCandidateRow>? = null
    var speciesProfiles: List<SpeciesProfileRow>? = null
    var entries: List<BioEntry>? = null
    val signedPhotoUrls: MutableMap<String, CachedValue<String>> = mutableMapOf()

    companion object {
        val shared = BioRepositoryCache()
    }
}

data class CachedValue<T>(
    val value: T,
    val cachedAtMillis: Long = System.currentTimeMillis()
) {
    fun isFresh(maxAgeMillis: Long = 50 * 60 * 1000L): Boolean {
        return System.currentTimeMillis() - cachedAtMillis < maxAgeMillis
    }
}
