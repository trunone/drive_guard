package com.example.driveguard.sensor

/**
 * Historical horizontal g-force point in the XY plane.
 *
 * @property gx Lateral horizontal g-force (+ right, - left).
 * @property gy Longitudinal horizontal g-force (+ accel forward, - braking backward).
 * @property timestampMs System clock timestamp in milliseconds.
 */
data class GForcePoint(
    val gx: Float,
    val gy: Float,
    val timestampMs: Long
)

/**
 * Data class representing real-time, historical trajectory, and peak g-force readings.
 *
 * @property gx Lateral horizontal g-force (+ right, - left).
 * @property gy Longitudinal horizontal g-force (+ accel forward, - braking backward).
 * @property gz Vertical Z-axis g-force aligned strictly with Earth's gravity direction (~1.0g at rest).
 * @property gXyMag Magnitude of horizontal g-force: sqrt(gx^2 + gy^2).
 * @property peakXy Maximum recorded magnitude of horizontal XY g-force.
 * @property peakZMax Maximum recorded vertical Z-axis g-force.
 * @property peakZMin Minimum recorded vertical Z-axis g-force.
 * @property trajectory History of XY-plane g-force points recorded in the past 10 seconds.
 */
data class GForceData(
    val gx: Float = 0f,
    val gy: Float = 0f,
    val gz: Float = 1.0f,
    val gXyMag: Float = 0f,
    val peakXy: Float = 0f,
    val peakZMax: Float = 1.0f,
    val peakZMin: Float = 1.0f,
    val trajectory: List<GForcePoint> = emptyList()
)
