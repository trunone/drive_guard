package com.example.driveguard.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class EnforcementCameraType {
    SPEED,
    RED_LIGHT,
    COMBINED,
    AVERAGE_SPEED_START,
    AVERAGE_SPEED_END
}

@Entity(tableName = "enforcement_cameras")
data class EnforcementCameraEntity(
    @PrimaryKey
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val type: EnforcementCameraType,
    @ColumnInfo(name = "max_speed")
    val max_speed: Int? = null
)
