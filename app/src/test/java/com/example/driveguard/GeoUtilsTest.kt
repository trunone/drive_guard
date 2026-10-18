package com.example.driveguard

import com.example.driveguard.util.GeoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoUtilsTest {

    @Test
    fun testCalculateBearingNorth() {
        // From (0,0) to (1,0) is Due North (0 degrees)
        val bearing = GeoUtils.calculateBearing(0.0, 0.0, 1.0, 0.0)
        assertEquals(0f, bearing, 0.1f)
    }

    @Test
    fun testCalculateBearingEast() {
        // From (0,0) to (0,1) is Due East (90 degrees)
        val bearing = GeoUtils.calculateBearing(0.0, 0.0, 0.0, 1.0)
        assertEquals(90f, bearing, 0.1f)
    }

    @Test
    fun testCalculateBearingSouth() {
        // From (1,0) to (0,0) is Due South (180 degrees)
        val bearing = GeoUtils.calculateBearing(1.0, 0.0, 0.0, 0.0)
        assertEquals(180f, bearing, 0.1f)
    }

    @Test
    fun testCalculateBearingWest() {
        // From (0,1) to (0,0) is Due West (270 degrees)
        val bearing = GeoUtils.calculateBearing(0.0, 1.0, 0.0, 0.0)
        assertEquals(270f, bearing, 0.1f)
    }

    @Test
    fun testCalculateDistanceMeters() {
        // Amsterdam (52.3676, 4.9041) to Schiphol (52.3105, 4.7683) ~11-12km
        val dist = GeoUtils.calculateDistanceMeters(52.3676, 4.9041, 52.3105, 4.7683)
        assertTrue("Distance should be ~11.2km", dist in 10000.0..13000.0)
    }

    @Test
    fun testCalculateAngularDifferenceNormal() {
        val diff = GeoUtils.calculateAngularDifference(10f, 30f)
        assertEquals(20f, diff, 0.01f)
    }

    @Test
    fun testCalculateAngularDifferenceWrapAround() {
        // 5 degrees and 355 degrees differ by 10 degrees across 0/360 boundary
        val diff = GeoUtils.calculateAngularDifference(5f, 355f)
        assertEquals(10f, diff, 0.01f)
    }

    @Test
    fun testGetBoundingBox() {
        val box = GeoUtils.getBoundingBox(52.0, 5.0, 500.0)
        assertTrue(box.minLat < 52.0)
        assertTrue(box.maxLat > 52.0)
        assertTrue(box.minLon < 5.0)
        assertTrue(box.maxLon > 5.0)
    }
}
