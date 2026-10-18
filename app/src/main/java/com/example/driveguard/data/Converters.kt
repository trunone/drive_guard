package com.example.driveguard.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromEnforcementCameraType(type: EnforcementCameraType?): String? {
        return type?.name
    }

    @TypeConverter
    fun toEnforcementCameraType(value: String?): EnforcementCameraType? {
        return value?.let {
            try {
                EnforcementCameraType.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
