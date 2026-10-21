package com.github.trunone.drive_guard

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.trunone.drive_guard.data.AppDatabase
import com.github.trunone.drive_guard.data.TrafficLightRepository
import com.github.trunone.drive_guard.mqtt.MqttBrokerManager
import com.github.trunone.drive_guard.mqtt.iVriPayloadParser
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MqttBrokerManagerTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TrafficLightRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = TrafficLightRepository(db.trafficLightDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun testPayloadParsingNormalState() {
        val payload = """
            {
                "intersection_id": "VRI_HAAG_01",
                "lane_id": "LANE_02",
                "color": "RED",
                "countdown_seconds": 18,
                "emergency_vehicle": false
            }
        """.trimIndent()

        val entity = iVriPayloadParser.parsePayload("nl/vri/region_haaglanden/VRI_HAAG_01/state", payload)

        assertNotNull(entity)
        assertEquals("VRI_HAAG_01", entity?.intersectionId)
        assertEquals("LANE_02", entity?.laneId)
        assertEquals("RED", entity?.currentColor)
        assertEquals(18, entity?.countdownSeconds)
        assertFalse(entity!!.emergencyOverrideActive)
    }

    @Test
    fun testPayloadParsingEmergencyOverride() {
        val payload = """
            {
                "intersectionId": "VRI_AMST_05",
                "laneId": "LANE_01",
                "state": "PROTECTED_MOVEMENT_ALLOWED",
                "timeToChange": 5,
                "emergencyVehicle": true
            }
        """.trimIndent()

        val entity = iVriPayloadParser.parsePayload("nl/vri/region_amsterdam/VRI_AMST_05/state", payload)

        assertNotNull(entity)
        assertEquals("VRI_AMST_05", entity?.intersectionId)
        assertEquals("LANE_01", entity?.laneId)
        assertEquals("GREEN", entity?.currentColor)
        assertEquals(5, entity?.countdownSeconds)
        assertTrue(entity!!.emergencyOverrideActive)
    }

    @Test
    fun testPayloadParsingInvalidJson() {
        val entity = iVriPayloadParser.parsePayload("nl/vri/topic", "invalid_json_{")
        assertNull(entity)
    }

    @Test
    fun testTopicSubscriptionUpdate() {
        val manager = MqttBrokerManager(repository)
        manager.updateRegionSubscription("nl/vri/region_haaglanden")

        assertEquals("nl/vri/region_haaglanden/+/#", manager.currentSubscribedTopic)
    }
}
