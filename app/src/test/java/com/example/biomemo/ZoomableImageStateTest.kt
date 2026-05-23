package com.example.biomemo

import com.example.biomemo.ui.ZoomableImageState
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomableImageStateTest {
    @Test
    fun scaleClampsToSupportedRange() {
        val state = ZoomableImageState()

        assertEquals(1f, state.zoomedBy(0.2f).scale)
        assertEquals(4f, state.zoomedBy(8f).scale)
    }

    @Test
    fun doubleTapTogglesBetweenFitAndZoomed() {
        val state = ZoomableImageState()

        assertEquals(2f, state.doubleTapped().scale)
        assertEquals(1f, state.doubleTapped().doubleTapped().scale)
    }
}
