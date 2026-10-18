package com.example.driveguard.repository

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import com.example.driveguard.data.EnforcementCameraDao
import com.example.driveguard.data.EnforcementCameraEntity
import com.example.driveguard.util.GeoUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val bearing: Float,
    val speedMps: Float
)

data class CameraWithDistance(
    val camera: EnforcementCameraEntity,
    val distanceMeters: Double,
    val targetBearing: Float
)

class LocationRepository(
    private val context: Context? = null,
    private val cameraDao: EnforcementCameraDao? = null
) : LocationListener {

    private val _locationFlow = MutableStateFlow<UserLocation?>(null)
    val locationFlow: StateFlow<UserLocation?> = _locationFlow.asStateFlow()

    fun updateLocation(location: UserLocation) {
        _locationFlow.value = location
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        context?.let { ctx ->
            val locationManager = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                this
            )
        }
    }

    fun stopLocationUpdates() {
        context?.let { ctx ->
            val locationManager = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            locationManager?.removeUpdates(this)
        }
    }

    override fun onLocationChanged(location: Location) {
        val userLocation = UserLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            bearing = if (location.hasBearing()) location.bearing else 0f,
            speedMps = if (location.hasSpeed()) location.speed else 0f
        )
        updateLocation(userLocation)
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    /**
     * Filters nearby cameras based on whether the camera's target bearing relative to the user
     * aligns with the user's travel direction (bearing) within toleranceDegrees (default 30 degrees).
     */
    fun filterCamerasByDirection(
        userLat: Double,
        userLon: Double,
        userBearing: Float,
        cameras: List<EnforcementCameraEntity>,
        toleranceDegrees: Float = 30f
    ): List<CameraWithDistance> {
        return cameras.mapNotNull { camera ->
            val targetBearing = GeoUtils.calculateBearing(userLat, userLon, camera.latitude, camera.longitude)
            val angleDiff = GeoUtils.calculateAngularDifference(userBearing, targetBearing)
            if (angleDiff <= toleranceDegrees) {
                val distance = GeoUtils.calculateDistanceMeters(userLat, userLon, camera.latitude, camera.longitude)
                CameraWithDistance(
                    camera = camera,
                    distanceMeters = distance,
                    targetBearing = targetBearing
                )
            } else {
                null
            }
        }
    }

    /**
     * Queries cameras in a bounding box (default 500m) from Room DAO and filters them by direction.
     */
    suspend fun getNearbyDirectionalCameras(
        userLat: Double,
        userLon: Double,
        userBearing: Float,
        radiusMeters: Double = 500.0,
        toleranceDegrees: Float = 30f
    ): List<CameraWithDistance> {
        val dao = cameraDao ?: return emptyList()
        val box = GeoUtils.getBoundingBox(userLat, userLon, radiusMeters)
        val camerasInBox = dao.getCamerasInBoundingBox(box.minLat, box.maxLat, box.minLon, box.maxLon)
        return filterCamerasByDirection(userLat, userLon, userBearing, camerasInBox, toleranceDegrees)
    }
}
