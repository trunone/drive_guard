package com.github.trunone.drive_guard.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.github.trunone.drive_guard.data.AppDatabase
import com.github.trunone.drive_guard.data.TrafficLightRepository
import com.github.trunone.drive_guard.mqtt.MqttBrokerManager
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull

/**
 * Android Foreground Service emitting high-accuracy GPS updates via Kotlin StateFlow,
 * evaluating regional boundary crossings, and dynamically driving MQTT broker subscriptions.
 */
class LocationTrackingService : Service() {

    private val binder = LocalBinder()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var mqttBrokerManager: MqttBrokerManager? = null
    private var lastBoundaryLocation: Location? = null

    inner class LocalBinder : Binder() {
        fun getService(): LocationTrackingService = this@LocationTrackingService
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize database, repository and MQTT Broker Manager
        val db = AppDatabase.getInstance(applicationContext)
        val repository = TrafficLightRepository(db.trafficLightDao())
        val brokerManager = MqttBrokerManager(repository)
        mqttBrokerManager = brokerManager
        brokerManager.connect()

        // Observe region boundary updates to adjust MQTT subscriptions dynamically
        serviceScope.launch {
            regionTagFlow.filterNotNull().collect { regionTag ->
                brokerManager.updateRegionSubscription(regionTag)
            }
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                _locationFlow.value = loc

                // Evaluate boundary / regional tile changes
                if (RegionBoundaryEvaluator.hasCrossedBoundary(loc, lastBoundaryLocation)) {
                    lastBoundaryLocation = loc
                    val newRegionTag = RegionBoundaryEvaluator.computeRegionTopicTag(loc.latitude, loc.longitude)
                    _regionTagFlow.value = newRegionTag
                }
            }
        }

        startForegroundServiceNotification()
        startLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setMinUpdateDistanceMeters(1f)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun startForegroundServiceNotification() {
        val channelId = "location_tracking_channel"
        val channelName = "Live Location Tracking"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("DriveGuard Traffic Monitor")
            .setContentText("Monitoring live GPS for traffic signal alerts...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        mqttBrokerManager?.disconnect()
        serviceScope.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001

        private val _locationFlow = MutableStateFlow<Location?>(null)
        val locationFlow: StateFlow<Location?> = _locationFlow.asStateFlow()

        private val _regionTagFlow = MutableStateFlow<String?>(null)
        val regionTagFlow: StateFlow<String?> = _regionTagFlow.asStateFlow()

        /**
         * Allows testing or external location injection.
         */
        fun updateMockLocation(location: Location) {
            _locationFlow.value = location
            if (RegionBoundaryEvaluator.hasCrossedBoundary(location, null)) {
                _regionTagFlow.value = RegionBoundaryEvaluator.computeRegionTopicTag(location.latitude, location.longitude)
            }
        }
    }
}
