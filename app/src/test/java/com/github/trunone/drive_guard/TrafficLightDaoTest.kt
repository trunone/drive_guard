package com.github.trunone.drive_guard

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.trunone.drive_guard.data.AppDatabase
import com.github.trunone.drive_guard.data.IntersectionEntity
import com.github.trunone.drive_guard.data.TrafficLightDao
import com.github.trunone.drive_guard.data.TrafficLightStateEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrafficLightDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TrafficLightDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.trafficLightDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun testHaversineNearestIntersectionQuery() = runBlocking {
        // Center: Hague City Center (52.0786, 4.3100)
        val closeIntersection = IntersectionEntity(
            id = "VRI_001",
            latitude = 52.0787, // ~11 meters away
            longitude = 4.3101,
            regionTopicTag = "nl/vri/region_haaglanden"
        )
        val farIntersection = IntersectionEntity(
            id = "VRI_002",
            latitude = 52.1000, // ~2.4 km away
            longitude = 4.3500,
            regionTopicTag = "nl/vri/region_haaglanden"
        )

        dao.insertIntersections(listOf(closeIntersection, farIntersection))

        val nearest = dao.getNearestIntersectionSync(52.0786, 4.3100, 200.0)
        assertNotNull(nearest)
        assertEquals("VRI_001", nearest?.id)
    }

    @Test
    fun testTrafficLightStateInsertAndQuery() = runBlocking {
        val state = TrafficLightStateEntity(
            laneId = "LANE_01",
            intersectionId = "VRI_001",
            currentColor = "GREEN",
            countdownSeconds = 12,
            emergencyOverrideActive = true
        )
        dao.insertOrUpdateState(state)

        val retrieved = dao.getStatesForIntersectionSync("VRI_001")
        assertEquals(1, retrieved.size)
        assertEquals("GREEN", retrieved[0].currentColor)
        assertTrue(retrieved[0].emergencyOverrideActive)
    }
}
