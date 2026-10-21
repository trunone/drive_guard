package com.github.trunone.drive_guard

import com.github.trunone.drive_guard.sensor.GForceData
import com.github.trunone.drive_guard.sensor.GForcePoint
import com.github.trunone.drive_guard.sensor.GForceSensorManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class GForceSensorManagerTest {

    private val gravityEarth = GForceSensorManager.GRAVITY_EARTH // 9.80665

    @Test
    fun testStationaryFlatFaceUp_returns1gZ_and0gXy() {
        // Android standard when flat face up:
        // Accelerometer measures reaction force UP (+9.80665 on Z)
        // Gravity sensor measures vector DOWN (-9.80665 on Z)
        val result = GForceSensorManager.computeGForce(
            ax = 0f, ay = 0f, az = gravityEarth,
            gx = 0f, gy = 0f, gz = -gravityEarth
        )

        assertEquals(1.0f, result.gz, 0.01f)
        assertEquals(0.0f, result.gx, 0.01f)
        assertEquals(0.0f, result.gy, 0.01f)
        assertEquals(0.0f, result.gXyMag, 0.01f)
    }

    @Test
    fun testStationaryTilted45Degrees_returns1gZ_regardlessOfOrientation() {
        // Tilted 45 degrees: accelerometer is (+6.93, 0, +6.93), gravity is (-6.93, 0, -6.93)
        val val45 = (gravityEarth / sqrt(2.0)).toFloat()
        val result = GForceSensorManager.computeGForce(
            ax = val45, ay = 0f, az = val45,
            gx = -val45, gy = 0f, gz = -val45
        )

        assertEquals(1.0f, result.gz, 0.01f)
        assertEquals(0.0f, result.gXyMag, 0.01f)
    }

    @Test
    fun testStationaryLandscapeMode_returns1gZ() {
        // Phone standing on side in landscape mode:
        // Accelerometer (+9.80665 on X), gravity (-9.80665 on X)
        val result = GForceSensorManager.computeGForce(
            ax = gravityEarth, ay = 0f, az = 0f,
            gx = -gravityEarth, gy = 0f, gz = 0f
        )

        assertEquals(1.0f, result.gz, 0.01f)
        assertEquals(0.0f, result.gXyMag, 0.01f)
    }

    @Test
    fun testStationaryUpsideDown_returns1gZ() {
        // Phone upside down: accelerometer (-9.80665 on Z), gravity (+9.80665 on Z)
        val result = GForceSensorManager.computeGForce(
            ax = 0f, ay = 0f, az = -gravityEarth,
            gx = 0f, gy = 0f, gz = gravityEarth
        )

        assertEquals(1.0f, result.gz, 0.01f)
        assertEquals(0.0f, result.gXyMag, 0.01f)
    }

    @Test
    fun testPureHorizontalAcceleration_doesNotAffectZ() {
        // Phone flat face up, accelerating forward (+Y axis) at 0.5g = 4.9033 m/s^2
        val accelFwd = 0.5f * gravityEarth
        val result = GForceSensorManager.computeGForce(
            ax = 0f, ay = accelFwd, az = gravityEarth,
            gx = 0f, gy = 0f, gz = -gravityEarth
        )

        // Z-axis must remain strictly 1.0g
        assertEquals(1.0f, result.gz, 0.01f)
        assertEquals(0.5f, result.gy, 0.01f)
        assertEquals(0.0f, result.gx, 0.01f)
        assertEquals(0.5f, result.gXyMag, 0.01f)
    }

    @Test
    fun testVerticalBumpOnTiltedPhone_increasesZOnly() {
        // Phone tilted 45 degrees, experiences +0.3g vertical bump along UP direction
        val val45 = (gravityEarth / sqrt(2.0)).toFloat()
        // Gravity vector points down (-val45, 0, -val45)
        val gx = -val45
        val gz = -val45
        // Accelerometer with extra 0.3g along UP direction
        val bumpFactor = 1.3f
        val ax = val45 * bumpFactor
        val az = val45 * bumpFactor

        val result = GForceSensorManager.computeGForce(
            ax = ax, ay = 0f, az = az,
            gx = gx, gy = 0f, gz = gz
        )

        assertEquals(1.3f, result.gz, 0.01f)
        assertEquals(0.0f, result.gXyMag, 0.01f)
    }

    @Test
    fun testLateralCornering_calculatesGxAndMag() {
        // Phone flat, turning right at 0.4g lateral force (+X axis)
        val lateralAccel = 0.4f * gravityEarth
        val result = GForceSensorManager.computeGForce(
            ax = lateralAccel, ay = 0f, az = gravityEarth,
            gx = 0f, gy = 0f, gz = -gravityEarth
        )

        assertEquals(1.0f, result.gz, 0.01f)
        assertEquals(0.4f, result.gx, 0.01f)
        assertEquals(0.4f, result.gXyMag, 0.01f)
    }

    @Test
    fun testCalibratedForwardReference_alignsGyToCalibratedDirection() {
        // Phone mounted in custom orientation where vehicle forward corresponds to (+X, +Y, 0)
        val calibRef = floatArrayOf(1f, 1f, 0f)
        val fwdAccel = 0.6f * gravityEarth
        val ax = fwdAccel / sqrt(2f)
        val ay = fwdAccel / sqrt(2f)

        val result = GForceSensorManager.computeGForce(
            ax = ax, ay = ay, az = gravityEarth,
            gx = 0f, gy = 0f, gz = -gravityEarth,
            calibratedRef = calibRef
        )

        assertEquals(1.0f, result.gz, 0.01f)
        assertEquals(0.6f, result.gy, 0.01f)
        assertEquals(0.0f, result.gx, 0.01f)
        assertEquals(0.6f, result.gXyMag, 0.01f)
    }

    @Test
    fun testTrajectoryDataModel_storesTimeAndGForce() {
        val now = 10000L
        val p1 = GForcePoint(gx = 0.1f, gy = 0.2f, timestampMs = now - 5000L)
        val p2 = GForcePoint(gx = 0.3f, gy = 0.4f, timestampMs = now)

        val data = GForceData(trajectory = listOf(p1, p2))

        assertEquals(2, data.trajectory.size)
        assertEquals(0.1f, data.trajectory[0].gx, 0.001f)
        assertEquals(0.3f, data.trajectory[1].gx, 0.001f)
        assertTrue(data.trajectory[1].timestampMs - data.trajectory[0].timestampMs == 5000L)
    }
}
