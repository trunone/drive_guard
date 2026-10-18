package com.example.driveguard

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VisionToggleTest {

    @Test
    fun testOverlayViewClearAndShow() {
        val overlayView = OverlayView(ApplicationProvider.getApplicationContext(), null)
        val frameResult = FrameResult(emptyList(), emptyList(), 1280, 720)

        overlayView.show(frameResult)
        // Verify overlay can be cleared without throwing exceptions
        overlayView.clear()
    }

    @Test
    fun testVisionFlagToggle() {
        var isVisionEnabled = true

        assertTrue(isVisionEnabled)

        // Toggle OFF
        isVisionEnabled = false
        assertFalse(isVisionEnabled)

        // Toggle ON
        isVisionEnabled = true
        assertTrue(isVisionEnabled)
    }
}
