package com.example.biomemo.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object BioImageLoader {
    private const val DEFAULT_TARGET_PX = 320
    private const val CONNECT_TIMEOUT_MS = 7_000
    private const val READ_TIMEOUT_MS = 12_000
    private const val SIGNED_URL_MAX_AGE_MS = 50 * 60 * 1000L
    private val bitmapCache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val signedUrlCache = mutableMapOf<String, CachedUrl>()

    suspend fun loadBitmap(
        photoRef: String,
        targetWidthPx: Int,
        targetHeightPx: Int,
        signedUrlResolver: suspend (String) -> String
    ): Bitmap? {
        val trimmedRef = photoRef.trim()
        if (trimmedRef.isBlank()) return null

        val width = targetWidthPx.takeIf { it > 0 } ?: DEFAULT_TARGET_PX
        val height = targetHeightPx.takeIf { it > 0 } ?: DEFAULT_TARGET_PX
        val cacheKey = "$trimmedRef@$width:$height"
        bitmapCache.get(cacheKey)?.let { return it }

        return withContext(Dispatchers.IO) {
            runCatching {
                val url = resolveUrl(trimmedRef, signedUrlResolver)
                val bytes = downloadBytes(url)
                decodeSampledBitmap(bytes, width, height)?.also { bitmap ->
                    bitmapCache.put(cacheKey, bitmap)
                }
            }.getOrNull()
        }
    }

    private suspend fun resolveUrl(photoRef: String, signedUrlResolver: suspend (String) -> String): String {
        if (photoRef.startsWith("http://") || photoRef.startsWith("https://")) return photoRef

        synchronized(signedUrlCache) {
            signedUrlCache[photoRef]
                ?.takeIf { it.isFresh() }
                ?.let { return it.url }
        }

        val signedUrl = signedUrlResolver(photoRef)
        synchronized(signedUrlCache) {
            signedUrlCache[photoRef] = CachedUrl(signedUrl)
        }
        return signedUrl
    }

    private fun downloadBytes(url: String): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
        }
        return try {
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun decodeSampledBitmap(bytes: ByteArray, targetWidth: Int, targetHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, targetWidth, targetHeight)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
    }

    internal fun calculateInSampleSize(sourceWidth: Int, sourceHeight: Int, targetWidth: Int, targetHeight: Int): Int {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) return 1

        var sampleSize = 1
        val halfWidth = sourceWidth / 2
        val halfHeight = sourceHeight / 2
        while (halfWidth / sampleSize >= targetWidth && halfHeight / sampleSize >= targetHeight) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private data class CachedUrl(
        val url: String,
        val cachedAtMillis: Long = System.currentTimeMillis()
    ) {
        fun isFresh(): Boolean = System.currentTimeMillis() - cachedAtMillis < SIGNED_URL_MAX_AGE_MS
    }
}
