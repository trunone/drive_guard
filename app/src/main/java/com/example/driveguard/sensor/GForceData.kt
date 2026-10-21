package com.example.driveguard.sensor

/**
 * Data class representing real-time and peak g-force readings.
 *
 * @property gx Lateral horizontal g-force (+ right, - left).
 * @property gy Longitudinal horizontal g-force (+ accel forward, - braking backward).
 * @property gz Vertical Z-axis g-force aligned strictly with Earth's gravity direction (~1.0g at rest).
 * @property gXyMag Magnitude of horizontal g-force: sqrt(gx^2 + gy^2).
 * @property peakXy Maximum recorded magnitude of horizontal XY g-force.
 * @property peakZMax Maximum recorded vertical Z-axis g-force.
 * @property peakZMin Minimum recorded vertical Z-axis g-force.
 */
data class GForceData(
    val gx: Float = 0f,
    val gy: Float = 0f,
    val gz: Float = 1.0f,
    val gXyMag: Float = 0f,
    val peakXy: Float = 0f,
    val peakZMax: Float = 1.0f,
    val peakZMin: Float = 1.0f
)
