package com.example.biomemo.features.records.data

object BioRecordPhotoPath {
    fun forOriginal(userId: String, recordId: String, contentType: String): String {
        val extension = when (contentType.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            else -> "jpg"
        }
        return "$userId/$recordId/original.$extension"
    }
}
