package com.example.driveguard

import android.view.TextureView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RtspCameraManagerTest {

    @Test
    fun testRtspCameraManagerTimeoutsConfiguration() {
        val context = RuntimeEnvironment.getApplication()
        val textureView = TextureView(context)
        val exec = Executors.newSingleThreadExecutor()

        val manager = RtspCameraManager(
            textureView = textureView,
            executorService = exec,
            onFrameAvailable = {},
            onError = {}
        )

        assertEquals(4000L, manager.watchdogTimeoutMs)
        assertEquals(4000L, manager.bufferingTimeoutMs)

        manager.watchdogTimeoutMs = 3000L
        manager.bufferingTimeoutMs = 5000L

        assertEquals(3000L, manager.watchdogTimeoutMs)
        assertEquals(5000L, manager.bufferingTimeoutMs)

        exec.shutdown()
    }

    @Test
    fun testRtspCameraManagerStopResetsState() {
        val context = RuntimeEnvironment.getApplication()
        val textureView = TextureView(context)
        val exec = Executors.newSingleThreadExecutor()

        val manager = RtspCameraManager(
            textureView = textureView,
            executorService = exec,
            onFrameAvailable = {},
            onError = {}
        )

        manager.stop()
        assertEquals(0L, manager.lastFrameTimeMs)

        exec.shutdown()
    }
}
