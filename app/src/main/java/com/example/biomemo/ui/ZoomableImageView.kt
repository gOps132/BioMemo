package com.example.biomemo.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

class ZoomableImageView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())
    private val imageBounds = RectF()
    private var bitmap: Bitmap? = null
    private var state = ZoomableImageState()

    fun setBitmap(nextBitmap: Bitmap) {
        bitmap = nextBitmap
        state = ZoomableImageState()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val currentBitmap = bitmap ?: return
        val fitScale = minOf(width / currentBitmap.width.toFloat(), height / currentBitmap.height.toFloat())
        val renderedWidth = currentBitmap.width * fitScale
        val renderedHeight = currentBitmap.height * fitScale
        val left = (width - renderedWidth) / 2f
        val top = (height - renderedHeight) / 2f
        imageBounds.set(left, top, left + renderedWidth, top + renderedHeight)

        canvas.save()
        canvas.translate(width / 2f + state.offsetX, height / 2f + state.offsetY)
        canvas.scale(state.scale, state.scale)
        canvas.translate(-width / 2f, -height / 2f)
        canvas.drawBitmap(currentBitmap, null, imageBounds, paint)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && !containsVisibleImage(event.x, event.y)) {
            return false
        }
        parent.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            parent.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    private fun containsVisibleImage(x: Float, y: Float): Boolean {
        if (bitmap == null) return false
        val scaledLeft = width / 2f + state.offsetX + (imageBounds.left - width / 2f) * state.scale
        val scaledTop = height / 2f + state.offsetY + (imageBounds.top - height / 2f) * state.scale
        val scaledRight = width / 2f + state.offsetX + (imageBounds.right - width / 2f) * state.scale
        val scaledBottom = height / 2f + state.offsetY + (imageBounds.bottom - height / 2f) * state.scale
        return x in scaledLeft..scaledRight && y in scaledTop..scaledBottom
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            state = state.zoomedBy(detector.scaleFactor)
            invalidate()
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onDoubleTap(e: MotionEvent): Boolean {
            state = state.doubleTapped()
            invalidate()
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            state = state.translatedBy(-distanceX, -distanceY)
            invalidate()
            return true
        }
    }
}
