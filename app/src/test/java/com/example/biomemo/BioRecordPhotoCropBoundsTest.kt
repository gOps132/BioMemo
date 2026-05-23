package com.example.biomemo

import com.example.biomemo.screens.capture.BioRecordCropBounds
import com.example.biomemo.screens.capture.BioRecordPhotoCompressor
import org.junit.Assert.assertEquals
import org.junit.Test

class BioRecordPhotoCropBoundsTest {
    @Test
    fun bitmapRectMapsNormalizedCropToPixelBounds() {
        val rect = BioRecordCropBounds(left = 0.25f, top = 0.1f, right = 0.75f, bottom = 0.6f)
            .toPixelBounds(width = 4000, height = 3000)

        assertEquals(1000, rect.left)
        assertEquals(300, rect.top)
        assertEquals(3000, rect.right)
        assertEquals(1800, rect.bottom)
    }

    @Test
    fun cropBoundsClampToImageAndKeepMinimumSize() {
        val bounds = BioRecordCropBounds(left = -0.4f, top = 0.95f, right = 1.4f, bottom = 0.97f)
            .clamped()

        assertEquals(0f, bounds.left)
        assertEquals(0.9f, bounds.top)
        assertEquals(1f, bounds.right)
        assertEquals(1f, bounds.bottom)
    }

    @Test
    fun resizePlanCapsCroppedPhotoLongestEdge() {
        val plan = BioRecordPhotoCompressor.planFor(width = 2400, height = 1800)

        assertEquals(1600, plan.width)
        assertEquals(1200, plan.height)
        assertEquals("image/jpeg", BioRecordPhotoCompressor.outputContentType)
    }
}
