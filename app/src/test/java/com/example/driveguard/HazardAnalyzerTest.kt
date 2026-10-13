package com.example.driveguard

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HazardAnalyzerTest {

    @Test
    fun testLightClassifierRed() {
        val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        // Draw bright red inside rect [10, 10, 50, 50]
        for (x in 10 until 50) {
            for (y in 10 until 50) {
                bmp.setPixel(x, y, Color.RED)
            }
        }
        val box = RectF(10f, 10f, 50f, 50f)
        val color = LightClassifier.classify(bmp, box)
        assertEquals(LightColor.RED, color)
    }

    @Test
    fun testLightClassifierGreen() {
        val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        for (x in 10 until 50) {
            for (y in 10 until 50) {
                bmp.setPixel(x, y, Color.GREEN)
            }
        }
        val box = RectF(10f, 10f, 50f, 50f)
        val color = LightClassifier.classify(bmp, box)
        assertEquals(LightColor.GREEN, color)
    }

    @Test
    fun testLightClassifierBoundaryTopRightCoercion() {
        val bmp = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        // Rect near bottom-right edge of bitmap
        val box = RectF(80f, 180f, 100f, 200f)
        // This used to trigger an invalid height coercion bug in LightClassifier when top was coerced against width - 2 instead of height - 2
        val color = LightClassifier.classify(bmp, box)
        assertEquals(LightColor.UNKNOWN, color)
    }

    @Test
    fun testHazardAnalyzerCarCloseAhead() {
        val analyzer = HazardAnalyzer()
        val bmp = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        // Car centered in ego lane: cx = 0.5, bottom = 0.8 * h
        val carBox = RectF(200f, 200f, 440f, 384f)
        val det = Det("car", 0.9f, carBox)

        val hazards = analyzer.analyze(bmp, listOf(det), speed = null, nowMs = 1000L)
        assertTrue(hazards.any { it.key == "gap" })
    }

    @Test
    fun testHazardAnalyzerZeroWidthBoxDoesNotCrash() {
        val analyzer = HazardAnalyzer()
        val bmp = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val zeroWidthCar = RectF(320f, 200f, 320f, 384f)
        val det = Det("car", 0.9f, zeroWidthCar)

        val hazards = analyzer.analyze(bmp, listOf(det), speed = null, nowMs = 1000L)
        assertTrue(hazards.none { it.key == "gap" || it.key == "ttc" })
    }
}
