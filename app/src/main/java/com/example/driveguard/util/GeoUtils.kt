package com.example.driveguard.util

import kotlin.math.*

data class BoundingBox(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
)

object GeoUtils {
    /**
     * Calculates the initial bearing (in degrees, 0..360) from (lat1, lon1) to (lat2, lon2).
     */
    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val initialBearing = Math.toDegrees(atan2(y, x))
        return ((initialBearing + 360.0) % 360.0).toFloat()
    }

    /**
     * Calculates distance in meters between two lat/lon coordinates using the Haversine formula.
     */
    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /**
     * Calculates absolute angular difference (0..180 degrees) between two bearings.
     */
    fun calculateAngularDifference(bearing1: Float, bearing2: Float): Float {
        val diff = abs(bearing1 - bearing2) % 360f
        return if (diff > 180f) 360f - diff else diff
    }

    /**
     * Calculates a bounding box around (lat, lon) for a given radius in meters.
     */
    fun getBoundingBox(lat: Double, lon: Double, radiusMeters: Double = 500.0): BoundingBox {
        val latDelta = radiusMeters / 111000.0
        val cosLat = cos(Math.toRadians(lat)).coerceAtLeast(0.01)
        val lonDelta = radiusMeters / (111000.0 * cosLat)
        return BoundingBox(
            minLat = lat - latDelta,
            maxLat = lat + latDelta,
            minLon = lon - lonDelta,
            maxLon = lon + lonDelta
        )
    }
}
