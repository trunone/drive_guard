package com.example.driveguard

import com.example.driveguard.data.EnforcementCameraDao
import com.example.driveguard.data.EnforcementCameraEntity
import com.example.driveguard.data.EnforcementCameraType
import com.example.driveguard.repository.LocationRepository
import com.example.driveguard.repository.UserLocation
import com.example.driveguard.ui.CameraAlertViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class FakeCameraDao(private val cameras: List<EnforcementCameraEntity>) : EnforcementCameraDao {
    override suspend fun getCamerasInBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double
    ): List<EnforcementCameraEntity> {
        return cameras.filter {
            it.latitude in minLat..maxLat && it.longitude in minLon..maxLon
        }
    }

    override suspend fun insertAll(cameras: List<EnforcementCameraEntity>) {}
    override suspend fun getAllCameras(): List<EnforcementCameraEntity> = cameras
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CameraAlertViewModelTest {

    @Test
    fun testAlertTriggeredWhenWithin300Meters() = runBlocking {
        // Camera at ~220m distance ahead (latitude 52.002, lon 5.0)
        val camera = EnforcementCameraEntity(
            id = "cam_near",
            latitude = 52.002,
            longitude = 5.0,
            type = EnforcementCameraType.SPEED,
            max_speed = 80
        )
        val fakeDao = FakeCameraDao(listOf(camera))
        val repo = LocationRepository(cameraDao = fakeDao)
        val viewModel = CameraAlertViewModel(repo)

        // User driving North at lat 52.0, lon 5.0 at 20 m/s (72 km/h, under 80 km/h limit)
        val userLoc = UserLocation(latitude = 52.0, longitude = 5.0, bearing = 0f, speedMps = 20f)
        viewModel.processLocationUpdate(userLoc)

        val state = viewModel.alertState.value
        assertNotNull(state)
        assertEquals("cam_near", state?.camera?.id)
        assertTrue("Distance should be < 300m", (state?.distanceMeters ?: 1000.0) < 300.0)
        assertFalse("72 km/h is not exceeding 80 km/h", state?.isCriticalViolation ?: true)
    }

    @Test
    fun testCriticalViolationWhenSpeedExceedsMaxSpeed() = runBlocking {
        val camera = EnforcementCameraEntity(
            id = "cam_speed",
            latitude = 52.001,
            longitude = 5.0,
            type = EnforcementCameraType.SPEED,
            max_speed = 50
        )
        val fakeDao = FakeCameraDao(listOf(camera))
        val repo = LocationRepository(cameraDao = fakeDao)
        val viewModel = CameraAlertViewModel(repo)

        // User driving at 25 m/s = 90 km/h in 50 km/h zone
        val userLoc = UserLocation(latitude = 52.0, longitude = 5.0, bearing = 0f, speedMps = 25f)
        viewModel.processLocationUpdate(userLoc)

        val state = viewModel.alertState.value
        assertNotNull(state)
        assertTrue("90 km/h > 50 km/h limit should trigger critical violation", state?.isCriticalViolation ?: false)
    }

    @Test
    fun testNoAlertWhenDistanceExceeds300Meters() = runBlocking {
        // Camera at ~1100m distance ahead (latitude 52.010, lon 5.0)
        val camera = EnforcementCameraEntity(
            id = "cam_far",
            latitude = 52.010,
            longitude = 5.0,
            type = EnforcementCameraType.SPEED,
            max_speed = 100
        )
        val fakeDao = FakeCameraDao(listOf(camera))
        val repo = LocationRepository(cameraDao = fakeDao)
        val viewModel = CameraAlertViewModel(repo)

        val userLoc = UserLocation(latitude = 52.0, longitude = 5.0, bearing = 0f, speedMps = 20f)
        viewModel.processLocationUpdate(userLoc)

        val state = viewModel.alertState.value
        assertNull("Camera > 300m away should not trigger alert state", state)
    }
}
