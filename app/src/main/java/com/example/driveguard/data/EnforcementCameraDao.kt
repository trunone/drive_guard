package com.example.driveguard.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EnforcementCameraDao {
    @Query("""
        SELECT * FROM enforcement_cameras
        WHERE latitude BETWEEN :minLat AND :maxLat
          AND longitude BETWEEN :minLon AND :maxLon
    """)
    suspend fun getCamerasInBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double
    ): List<EnforcementCameraEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cameras: List<EnforcementCameraEntity>)

    @Query("SELECT * FROM enforcement_cameras")
    suspend fun getAllCameras(): List<EnforcementCameraEntity>
}
