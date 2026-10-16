package com.example.driveguard

import android.graphics.Bitmap
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ObjectVideoRecorderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dummyBitmap: Bitmap
    private lateinit var fakeEncoder: FakeVideoFrameEncoder

    class FakeVideoFrameEncoder : VideoFrameEncoder {
        var startCount = 0
        var encodeFrameCount = 0
        var stopCount = 0
        var lastOutputFile: File? = null
        var isStarted = false

        override fun start(outputFile: File, width: Int, height: Int) {
            startCount++
            lastOutputFile = outputFile
            isStarted = true
        }

        override fun encodeFrame(bitmap: Bitmap, timestampNs: Long) {
            encodeFrameCount++
        }

        override fun stop() {
            stopCount++
            isStarted = false
        }
    }

    @Before
    fun setUp() {
        dummyBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        fakeEncoder = FakeVideoFrameEncoder()
    }

    @Test
    fun testDefaultTargetObjects() {
        assertEquals(setOf("person", "car"), ObjectVideoRecorder.DEFAULT_TARGET_OBJECTS)
    }

    @Test
    fun testRecordingStartsOnTargetObjectAppearance() {
        val saveDir = tempFolder.newFolder("videos")
        var recordingStateChanged = false
        var recordingPath: String? = null

        val recorder = ObjectVideoRecorder(
            isEnabled = true,
            saveDirectoryPath = saveDir.absolutePath,
            disappearanceTimeoutMs = 1500L,
            encoderFactory = { fakeEncoder },
            onRecordingStateChanged = { isRec, path ->
                recordingStateChanged = isRec
                recordingPath = path
            }
        )

        val personDet = Det("person", 0.9f, RectF(0f, 0f, 50f, 50f))

        recorder.processFrame(dummyBitmap, listOf(personDet), 1000L)

        assertTrue(recorder.isRecording)
        assertTrue(recordingStateChanged)
        assertNotNull(recordingPath)
        assertEquals(1, fakeEncoder.startCount)
        assertEquals(1, fakeEncoder.encodeFrameCount)
        assertNotNull(fakeEncoder.lastOutputFile)
        assertTrue(fakeEncoder.lastOutputFile!!.parentFile!!.absolutePath == saveDir.absolutePath)
    }

    @Test
    fun testRecordingIgnoredWhenDisabled() {
        val saveDir = tempFolder.newFolder("videos")

        val recorder = ObjectVideoRecorder(
            isEnabled = false,
            saveDirectoryPath = saveDir.absolutePath,
            encoderFactory = { fakeEncoder }
        )

        val carDet = Det("car", 0.85f, RectF(0f, 0f, 50f, 50f))

        recorder.processFrame(dummyBitmap, listOf(carDet), 1000L)

        assertFalse(recorder.isRecording)
        assertEquals(0, fakeEncoder.startCount)
    }

    @Test
    fun testRecordingIgnoredForNonTargetObjects() {
        val saveDir = tempFolder.newFolder("videos")

        val recorder = ObjectVideoRecorder(
            isEnabled = true,
            saveDirectoryPath = saveDir.absolutePath,
            encoderFactory = { fakeEncoder }
        )

        val trafficLightDet = Det("traffic light", 0.95f, RectF(0f, 0f, 50f, 50f))
        val stopSignDet = Det("stop sign", 0.95f, RectF(0f, 0f, 50f, 50f))

        recorder.processFrame(dummyBitmap, listOf(trafficLightDet, stopSignDet), 1000L)

        assertFalse(recorder.isRecording)
        assertEquals(0, fakeEncoder.startCount)
    }

    @Test
    fun testRecordingStopsWhenObjectDisappears() {
        val saveDir = tempFolder.newFolder("videos")
        var lastState: Boolean? = null

        val recorder = ObjectVideoRecorder(
            isEnabled = true,
            saveDirectoryPath = saveDir.absolutePath,
            disappearanceTimeoutMs = 1500L,
            encoderFactory = { fakeEncoder },
            onRecordingStateChanged = { isRec, _ -> lastState = isRec }
        )

        val carDet = Det("car", 0.9f, RectF(0f, 0f, 50f, 50f))

        // 1. Car appears at t = 1000ms -> Recording starts
        recorder.processFrame(dummyBitmap, listOf(carDet), 1000L)
        assertTrue(recorder.isRecording)
        assertEquals(1, fakeEncoder.startCount)

        // 2. Car present at t = 2000ms -> Recording continues
        recorder.processFrame(dummyBitmap, listOf(carDet), 2000L)
        assertTrue(recorder.isRecording)

        // 3. Car disappears at t = 2500ms (500ms elapsed < 1500ms timeout) -> Still recording in grace period
        recorder.processFrame(dummyBitmap, emptyList(), 2500L)
        assertTrue(recorder.isRecording)

        // 4. Car still absent at t = 3600ms (1600ms elapsed >= 1500ms timeout) -> Recording stops
        recorder.processFrame(dummyBitmap, emptyList(), 3600L)
        assertFalse(recorder.isRecording)
        assertEquals(1, fakeEncoder.stopCount)
        assertFalse(lastState!!)
    }

    @Test
    fun testDisablingOptionStopsActiveRecording() {
        val saveDir = tempFolder.newFolder("videos")

        val recorder = ObjectVideoRecorder(
            isEnabled = true,
            saveDirectoryPath = saveDir.absolutePath,
            encoderFactory = { fakeEncoder }
        )

        val personDet = Det("person", 0.9f, RectF(0f, 0f, 50f, 50f))

        recorder.processFrame(dummyBitmap, listOf(personDet), 1000L)
        assertTrue(recorder.isRecording)

        // Disable recorder while recording
        recorder.isEnabled = false
        recorder.processFrame(dummyBitmap, listOf(personDet), 1100L)

        assertFalse(recorder.isRecording)
        assertEquals(1, fakeEncoder.stopCount)
    }
}
