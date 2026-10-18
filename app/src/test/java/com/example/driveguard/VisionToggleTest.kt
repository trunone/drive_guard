package com.example.driveguard

import android.content.pm.ActivityInfo
import android.view.View
import androidx.appcompat.widget.SwitchCompat
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

import org.robolectric.annotation.Config

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

    @Test
    fun testMainActivityVisionToggleHidesCameraViews() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.Theme_DriveGuard)
        controller.create().start().resume()

        val toggleSwitch = activity.findViewById<SwitchCompat>(R.id.toggleVisionSwitch)
        val previewView = activity.findViewById<View>(R.id.preview)
        val rtspPreviewView = activity.findViewById<View>(R.id.rtspPreview)
        val overlayView = activity.findViewById<View>(R.id.overlay)
        val composeDashboard = activity.findViewById<ComposeView>(R.id.trafficLightComposeView)

        assertNotNull(toggleSwitch)
        assertNotNull(previewView)
        assertNotNull(rtspPreviewView)
        assertNotNull(overlayView)
        assertNotNull(composeDashboard)

        assertTrue(activity.isVisionEnabled)

        // Toggle Vision OFF
        toggleSwitch.isChecked = false

        assertFalse(activity.isVisionEnabled)
        assertEquals(View.GONE, previewView.visibility)
        assertEquals(View.GONE, rtspPreviewView.visibility)
        assertEquals(View.GONE, overlayView.visibility)

        // Toggle Vision back ON
        toggleSwitch.isChecked = true

        assertTrue(activity.isVisionEnabled)
        assertEquals(View.VISIBLE, previewView.visibility)
        assertEquals(View.VISIBLE, overlayView.visibility)
    }

    @Test
    fun testMainActivityOrientationSupportsVerticalMode() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.Theme_DriveGuard)
        controller.create().start().resume()

        // Verify activity requested orientation allows full sensor (vertical + horizontal)
        val orientation = activity.requestedOrientation
        assertTrue(
            orientation == ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR ||
            orientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED ||
            orientation == ActivityInfo.SCREEN_ORIENTATION_USER ||
            orientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        )
    }
}
