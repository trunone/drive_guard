package com.github.trunone.drive_guard

import android.location.Location
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.trunone.drive_guard.data.AppDatabase
import com.github.trunone.drive_guard.data.IntersectionEntity
import com.github.trunone.drive_guard.data.TrafficLightRepository
import com.github.trunone.drive_guard.data.TrafficLightStateEntity
import com.github.trunone.drive_guard.ui.TrafficLightViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TrafficLightViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var db: AppDatabase
    private lateinit var repository: TrafficLightRepository
    private val locationFlow = MutableStateFlow<Location?>(null)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = TrafficLightRepository(db.trafficLightDao())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun testViewModelReceivesNearestSignalState() = runTest(testDispatcher) {
        // Prepare DB data
        val intersection = IntersectionEntity(
            id = "VRI_HAAG_01",
            latitude = 52.0786,
            longitude = 4.3100,
            regionTopicTag = "nl/vri/region_haaglanden"
        )
        val state = TrafficLightStateEntity(
            laneId = "LANE_01",
            intersectionId = "VRI_HAAG_01",
            currentColor = "RED",
            countdownSeconds = 12,
            emergencyOverrideActive = true
        )

        repository.saveIntersection(intersection)
        repository.saveTrafficLightState(state)

        val viewModel = TrafficLightViewModel(repository, locationFlow)

        // Emit user location near VRI_HAAG_01
        val userLoc = Location("gps").apply {
            latitude = 52.07865
            longitude = 4.31005
        }
        locationFlow.value = userLoc

        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertFalse(uiState.isSearchingSignal)
        assertNotNull(uiState.nearestIntersection)
        assertEquals("VRI_HAAG_01", uiState.nearestIntersection?.id)
        assertNotNull(uiState.activeState)
        assertEquals("RED", uiState.activeState?.currentColor)
        assertEquals(12, uiState.activeState?.countdownSeconds)
        assertTrue(uiState.activeState!!.emergencyOverrideActive)
    }
}
