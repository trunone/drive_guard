package com.example.driveguard

import androidx.room.Room
import com.example.driveguard.data.AppDatabase
import com.example.driveguard.data.EnforcementCameraDao
import com.example.driveguard.data.EnforcementCameraEntity
import com.example.driveguard.data.EnforcementCameraType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CameraDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: EnforcementCameraDao

    @Before
    fun createDb() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.enforcementCameraDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testInsertAndGetCamerasInBoundingBox() = runBlocking {
        val cameras = listOf(
            EnforcementCameraEntity("c1", 52.001, 5.001, EnforcementCameraType.SPEED, 100),
            EnforcementCameraEntity("c2", 52.002, 5.002, EnforcementCameraType.RED_LIGHT, 50),
            EnforcementCameraEntity("c3", 53.000, 6.000, EnforcementCameraType.COMBINED, 80) // Outside box
        )

        dao.insertAll(cameras)

        val result = dao.getCamerasInBoundingBox(
            minLat = 52.000,
            maxLat = 52.005,
            minLon = 5.000,
            maxLon = 5.005
        )

        assertEquals(2, result.size)
        val c1 = result.find { it.id == "c1" }
        assertNotNull(c1)
        assertEquals(EnforcementCameraType.SPEED, c1?.type)
        assertEquals(100, c1?.max_speed)
    }

    @Test
    fun testAverageSpeedCheckCameras() = runBlocking {
        val cameraStart = EnforcementCameraEntity("start_1", 52.100, 5.100, EnforcementCameraType.AVERAGE_SPEED_START, 100)
        val cameraEnd = EnforcementCameraEntity("end_1", 52.150, 5.150, EnforcementCameraType.AVERAGE_SPEED_END, 100)

        dao.insertAll(listOf(cameraStart, cameraEnd))

        val all = dao.getAllCameras()
        assertEquals(2, all.size)
        assertEquals(EnforcementCameraType.AVERAGE_SPEED_START, all.find { it.id == "start_1" }?.type)
        assertEquals(EnforcementCameraType.AVERAGE_SPEED_END, all.find { it.id == "end_1" }?.type)
    }
}
