package com.example.biomemo.screens.map

import com.example.biomemo.features.records.domain.BioEntry
import java.util.Locale

data class BioMapUiState(
    val totalRecords: Int,
    val recordsWithoutLocation: Int,
    val pins: List<BioMapPin>,
    val summary: String,
    val emptyTitle: String?,
    val emptyMessage: String?
)

data class BioMapPin(
    val id: String,
    val commonName: String,
    val scientificName: String,
    val category: String,
    val photoUrl: String,
    val latitude: Double,
    val longitude: Double,
    val primaryMetadata: String,
    val locationMetadata: String,
    val tagsLabel: String
)

object BioMapModel {
    fun fromEntries(entries: List<BioEntry>): BioMapUiState {
        val pins = entries.mapNotNull { entry ->
            val latitude = entry.latitude
            val longitude = entry.longitude
            if (!hasValidCoordinates(latitude, longitude)) return@mapNotNull null
            val validLatitude = latitude ?: return@mapNotNull null
            val validLongitude = longitude ?: return@mapNotNull null

            BioMapPin(
                id = entry.id,
                commonName = entry.commonName,
                scientificName = entry.scientificName,
                category = entry.category,
                photoUrl = entry.photoUrl,
                latitude = validLatitude,
                longitude = validLongitude,
                primaryMetadata = "${entry.location.locationLabel()} · ${entry.date} · ${entry.confidence}% ID confidence",
                locationMetadata = "${entry.metadataAvailability.locationMetadataLabel()} · ${coordinateLabel(validLatitude, validLongitude)}",
                tagsLabel = entry.tags.tagsLabel()
            )
        }
        val recordsWithoutLocation = entries.size - pins.size
        val emptyCopy = emptyCopy(entries.size, recordsWithoutLocation, pins.size)

        return BioMapUiState(
            totalRecords = entries.size,
            recordsWithoutLocation = recordsWithoutLocation,
            pins = pins,
            summary = summary(pins.size, recordsWithoutLocation, entries.size),
            emptyTitle = emptyCopy?.first,
            emptyMessage = emptyCopy?.second
        )
    }

    private fun hasValidCoordinates(latitude: Double?, longitude: Double?): Boolean {
        return latitude != null &&
            longitude != null &&
            !latitude.isNaN() &&
            !longitude.isNaN() &&
            !latitude.isInfinite() &&
            !longitude.isInfinite() &&
            latitude in -90.0..90.0 &&
            longitude in -180.0..180.0 &&
            !(latitude == 0.0 && longitude == 0.0)
    }

    private fun summary(pinCount: Int, recordsWithoutLocation: Int, totalRecords: Int): String {
        if (totalRecords == 0) return "No mapped BioRecords yet"
        if (pinCount == 0) return "No mapped BioRecords · $recordsWithoutLocation missing GPS"
        if (recordsWithoutLocation == 0) {
            return "$pinCount mapped · all BioRecords have GPS"
        }
        return "$pinCount mapped · $recordsWithoutLocation missing GPS"
    }

    private fun emptyCopy(totalRecords: Int, recordsWithoutLocation: Int, pinCount: Int): Pair<String, String>? {
        if (pinCount > 0) return null
        if (totalRecords == 0) {
            return "No BioRecords yet" to "Capture BioRecords with location enabled to build your map."
        }
        val recordLabel = if (recordsWithoutLocation == 1) "record needs" else "records need"
        return "No mapped BioRecords" to "$recordsWithoutLocation $recordLabel usable GPS metadata before they can appear here."
    }

    private fun coordinateLabel(latitude: Double, longitude: Double): String {
        return String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
    }

    private fun String.locationLabel(): String = ifBlank { "Unknown location" }

    private fun String.locationMetadataLabel(): String = ifBlank { "GPS coordinates available" }

    private fun List<String>.tagsLabel(): String = map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase(Locale.US) }
        .joinToString(" · ")
        .ifBlank { "No tags" }
}
