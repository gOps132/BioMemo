package com.example.biomemo.screens.capture

import com.example.biomemo.data.BioRecordPhotoMetadata

data class BioRecordLocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val source: String
)

object BioRecordLocationMetadataMerger {
    fun withFallbackLocation(
        metadata: BioRecordPhotoMetadata,
        location: BioRecordLocationSnapshot?
    ): BioRecordPhotoMetadata {
        if (hasValidCoordinates(metadata.latitude, metadata.longitude)) return metadata
        if (location == null || !hasValidCoordinates(location.latitude, location.longitude)) return metadata

        val raw = metadata.raw + mapOf(
            "latitude" to location.latitude.toString(),
            "longitude" to location.longitude.toString(),
            "location_source" to location.source
        )

        return metadata.copy(
            latitude = location.latitude,
            longitude = location.longitude,
            metadataAvailability = metadataAvailability(metadata.capturedAt),
            raw = raw
        )
    }

    private fun metadataAvailability(capturedAt: String?): String {
        return if (capturedAt.isNullOrBlank()) {
            "device GPS available"
        } else {
            "capture time and device GPS available"
        }
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
}
