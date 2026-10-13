package com.example.driveguard

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlertManagerTest {

    @Test
    fun testAlertManagerCooldownDefaults() {
        val alerts = AlertManager(RuntimeEnvironment.getApplication())
        assertEquals(3_000L, alerts.critCooldownMs)
        assertEquals(6_000L, alerts.warnCooldownMs)
        assertEquals(20_000L, alerts.infoCooldownMs)
    }

    @Test
    fun testAlertManagerCooldownModification() {
        val alerts = AlertManager(RuntimeEnvironment.getApplication())
        alerts.critCooldownMs = 1_000L
        alerts.warnCooldownMs = 2_000L
        alerts.infoCooldownMs = 5_000L

        assertEquals(1_000L, alerts.critCooldownMs)
        assertEquals(2_000L, alerts.warnCooldownMs)
        assertEquals(5_000L, alerts.infoCooldownMs)
    }
}
