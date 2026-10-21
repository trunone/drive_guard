package com.github.trunone.drive_guard

import android.location.Location
import com.github.trunone.drive_guard.service.LocationTrackingService
import com.github.trunone.drive_guard.service.RegionBoundaryEvaluator
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocationTrackingServiceTest {

    @Test
    fun testRegionTopicTagComputation() {
        // Hague center (52.0786, 4.3100)
        val tag = RegionBoundaryEvaluator.computeRegionTopicTag(52.0786, 4.3100)
        assertEquals("nl/vri/tile_520_43", tag)
    }

    @Test
    fun testBoundaryCrossingDetection() {
        val loc1 = Location("gps").apply {
            latitude = 52.0786
            longitude = 4.3100
        }

        // Location 2 is nearby within same tile
        val loc2 = Location("gps").apply {
            latitude = 52.0787
            longitude = 4.3101
        }

        assertFalse(RegionBoundaryEvaluator.hasCrossedBoundary(loc2, loc1, 1000f))

        // Location 3 crossed into next tile (52.2, 4.3 -> tile_522_043)
        val loc3 = Location("gps").apply {
            latitude = 52.2000
            longitude = 4.3100
        }

        assertTrue(RegionBoundaryEvaluator.hasCrossedBoundary(loc3, loc1, 1000f))
    }

    @Test
    fun testLocationTrackingServiceMockUpdates() {
        val testLoc = Location("gps").apply {
            latitude = 52.0786
            longitude = 4.3100
        }

        LocationTrackingService.updateMockLocation(testLoc)

        assertEquals(testLoc, LocationTrackingService.locationFlow.value)
        assertEquals("nl/vri/tile_520_43", LocationTrackingService.regionTagFlow.value)
    }
}
