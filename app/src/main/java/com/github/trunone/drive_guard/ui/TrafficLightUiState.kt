package com.github.trunone.drive_guard.ui

import com.github.trunone.drive_guard.data.IntersectionEntity
import com.github.trunone.drive_guard.data.TrafficLightStateEntity
import com.github.trunone.drive_guard.vision.TrafficLightVisionResult

/**
 * UI State for the iVRI Traffic Light Dashboard.
 */
data class TrafficLightUiState(
    val userLatitude: Double? = null,
    val userLongitude: Double? = null,
    val nearestIntersection: IntersectionEntity? = null,
    val activeState: TrafficLightStateEntity? = null,
    val distanceMeters: Float? = null,
    val isSearchingSignal: Boolean = true,
    val visionResult: TrafficLightVisionResult? = null
)
