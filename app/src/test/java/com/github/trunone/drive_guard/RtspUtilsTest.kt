package com.github.trunone.drive_guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RtspUtilsTest {

    @Test
    fun testParseRtspUrlWithExplicitPort() {
        val input = "rtsp://192.168.1.50:8554/live"
        val parsed = RtspUtils.parseRtspUrl(input)
        assertEquals("rtsp://192.168.1.50:8554/live", parsed)
    }

    @Test
    fun testParseRtspUrlWithoutSchemeAndExplicitPort() {
        val input = "192.168.1.50:8554/h264"
        val parsed = RtspUtils.parseRtspUrl(input)
        assertEquals("rtsp://192.168.1.50:8554/h264", parsed)
    }

    @Test
    fun testParseRtspUrlWithoutPortDefaultsTo554() {
        val input = "rtsp://10.0.0.12/stream1"
        val parsed = RtspUtils.parseRtspUrl(input)
        assertEquals("rtsp://10.0.0.12:554/stream1", parsed)
    }

    @Test
    fun testParseRtspUrlBareHostAndPort() {
        val input = "camera.local:554"
        val parsed = RtspUtils.parseRtspUrl(input)
        assertEquals("rtsp://camera.local:554", parsed)
    }

    @Test
    fun testParseRtspUrlWithQueryParameters() {
        val input = "rtsp://192.168.1.100:554/cam/realmonitor?channel=1&subtype=0"
        val parsed = RtspUtils.parseRtspUrl(input)
        assertEquals("rtsp://192.168.1.100:554/cam/realmonitor?channel=1&subtype=0", parsed)
    }

    @Test
    fun testParseRtspUrlInvalidEmpty() {
        assertNull(RtspUtils.parseRtspUrl(""))
        assertNull(RtspUtils.parseRtspUrl("   "))
    }

    @Test
    fun testParseRtspUrlInvalidScheme() {
        assertNull(RtspUtils.parseRtspUrl("http://192.168.1.50:8080/stream"))
    }

    @Test
    fun testParseRtspUrlInvalidPortNumber() {
        assertNull(RtspUtils.parseRtspUrl("rtsp://192.168.1.50:70000/stream"))
        assertNull(RtspUtils.parseRtspUrl("rtsp://192.168.1.50:0/stream"))
    }
}
