package com.example.driveguard.ui

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.driveguard.data.TrafficLightRepository
import com.example.driveguard.service.LocationTrackingService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Jetpack Compose ViewModel monitoring live GPS updates and Room queries for nearest traffic signals.
 */
class TrafficLightViewModel(
    private val repository: TrafficLightRepository,
    private val locationFlow: StateFlow<Location?> = LocationTrackingService.locationFlow
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrafficLightUiState())
    val uiState: StateFlow<TrafficLightUiState> = _uiState.asStateFlow()

    init {
        observeLocationAndSignals()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeLocationAndSignals() {
        viewModelScope.launch {
            locationFlow
                .filterNotNull()
                .flatMapLatest { location ->
                    flow {
                        val nearest = repository.getNearestIntersectionSync(
                            userLat = location.latitude,
                            userLng = location.longitude,
                            maxDistanceMeters = 200.0
                        )

                        if (nearest != null) {
                            val distance = calculateDistanceMeters(
                                location.latitude, location.longitude,
                                nearest.latitude, nearest.longitude
                            )
                            emitAll(
                                repository.getStatesForIntersectionFlow(nearest.id)
                                    .map { states ->
                                        val primaryState = states.firstOrNull { it.emergencyOverrideActive }
                                            ?: states.firstOrNull()

                                        TrafficLightUiState(
                                            userLatitude = location.latitude,
                                            userLongitude = location.longitude,
                                            nearestIntersection = nearest,
                                            activeState = primaryState,
                                            distanceMeters = distance,
                                            isSearchingSignal = false
                                        )
                                    }
                            )
                        } else {
                            emit(
                                TrafficLightUiState(
                                    userLatitude = location.latitude,
                                    userLongitude = location.longitude,
                                    nearestIntersection = null,
                                    activeState = null,
                                    distanceMeters = null,
                                    isSearchingSignal = true
                                )
                            )
                        }
                    }
                }
                .collect { newState ->
                    _uiState.value = newState
                }
        }
    }

    private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }
}
