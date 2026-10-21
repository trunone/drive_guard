package com.github.trunone.drive_guard

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
    fun testLightClassifierGreenTurnArrowHorizontal() {
        // Horizontal traffic light box: width 60, height 20 [10, 10, 70, 30]
        val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        // Green left arrow bulb on the left side: x in 10..28, y in 10..30
        for (x in 10 until 28) {
            for (y in 10 until 30) {
                bmp.setPixel(x, y, Color.GREEN)
            }
        }
        val box = RectF(10f, 10f, 70f, 30f)
        val color = LightClassifier.classify(bmp, box)
        assertEquals(LightColor.GREEN_LEFT, color)
    }

    @Test
    fun testHazardAnalyzerGreenLeftArrowHazard() {
        val analyzer = HazardAnalyzer()
        val bmp = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)

        // Green left arrow on horizontal light in frame center
        for (x in 280 until 305) {
            for (y in 50 until 80) {
                bmp.setPixel(x, y, Color.GREEN)
            }
        }
        val tlDet = Det("traffic light", 0.9f, RectF(280f, 50f, 360f, 80f))

        var hazards: List<Hazard> = emptyList()
        for (i in 0 until 4) {
            hazards = analyzer.analyze(bmp, listOf(tlDet), speed = null, nowMs = 1000L + i * 100)
        }

        assertTrue(hazards.any { it.key == "green_left" && it.message == "Green left arrow ahead" })
    }

    @Test
    fun testHazardAnalyzerLeftAndRightTrafficLights() {
        val analyzer = HazardAnalyzer()
        val bmp = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)

        // Fill left light box with green
        for (x in 60 until 120) {
            for (y in 50 until 100) {
                bmp.setPixel(x, y, Color.GREEN)
            }
        }
        val leftTl = Det("traffic light", 0.9f, RectF(60f, 50f, 120f, 100f)) // cx ~ 90/640 = 0.14

        // Feed 4 frames to accumulate stable temporal vote
        var hazards: List<Hazard> = emptyList()
        for (i in 0 until 4) {
            hazards = analyzer.analyze(bmp, listOf(leftTl), speed = null, nowMs = 1000L + i * 100)
        }

        assertTrue(hazards.any { it.key == "green" && it.message.contains("on left") })
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
    fun testLightClassifierYellow() {
        val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        for (x in 10 until 50) {
            for (y in 10 until 50) {
                bmp.setPixel(x, y, Color.YELLOW)
            }
        }
        val box = RectF(10f, 10f, 50f, 50f)
        val color = LightClassifier.classify(bmp, box)
        assertEquals(LightColor.YELLOW, color)
    }

    @Test
    fun testLightClassifierVerticalSpatialWeighting() {
        // Vertical traffic light box [10, 10, 30, 70] (width 20, height 60)
        val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        // Red bulb at top: y in 10..25
        for (x in 10 until 30) {
            for (y in 10 until 25) {
                bmp.setPixel(x, y, Color.RED)
            }
        }
        val box = RectF(10f, 10f, 30f, 70f)
        val color = LightClassifier.classify(bmp, box)
        assertEquals(LightColor.RED, color)
    }

    @Test
    fun testLightClassifierOverexposedBrightCenter() {
        val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        // Bright overexposed red bulb (R: 255, G: 210, B: 210)
        val brightRed = Color.rgb(255, 210, 210)
        for (x in 10 until 50) {
            for (y in 10 until 50) {
                bmp.setPixel(x, y, brightRed)
            }
        }
        val box = RectF(10f, 10f, 50f, 50f)
        val color = LightClassifier.classify(bmp, box)
        assertEquals(LightColor.RED, color)
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

    @Test
    fun testThresholdAdjustmentSafeHeadway() {
        val analyzer = HazardAnalyzer()
        val bmp = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        // Box width producing ~18m distance (gap = 1.8s at speed 10 m/s)
        val carBox = RectF(295.725f, 200f, 344.275f, 384f)
        val det = Det("car", 0.9f, carBox)

        // Default safeHeadwayS = 2.0s, gap = 1.8s -> triggers gap warning
        val hazardsDefault = analyzer.analyze(bmp, listOf(det), speed = 10f, nowMs = 1000L)
        assertTrue(hazardsDefault.any { it.key == "gap" && it.severity == Severity.WARNING })

        // Lower safeHeadwayS to 1.5s -> gap 1.8s is now safe, no gap hazard
        analyzer.safeHeadwayS = 1.5f
        val hazardsModified = analyzer.analyze(bmp, listOf(det), speed = 10f, nowMs = 1000L)
        assertTrue(hazardsModified.none { it.key == "gap" })
    }

    @Test
    fun testThresholdAdjustmentCritHeadway() {
        val analyzer = HazardAnalyzer()
        val bmp = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val carBox = RectF(295.725f, 200f, 344.275f, 384f)
        val det = Det("car", 0.9f, carBox)

        // Default critHeadwayS = 1.0s, gap = 1.8s -> WARNING
        val hazardsDefault = analyzer.analyze(bmp, listOf(det), speed = 10f, nowMs = 1000L)
        assertTrue(hazardsDefault.any { it.key == "gap" && it.severity == Severity.WARNING })

        // Increase critHeadwayS to 2.0s -> gap 1.8s < 2.0s, triggers CRITICAL
        analyzer.critHeadwayS = 2.0f
        val hazardsModified = analyzer.analyze(bmp, listOf(det), speed = 10f, nowMs = 1000L)
        assertTrue(hazardsModified.any { it.key == "gap" && it.severity == Severity.CRITICAL })
    }
}
