package com.example.driveguard.ui

import com.example.driveguard.data.IntersectionEntity
import com.example.driveguard.data.TrafficLightStateEntity
import com.example.driveguard.vision.TrafficLightVisionResult

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
