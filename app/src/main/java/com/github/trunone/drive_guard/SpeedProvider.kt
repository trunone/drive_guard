package com.github.trunone.drive_guard

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock

/** GPS ground speed in m/s (null when no recent fix). */
class SpeedProvider(private val ctx: Context) : LocationListener {
    @Volatile private var speedMps: Float? = null
    @Volatile private var lastFix = 0L

    val current: Float? get() = if (SystemClock.elapsedRealtime() - lastFix < 5000) speedMps else null

    @SuppressLint("MissingPermission")
    fun start() {
        ctx.getSystemService(LocationManager::class.java)
            .requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this)
    }

    fun stop() = ctx.getSystemService(LocationManager::class.java).removeUpdates(this)

    override fun onLocationChanged(l: Location) {
        speedMps = if (l.hasSpeed()) l.speed else null
        lastFix = SystemClock.elapsedRealtime()
    }

    // Required on API < 30
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
}
