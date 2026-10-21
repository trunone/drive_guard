package com.github.trunone.drive_guard.data

import kotlinx.coroutines.flow.Flow

/**
 * Repository pattern implementation for traffic light data access and spatial queries.
 */
class TrafficLightRepository(private val dao: TrafficLightDao) {

    suspend fun saveIntersections(intersections: List<IntersectionEntity>) {
        dao.insertIntersections(intersections)
    }

    suspend fun saveIntersection(intersection: IntersectionEntity) {
        dao.insertIntersection(intersection)
    }

    suspend fun saveTrafficLightState(state: TrafficLightStateEntity) {
        dao.insertOrUpdateState(state)
    }

    suspend fun saveTrafficLightStates(states: List<TrafficLightStateEntity>) {
        dao.insertOrUpdateStates(states)
    }

    fun getNearestIntersectionFlow(userLat: Double, userLng: Double, maxDistanceMeters: Double = 200.0): Flow<IntersectionEntity?> {
        return dao.getNearestIntersectionFlow(userLat, userLng, maxDistanceMeters)
    }

    suspend fun getNearestIntersectionSync(userLat: Double, userLng: Double, maxDistanceMeters: Double = 200.0): IntersectionEntity? {
        return dao.getNearestIntersectionSync(userLat, userLng, maxDistanceMeters)
    }

    fun getStatesForIntersectionFlow(intersectionId: String): Flow<List<TrafficLightStateEntity>> {
        return dao.getStatesForIntersectionFlow(intersectionId)
    }

    suspend fun getStatesForIntersectionSync(intersectionId: String): List<TrafficLightStateEntity> {
        return dao.getStatesForIntersectionSync(intersectionId)
    }

    fun getStateForLaneFlow(laneId: String): Flow<TrafficLightStateEntity?> {
        return dao.getStateForLaneFlow(laneId)
    }

    suspend fun purgeStaleStates(olderThanMs: Long) {
        dao.purgeStaleStates(olderThanMs)
    }
}
