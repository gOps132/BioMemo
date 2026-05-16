package com.example.biomemo.screens.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import com.example.biomemo.data.BioRecordPhotoMetadata
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

object BioRecordPhotoMetadataExtractor {
    private val exifDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US)

    fun fromBytes(bytes: ByteArray, contentType: String): BioRecordPhotoMetadata {
        val dimensions = BitmapFactory.Options().run {
            inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, this)
            outWidth.takeIf { it > 0 } to outHeight.takeIf { it > 0 }
        }

        val exif = runCatching { ExifInterface(ByteArrayInputStream(bytes)) }.getOrNull()
        val gps = FloatArray(2)
        val hasGps = exif?.getLatLong(gps) == true
        val capturedAt = exif?.capturedAtIso()
        val orientation = exif
            ?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
            ?.takeIf { it != ExifInterface.ORIENTATION_UNDEFINED }

        val latitude = if (hasGps) gps[0].toDouble() else null
        val longitude = if (hasGps) gps[1].toDouble() else null
        val raw = buildMap {
            put("file_type", contentType)
            capturedAt?.let { put("captured_at", it) }
            latitude?.let { put("latitude", it.toString()) }
            longitude?.let { put("longitude", it.toString()) }
            orientation?.let { put("orientation", it.toString()) }
            dimensions.first?.let { put("width", it.toString()) }
            dimensions.second?.let { put("height", it.toString()) }
        }

        return BioRecordPhotoMetadata(
            capturedAt = capturedAt,
            latitude = latitude,
            longitude = longitude,
            orientation = orientation,
            width = dimensions.first,
            height = dimensions.second,
            metadataAvailability = metadataAvailability(capturedAt, latitude, longitude),
            raw = raw
        )
    }

    fun fromBitmap(bitmap: Bitmap, contentType: String): BioRecordPhotoMetadata {
        return BioRecordPhotoMetadata(
            width = bitmap.width,
            height = bitmap.height,
            metadataAvailability = "camera capture available",
            raw = mapOf(
                "file_type" to contentType,
                "width" to bitmap.width.toString(),
                "height" to bitmap.height.toString()
            )
        )
    }

    private fun ExifInterface.capturedAtIso(): String? {
        val rawDate = getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
            ?: getAttribute(ExifInterface.TAG_DATETIME)
        return rawDate
            ?.let { runCatching { LocalDateTime.parse(it, exifDateFormatter) }.getOrNull() }
            ?.atOffset(ZoneOffset.UTC)
            ?.toString()
    }

    private fun metadataAvailability(capturedAt: String?, latitude: Double?, longitude: Double?): String {
        val hasCapturedAt = !capturedAt.isNullOrBlank()
        val hasGps = latitude != null && longitude != null
        return when {
            hasCapturedAt && hasGps -> "capture time and GPS available"
            hasCapturedAt -> "capture time available"
            hasGps -> "GPS available"
            else -> "unknown"
        }
    }
}
