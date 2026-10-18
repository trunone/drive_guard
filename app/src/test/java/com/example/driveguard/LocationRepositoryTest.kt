package com.example.driveguard

import com.example.driveguard.data.EnforcementCameraEntity
import com.example.driveguard.data.EnforcementCameraType
import com.example.driveguard.repository.LocationRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationRepositoryTest {

    private val repo = LocationRepository()

    @Test
    fun testDirectionalFilteringIncludesAlignedCamera() {
        val userLat = 52.0
        val userLon = 5.0
        val userBearing = 0f // Driving North

        // Camera directly north (latitude 52.002, longitude 5.0) -> bearing ~0 deg
        val cameraNorth = EnforcementCameraEntity(
            id = "cam_north",
            latitude = 52.002,
            longitude = 5.0,
            type = EnforcementCameraType.SPEED,
            max_speed = 80
        )

        val filtered = repo.filterCamerasByDirection(
            userLat = userLat,
            userLon = userLon,
            userBearing = userBearing,
            cameras = listOf(cameraNorth),
            toleranceDegrees = 30f
        )

        assertEquals(1, filtered.size)
        assertEquals("cam_north", filtered[0].camera.id)
    }

    @Test
    fun testDirectionalFilteringExcludesOppositeCamera() {
        val userLat = 52.0
        val userLon = 5.0
        val userBearing = 0f // Driving North

        // Camera to the south (latitude 51.998, longitude 5.0) -> target bearing ~180 deg
        val cameraSouth = EnforcementCameraEntity(
            id = "cam_south",
            latitude = 51.998,
            longitude = 5.0,
            type = EnforcementCameraType.SPEED,
            max_speed = 80
        )

        val filtered = repo.filterCamerasByDirection(
            userLat = userLat,
            userLon = userLon,
            userBearing = userBearing,
            cameras = listOf(cameraSouth),
            toleranceDegrees = 30f
        )

        assertTrue(filtered.isEmpty())
    }

    @Test
    fun testDirectionalFilteringWrapAroundBoundary() {
        val userLat = 52.0
        val userLon = 5.0
        val userBearing = 355f // Driving almost North (355 deg)

        // Camera slightly east of North (target bearing ~5 deg) -> diff = 10 deg <= 30 deg
        val cameraNorthEast = EnforcementCameraEntity(
            id = "cam_ne",
            latitude = 52.002,
            longitude = 5.0001,
            type = EnforcementCameraType.RED_LIGHT,
            max_speed = 50
        )

        val filtered = repo.filterCamerasByDirection(
            userLat = userLat,
            userLon = userLon,
            userBearing = userBearing,
            cameras = listOf(cameraNorthEast),
            toleranceDegrees = 30f
        )

        assertEquals(1, filtered.size)
    }
}
