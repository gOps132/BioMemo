package com.example.biomemo.screens.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max

class CropPhotoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadePaint = Paint().apply { color = Color.argb(150, 0, 0, 0) }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val imageRect = RectF()
    private val cropRect = RectF()
    private var bitmap: Bitmap? = null
    private var dragMode = DragMode.NONE
    private var lastX = 0f
    private var lastY = 0f
    private val hitSize = dp(36f)
    private val handleSize = dp(9f)
    private val minCropSize = dp(92f)

    fun setBitmap(nextBitmap: Bitmap) {
        bitmap = nextBitmap
        configureImageRect(width, height)
        invalidate()
    }

    fun cropBounds(): BioRecordCropBounds {
        if (imageRect.width() <= 0f || imageRect.height() <= 0f) return BioRecordCropBounds.Full
        return BioRecordCropBounds(
            left = ((cropRect.left - imageRect.left) / imageRect.width()).coerceIn(0f, 1f),
            top = ((cropRect.top - imageRect.top) / imageRect.height()).coerceIn(0f, 1f),
            right = ((cropRect.right - imageRect.left) / imageRect.width()).coerceIn(0f, 1f),
            bottom = ((cropRect.bottom - imageRect.top) / imageRect.height()).coerceIn(0f, 1f)
        ).clamped()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        configureImageRect(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val currentBitmap = bitmap ?: return
        canvas.drawColor(Color.rgb(7, 24, 15))
        canvas.drawBitmap(currentBitmap, null, imageRect, bitmapPaint)
        drawOverlay(canvas)
        canvas.drawRect(cropRect, borderPaint)
        drawHandles(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bitmap == null || imageRect.isEmpty) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                dragMode = hitMode(event.x, event.y)
                lastX = event.x
                lastY = event.y
                return dragMode != DragMode.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                moveCrop(dx, dy)
                lastX = event.x
                lastY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragMode = DragMode.NONE
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    private fun configureImageRect(viewWidth: Int, viewHeight: Int) {
        val currentBitmap = bitmap ?: return
        if (viewWidth <= 0 || viewHeight <= 0) return
        val scale = minOf(viewWidth / currentBitmap.width.toFloat(), viewHeight / currentBitmap.height.toFloat())
        val imageWidth = currentBitmap.width * scale
        val imageHeight = currentBitmap.height * scale
        val left = (viewWidth - imageWidth) / 2f
        val top = (viewHeight - imageHeight) / 2f
        imageRect.set(left, top, left + imageWidth, top + imageHeight)

        if (cropRect.isEmpty) {
            val insetX = imageRect.width() * 0.06f
            val insetY = imageRect.height() * 0.06f
            cropRect.set(imageRect.left + insetX, imageRect.top + insetY, imageRect.right - insetX, imageRect.bottom - insetY)
        } else {
            cropRect.intersect(imageRect)
        }
    }

    private fun drawOverlay(canvas: Canvas) {
        canvas.drawRect(imageRect.left, imageRect.top, imageRect.right, cropRect.top, shadePaint)
        canvas.drawRect(imageRect.left, cropRect.bottom, imageRect.right, imageRect.bottom, shadePaint)
        canvas.drawRect(imageRect.left, cropRect.top, cropRect.left, cropRect.bottom, shadePaint)
        canvas.drawRect(cropRect.right, cropRect.top, imageRect.right, cropRect.bottom, shadePaint)
    }

    private fun drawHandles(canvas: Canvas) {
        listOf(
            cropRect.left to cropRect.top,
            cropRect.right to cropRect.top,
            cropRect.left to cropRect.bottom,
            cropRect.right to cropRect.bottom
        ).forEach { (x, y) ->
            canvas.drawCircle(x, y, handleSize, handlePaint)
        }
    }

    private fun hitMode(x: Float, y: Float): DragMode {
        return when {
            near(x, y, cropRect.left, cropRect.top) -> DragMode.TOP_LEFT
            near(x, y, cropRect.right, cropRect.top) -> DragMode.TOP_RIGHT
            near(x, y, cropRect.left, cropRect.bottom) -> DragMode.BOTTOM_LEFT
            near(x, y, cropRect.right, cropRect.bottom) -> DragMode.BOTTOM_RIGHT
            cropRect.contains(x, y) -> DragMode.MOVE
            else -> DragMode.NONE
        }
    }

    private fun moveCrop(dx: Float, dy: Float) {
        when (dragMode) {
            DragMode.MOVE -> {
                val clampedDx = dx.coerceIn(imageRect.left - cropRect.left, imageRect.right - cropRect.right)
                val clampedDy = dy.coerceIn(imageRect.top - cropRect.top, imageRect.bottom - cropRect.bottom)
                cropRect.offset(clampedDx, clampedDy)
            }
            DragMode.TOP_LEFT -> {
                cropRect.left = (cropRect.left + dx).coerceIn(imageRect.left, cropRect.right - minCropSize)
                cropRect.top = (cropRect.top + dy).coerceIn(imageRect.top, cropRect.bottom - minCropSize)
            }
            DragMode.TOP_RIGHT -> {
                cropRect.right = (cropRect.right + dx).coerceIn(cropRect.left + minCropSize, imageRect.right)
                cropRect.top = (cropRect.top + dy).coerceIn(imageRect.top, cropRect.bottom - minCropSize)
            }
            DragMode.BOTTOM_LEFT -> {
                cropRect.left = (cropRect.left + dx).coerceIn(imageRect.left, cropRect.right - minCropSize)
                cropRect.bottom = (cropRect.bottom + dy).coerceIn(cropRect.top + minCropSize, imageRect.bottom)
            }
            DragMode.BOTTOM_RIGHT -> {
                cropRect.right = (cropRect.right + dx).coerceIn(cropRect.left + minCropSize, imageRect.right)
                cropRect.bottom = (cropRect.bottom + dy).coerceIn(cropRect.top + minCropSize, imageRect.bottom)
            }
            DragMode.NONE -> Unit
        }
    }

    private fun near(x: Float, y: Float, targetX: Float, targetY: Float): Boolean {
        return max(abs(x - targetX), abs(y - targetY)) <= hitSize
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private enum class DragMode {
        NONE,
        MOVE,
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }
}
