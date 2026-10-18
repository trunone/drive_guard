package com.example.driveguard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.driveguard.data.EnforcementCameraEntity
import com.example.driveguard.repository.CameraWithDistance
import com.example.driveguard.repository.LocationRepository
import com.example.driveguard.repository.UserLocation
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CameraAlertState(
    val camera: EnforcementCameraEntity,
    val distanceMeters: Double,
    val userSpeedKmh: Double,
    val isCriticalViolation: Boolean,
    val shouldTriggerAudioChime: Boolean = false
)

class CameraAlertViewModel(
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _alertState = MutableStateFlow<CameraAlertState?>(null)
    val alertState: StateFlow<CameraAlertState?> = _alertState.asStateFlow()

    private val _audioChimeEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val audioChimeEvent: SharedFlow<Unit> = _audioChimeEvent.asSharedFlow()

    private var currentActiveCameraId: String? = null

    init {
        observeLocation()
    }

    private fun observeLocation() {
        viewModelScope.launch {
            locationRepository.locationFlow
                .filterNotNull()
                .collect { userLocation ->
                    processLocationUpdate(userLocation)
                }
        }
    }

    suspend fun processLocationUpdate(userLocation: UserLocation) {
        val nearbyDirectionalCameras = locationRepository.getNearbyDirectionalCameras(
            userLat = userLocation.latitude,
            userLon = userLocation.longitude,
            userBearing = userLocation.bearing,
            radiusMeters = 500.0,
            toleranceDegrees = 30f
        )

        // Find cameras within 300 meters threshold
        val approachingCamera = nearbyDirectionalCameras
            .filter { it.distanceMeters < 300.0 }
            .minByOrNull { it.distanceMeters }

        if (approachingCamera != null) {
            val camera = approachingCamera.camera
            val userSpeedKmh = userLocation.speedMps * 3.6
            val maxSpeed = camera.max_speed
            val isCritical = maxSpeed != null && userSpeedKmh > maxSpeed

            val isNewCameraAlert = currentActiveCameraId != camera.id
            if (isNewCameraAlert) {
                currentActiveCameraId = camera.id
                _audioChimeEvent.tryEmit(Unit)
            }

            _alertState.value = CameraAlertState(
                camera = camera,
                distanceMeters = approachingCamera.distanceMeters,
                userSpeedKmh = userSpeedKmh,
                isCriticalViolation = isCritical,
                shouldTriggerAudioChime = isNewCameraAlert
            )
        } else {
            currentActiveCameraId = null
            _alertState.value = null
        }
    }
}
