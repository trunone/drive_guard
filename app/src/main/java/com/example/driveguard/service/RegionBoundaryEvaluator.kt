package com.example.driveguard.service

import android.location.Location
import kotlin.math.floor

/**
 * Evaluates geographic region tags and boundary crossings for dynamic MQTT subscriptions.
 */
object RegionBoundaryEvaluator {

    /**
     * Converts lat/lng coordinates into a regional tile tag (e.g. "nl/vri/tile_520_043").
     * Uses 0.1 degree grid tiles (~11km x 7km in the Netherlands) for efficient regional grouping.
     */
    fun computeRegionTopicTag(latitude: Double, longitude: Double): String {
        val latTile = floor(latitude * 10).toInt()
        val lngTile = floor(longitude * 10).toInt()
        return "nl/vri/tile_${latTile}_${lngTile}"
    }

    /**
     * Determines if the user has moved far enough from [lastLocation] (> [thresholdMeters])
     * or crossed into a new regional tile to warrant updating regional network subscriptions.
     */
    fun hasCrossedBoundary(
        currentLocation: Location,
        lastLocation: Location?,
        thresholdMeters: Float = 1000f
    ): Boolean {
        if (lastLocation == null) return true
        val distance = currentLocation.distanceTo(lastLocation)
        if (distance >= thresholdMeters) return true

        val currentTag = computeRegionTopicTag(currentLocation.latitude, currentLocation.longitude)
        val lastTag = computeRegionTopicTag(lastLocation.latitude, lastLocation.longitude)
        return currentTag != lastTag
    }
}
