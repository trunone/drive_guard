package com.github.trunone.drive_guard.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import kotlinx.coroutines.flow.Flow
/**
 * Data Access Object for intersections and traffic light state caching.
 */
@Dao
@RewriteQueriesToDropUnusedColumns
interface TrafficLightDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIntersections(intersections: List<IntersectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIntersection(intersection: IntersectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateState(state: TrafficLightStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateStates(states: List<TrafficLightStateEntity>)

    /**
     * Calculates spatial distance directly in SQLite using equirectangular projection
     * (flat-surface Haversine approximation) for localized queries without requiring external math extensions.
     */
    @Query("""
        SELECT * FROM intersections
        WHERE (
            ((latitude - :userLat) * 111139.0) * ((latitude - :userLat) * 111139.0) +
            ((longitude - :userLng) * 111139.0 * :cosLat) * ((longitude - :userLng) * 111139.0 * :cosLat)
        ) <= (:maxDistanceMeters * :maxDistanceMeters)
        ORDER BY (
            ((latitude - :userLat) * 111139.0) * ((latitude - :userLat) * 111139.0) +
            ((longitude - :userLng) * 111139.0 * :cosLat) * ((longitude - :userLng) * 111139.0 * :cosLat)
        ) ASC
        LIMIT 1
    """)
    fun getNearestIntersectionInternalFlow(
        userLat: Double,
        userLng: Double,
        cosLat: Double,
        maxDistanceMeters: Double
    ): Flow<IntersectionEntity?>

    @Query("""
        SELECT * FROM intersections
        WHERE (
            ((latitude - :userLat) * 111139.0) * ((latitude - :userLat) * 111139.0) +
            ((longitude - :userLng) * 111139.0 * :cosLat) * ((longitude - :userLng) * 111139.0 * :cosLat)
        ) <= (:maxDistanceMeters * :maxDistanceMeters)
        ORDER BY (
            ((latitude - :userLat) * 111139.0) * ((latitude - :userLat) * 111139.0) +
            ((longitude - :userLng) * 111139.0 * :cosLat) * ((longitude - :userLng) * 111139.0 * :cosLat)
        ) ASC
        LIMIT 1
    """)
    suspend fun getNearestIntersectionInternalSync(
        userLat: Double,
        userLng: Double,
        cosLat: Double,
        maxDistanceMeters: Double
    ): IntersectionEntity?

    fun getNearestIntersectionFlow(
        userLat: Double,
        userLng: Double,
        maxDistanceMeters: Double = 200.0
    ): Flow<IntersectionEntity?> {
        val cosLat = Math.cos(Math.toRadians(userLat))
        return getNearestIntersectionInternalFlow(userLat, userLng, cosLat, maxDistanceMeters)
    }

    suspend fun getNearestIntersectionSync(
        userLat: Double,
        userLng: Double,
        maxDistanceMeters: Double = 200.0
    ): IntersectionEntity? {
        val cosLat = Math.cos(Math.toRadians(userLat))
        return getNearestIntersectionInternalSync(userLat, userLng, cosLat, maxDistanceMeters)
    }

    @Query("SELECT * FROM traffic_light_states WHERE intersection_id = :intersectionId")
    fun getStatesForIntersectionFlow(intersectionId: String): Flow<List<TrafficLightStateEntity>>

    @Query("SELECT * FROM traffic_light_states WHERE intersection_id = :intersectionId")
    suspend fun getStatesForIntersectionSync(intersectionId: String): List<TrafficLightStateEntity>

    @Query("SELECT * FROM traffic_light_states WHERE lane_id = :laneId LIMIT 1")
    fun getStateForLaneFlow(laneId: String): Flow<TrafficLightStateEntity?>

    @Query("SELECT * FROM intersections")
    suspend fun getAllIntersections(): List<IntersectionEntity>

    @Query("DELETE FROM traffic_light_states WHERE last_updated_ms < :olderThanMs")
    suspend fun purgeStaleStates(olderThanMs: Long)
}
