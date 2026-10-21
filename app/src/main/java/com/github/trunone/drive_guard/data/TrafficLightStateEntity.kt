package com.github.trunone.drive_guard.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores reactive real-time state for a traffic light lane at an intersection.
 */
@Entity(tableName = "traffic_light_states")
data class TrafficLightStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "lane_id")
    val laneId: String,
    @ColumnInfo(name = "intersection_id")
    val intersectionId: String,
    @ColumnInfo(name = "current_color")
    val currentColor: String, // "RED", "AMBER", "GREEN", "UNKNOWN"
    @ColumnInfo(name = "countdown_seconds")
    val countdownSeconds: Int,
    @ColumnInfo(name = "emergency_override_active")
    val emergencyOverrideActive: Boolean,
    @ColumnInfo(name = "last_updated_ms")
    val lastUpdatedMs: Long = System.currentTimeMillis()
)
