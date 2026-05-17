package com.example.biomemo.screens.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import com.example.biomemo.data.BioRecordPhotoMetadata
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class BioRecordCompressedPhoto(
    val bytes: ByteArray,
    val contentType: String,
    val metadata: BioRecordPhotoMetadata
)

data class BioRecordPhotoResizePlan(
    val width: Int,
    val height: Int
)

data class BioRecordPixelCropBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

data class BioRecordCropBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun clamped(minSize: Float = 0.1f): BioRecordCropBounds {
        var nextLeft = left.coerceIn(0f, 1f)
        var nextTop = top.coerceIn(0f, 1f)
        var nextRight = right.coerceIn(0f, 1f)
        var nextBottom = bottom.coerceIn(0f, 1f)

        if (nextRight - nextLeft < minSize) {
            if (nextLeft + minSize > 1f) {
                nextLeft = (1f - minSize).coerceAtLeast(0f)
                nextRight = 1f
            } else {
                nextRight = nextLeft + minSize
            }
        }
        if (nextBottom - nextTop < minSize) {
            if (nextTop + minSize > 1f) {
                nextTop = (1f - minSize).coerceAtLeast(0f)
                nextBottom = 1f
            } else {
                nextBottom = nextTop + minSize
            }
        }
        return BioRecordCropBounds(nextLeft, nextTop, nextRight, nextBottom)
    }

    fun toPixelBounds(width: Int, height: Int): BioRecordPixelCropBounds {
        val bounds = clamped()
        return BioRecordPixelCropBounds(
            (bounds.left * width).roundToInt().coerceIn(0, width - 1),
            (bounds.top * height).roundToInt().coerceIn(0, height - 1),
            (bounds.right * width).roundToInt().coerceIn(1, width),
            (bounds.bottom * height).roundToInt().coerceIn(1, height)
        )
    }

    fun toBitmapRect(width: Int, height: Int): Rect {
        val bounds = toPixelBounds(width, height)
        return Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
    }

    companion object {
        val Full = BioRecordCropBounds(0f, 0f, 1f, 1f)
    }
}

object BioRecordPhotoCompressor {
    const val outputContentType = "image/jpeg"
    private const val maxLongEdge = 1600
    private const val maxDecodedLongEdge = 3200
    private const val jpegQuality = 85

    fun planFor(width: Int, height: Int): BioRecordPhotoResizePlan {
        if (width <= 0 || height <= 0) return BioRecordPhotoResizePlan(width, height)
        val longEdge = max(width, height)
        if (longEdge <= maxLongEdge) return BioRecordPhotoResizePlan(width, height)

        val scale = maxLongEdge.toFloat() / longEdge.toFloat()
        return BioRecordPhotoResizePlan(
            width = (width * scale).roundToInt().coerceAtLeast(1),
            height = (height * scale).roundToInt().coerceAtLeast(1)
        )
    }

    fun previewBitmapFromBytes(bytes: ByteArray): Bitmap {
        return decodeOrientedBitmap(bytes, maxDecodedLongEdge)
            ?: error("Could not decode selected photo.")
    }

    fun fromBytes(
        bytes: ByteArray,
        contentType: String,
        cropBounds: BioRecordCropBounds = BioRecordCropBounds.Full,
        metadata: BioRecordPhotoMetadata? = null
    ): BioRecordCompressedPhoto {
        val originalMetadata = metadata ?: BioRecordPhotoMetadataExtractor.fromBytes(bytes, contentType)
        val bitmap = decodeOrientedBitmap(bytes, maxDecodedLongEdge)
            ?: error("Could not decode selected photo.")
        return compressBitmap(
            bitmap = bitmap.crop(cropBounds),
            originalMetadata = originalMetadata
        )
    }

    fun fromBitmap(
        bitmap: Bitmap,
        metadata: BioRecordPhotoMetadata,
        cropBounds: BioRecordCropBounds = BioRecordCropBounds.Full
    ): BioRecordCompressedPhoto {
        return compressBitmap(bitmap.crop(cropBounds), metadata)
    }

    private fun compressBitmap(bitmap: Bitmap, originalMetadata: BioRecordPhotoMetadata): BioRecordCompressedPhoto {
        val plan = planFor(bitmap.width, bitmap.height)
        val scaled = if (plan.width == bitmap.width && plan.height == bitmap.height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, plan.width, plan.height, true)
        }

        val compressedBytes = ByteArrayOutputStream().use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, output)
            output.toByteArray()
        }
        val raw = originalMetadata.raw +
            mapOf(
                "file_type" to outputContentType,
                "width" to plan.width.toString(),
                "height" to plan.height.toString()
            ) +
            originalMetadata.width?.let { mapOf("original_width" to it.toString()) }.orEmpty() +
            originalMetadata.height?.let { mapOf("original_height" to it.toString()) }.orEmpty()

        return BioRecordCompressedPhoto(
            bytes = compressedBytes,
            contentType = outputContentType,
            metadata = originalMetadata.copy(
                width = plan.width,
                height = plan.height,
                raw = raw
            )
        )
    }

    private fun decodeOrientedBitmap(bytes: ByteArray, maxLongEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, this)
        }
        val bitmap = BitmapFactory.Options().run {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxLongEdge)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, this)
        }
        return bitmap?.applyExifOrientation(bytes)
    }

    private fun sampleSizeFor(width: Int, height: Int, maxLongEdge: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sampleSize = 1
        var sampledWidth = width
        var sampledHeight = height
        while (max(sampledWidth, sampledHeight) / 2 >= maxLongEdge) {
            sampleSize *= 2
            sampledWidth /= 2
            sampledHeight /= 2
        }
        return sampleSize
    }

    private fun Bitmap.crop(bounds: BioRecordCropBounds): Bitmap {
        val crop = bounds.toBitmapRect(width, height)
        return Bitmap.createBitmap(this, crop.left, crop.top, crop.width().coerceAtLeast(1), crop.height().coerceAtLeast(1))
    }

    private fun Bitmap.applyExifOrientation(bytes: ByteArray): Bitmap {
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> preScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> preScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    postRotate(90f)
                    preScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    postRotate(-90f)
                    preScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(-90f)
            }
        }
        if (matrix.isIdentity) return this
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }
}
