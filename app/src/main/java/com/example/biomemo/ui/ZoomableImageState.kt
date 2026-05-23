package com.example.biomemo.ui

data class ZoomableImageState(
    val scale: Float = MIN_SCALE,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
) {
    fun zoomedBy(factor: Float): ZoomableImageState {
        return copy(scale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE))
    }

    fun doubleTapped(): ZoomableImageState {
        return if (scale > MIN_SCALE) copy(scale = MIN_SCALE, offsetX = 0f, offsetY = 0f) else copy(scale = DOUBLE_TAP_SCALE)
    }

    fun translatedBy(dx: Float, dy: Float): ZoomableImageState {
        if (scale <= MIN_SCALE) return copy(offsetX = 0f, offsetY = 0f)
        return copy(offsetX = offsetX + dx, offsetY = offsetY + dy)
    }

    companion object {
        const val MIN_SCALE = 1f
        const val DOUBLE_TAP_SCALE = 2f
        const val MAX_SCALE = 4f
    }
}
