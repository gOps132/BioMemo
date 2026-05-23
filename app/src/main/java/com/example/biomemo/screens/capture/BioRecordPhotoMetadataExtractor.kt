package com.example.biomemo.screens.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.biomemo.features.records.domain.BioRecordPhotoMetadata
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
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

    fun originalUri(context: Context, uri: Uri): Uri {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return uri
        val canReadOriginalLocation = context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!canReadOriginalLocation) return uri
        return runCatching { MediaStore.setRequireOriginal(uri) }.getOrDefault(uri)
    }

    fun fromUploadUri(context: Context, uri: Uri, bytes: ByteArray, contentType: String): BioRecordPhotoMetadata {
        val metadata = fromBytes(bytes, contentType)
        if (!metadata.capturedAt.isNullOrBlank()) return metadata

        val mediaStoreCapturedAt = context.mediaStoreDateTaken(uri) ?: return metadata
        return metadata.copy(
            capturedAt = mediaStoreCapturedAt,
            metadataAvailability = metadataAvailability(mediaStoreCapturedAt, metadata.latitude, metadata.longitude),
            raw = metadata.raw + mapOf(
                "captured_at" to mediaStoreCapturedAt,
                "captured_at_source" to "media_store_date_taken"
            )
        )
    }

    fun fromBitmap(bitmap: Bitmap, contentType: String): BioRecordPhotoMetadata {
        val capturedAt = OffsetDateTime.now(ZoneOffset.UTC).toString()
        return BioRecordPhotoMetadata(
            capturedAt = capturedAt,
            width = bitmap.width,
            height = bitmap.height,
            metadataAvailability = "capture time available",
            raw = mapOf(
                "file_type" to contentType,
                "captured_at" to capturedAt,
                "captured_at_source" to "device_capture_time",
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

    private fun Context.mediaStoreDateTaken(uri: Uri): String? {
        val projection = arrayOf(MediaStore.Images.Media.DATE_TAKEN)
        return runCatching {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                if (index < 0 || cursor.isNull(index)) return@use null
                cursor.getLong(index)
                    .takeIf { it > 0L }
                    ?.let { Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toString() }
            }
        }.getOrNull()
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
