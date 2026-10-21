package com.example.driveguard

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import com.example.driveguard.vision.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TrafficLightVisionTest {

    @Test
    fun testStageOnePrioritizesClosestToHorizontalCenterLine() {
        val centerLineX = 300f

        val leftLight = TrafficLightDetection(RectF(20f, 50f, 60f, 150f), 0.85f)      // centerX = 40f
        val centerLight = TrafficLightDetection(RectF(280f, 50f, 320f, 150f), 0.90f)  // centerX = 300f
        val rightLight = TrafficLightDetection(RectF(520f, 50f, 560f, 150f), 0.80f)   // centerX = 540f

        val detections = listOf(leftLight, rightLight, centerLight)
        val selected = StageOneDetector.prioritize(detections, centerLineX)

        assertNotNull(selected)
        assertEquals(centerLight, selected)
    }

    @Test
    fun testStageOneFiltersBelowConfidenceThreshold() {
        val centerLineX = 300f

        // Confidence 0.60 is below the 0.65 (65%) threshold
        val lowConfCenterLight = TrafficLightDetection(RectF(280f, 50f, 320f, 150f), 0.60f)
        val highConfFarLight = TrafficLightDetection(RectF(20f, 50f, 60f, 150f), 0.75f)

        val detections = listOf(lowConfCenterLight, highConfFarLight)
        val selected = StageOneDetector.prioritize(detections, centerLineX)

        assertNotNull(selected)
        assertEquals(highConfFarLight, selected)
    }

    @Test
    fun testStageTwoCoordinateTranslation() {
        // ROI cropped from high-res frame at (x=100, y=200), width=400, height=300
        // ROI image size fed to Stage 1: 400x300
        val relativeBox = RectF(50f, 50f, 150f, 150f)
        val translated = StageTwoClassifier.translateToOriginalFrame(
            relativeBox = relativeBox,
            roiOffsetLeft = 100f,
            roiOffsetTop = 200f,
            roiWidth = 400f,
            roiHeight = 300f,
            roiImageWidth = 400,
            roiImageHeight = 300
        )

        assertEquals(150f, translated.left, 0.01f)  // 100 + 50
        assertEquals(250f, translated.top, 0.01f)   // 200 + 50
        assertEquals(250f, translated.right, 0.01f) // 100 + 150
        assertEquals(350f, translated.bottom, 0.01f)// 200 + 150
    }

    @Test
    fun testStageTwoCropAndResize() {
        val highResFrame = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val cropRect = RectF(100f, 200f, 300f, 400f)

        val cropped = StageTwoClassifier.cropAndResize(highResFrame, cropRect, 64, 64)

        assertNotNull(cropped)
        assertEquals(64, cropped!!.width)
        assertEquals(64, cropped.height)
    }

    @Test
    fun testTemporalSmoothingRequiresThreeConsecutiveFrames() {
        val dispatcher = TemporalSmoothingDispatcher(bufferSize = 3)

        val redResult = ClassificationResult(TrafficLightState.RED, floatArrayOf(0.9f, 0.0f, 0.0f, 0.0f, 0.0f), 0.9f)
        val amberResult = ClassificationResult(TrafficLightState.AMBER, floatArrayOf(0.0f, 0.9f, 0.0f, 0.0f, 0.0f), 0.9f)

        // Frame 1: Red -> null
        assertNull(dispatcher.processFrame(redResult))

        // Frame 2: Red -> null
        assertNull(dispatcher.processFrame(redResult))

        // Frame 3: Red -> Confirmed Red!
        val frame3Output = dispatcher.processFrame(redResult)
        assertNotNull(frame3Output)
        assertEquals("Red", frame3Output!!.active_color)
        assertFalse(frame3Output.is_arrow)
        assertEquals(0.9f, frame3Output.confidence, 0.01f)

        // Frame 4: Amber -> null (state transition not yet confirmed)
        assertNull(dispatcher.processFrame(amberResult))

        // Frame 5: Amber -> null
        assertNull(dispatcher.processFrame(amberResult))

        // Frame 6: Amber -> Confirmed Amber!
        val frame6Output = dispatcher.processFrame(amberResult)
        assertNotNull(frame6Output)
        assertEquals("Amber", frame6Output!!.active_color)
        assertFalse(frame6Output.is_arrow)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testTemporalSmoothingSharedFlowDispatch() = runTest {
        val dispatcher = TemporalSmoothingDispatcher(bufferSize = 3)
        val leftArrowResult = ClassificationResult(
            TrafficLightState.LEFT_ARROW_GREEN,
            floatArrayOf(0.0f, 0.0f, 0.0f, 0.95f, 0.0f),
            0.95f
        )

        dispatcher.processFrame(leftArrowResult)
        dispatcher.processFrame(leftArrowResult)
        dispatcher.processFrame(leftArrowResult)

        val emitted = dispatcher.resultFlow.first()
        assertEquals("Left Arrow Green", emitted.active_color)
        assertTrue(emitted.is_arrow)
        assertEquals(TrafficLightState.LEFT_ARROW_GREEN, emitted.state)
        assertEquals(0.95f, emitted.confidence, 0.01f)
    }
}
