package com.example.biomemo

import com.example.biomemo.data.BioRecordPhotoMetadata
import com.example.biomemo.screens.capture.BioRecordLocationMetadataMerger
import com.example.biomemo.screens.capture.BioRecordLocationSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class BioRecordLocationMetadataMergerTest {
    @Test
    fun fillsMissingGpsWithCurrentDeviceLocation() {
        val metadata = BioRecordPhotoMetadata(
            capturedAt = "2026-05-05T10:15:30Z",
            metadataAvailability = "capture time available",
            raw = mapOf("file_type" to "image/jpeg")
        )

        val merged = BioRecordLocationMetadataMerger.withFallbackLocation(
            metadata = metadata,
            location = BioRecordLocationSnapshot(14.5995, 120.9842, "device location")
        )

        assertEquals(14.5995, merged.latitude)
        assertEquals(120.9842, merged.longitude)
        assertEquals("capture time and device GPS available", merged.metadataAvailability)
        assertEquals("14.5995", merged.raw["latitude"])
        assertEquals("120.9842", merged.raw["longitude"])
        assertEquals("device location", merged.raw["location_source"])
    }

    @Test
    fun keepsExifGpsWhenPhotoAlreadyHasCoordinates() {
        val metadata = BioRecordPhotoMetadata(
            latitude = 10.0,
            longitude = 20.0,
            metadataAvailability = "GPS available",
            raw = mapOf("latitude" to "10.0", "longitude" to "20.0")
        )

        val merged = BioRecordLocationMetadataMerger.withFallbackLocation(
            metadata = metadata,
            location = BioRecordLocationSnapshot(14.5995, 120.9842, "device location")
        )

        assertEquals(10.0, merged.latitude)
        assertEquals(20.0, merged.longitude)
        assertEquals("GPS available", merged.metadataAvailability)
        assertEquals(null, merged.raw["location_source"])
    }

    @Test
    fun ignoresNullIslandFallback() {
        val metadata = BioRecordPhotoMetadata(metadataAvailability = "camera capture available")

        val merged = BioRecordLocationMetadataMerger.withFallbackLocation(
            metadata = metadata,
            location = BioRecordLocationSnapshot(0.0, 0.0, "device location")
        )

        assertEquals(null, merged.latitude)
        assertEquals(null, merged.longitude)
        assertEquals("camera capture available", merged.metadataAvailability)
    }
}
