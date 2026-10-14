package com.example.driveguard

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class TrapCameraUrlParserTest {

    @Test
    fun testParseUrlWithIpAndPort() {
        val result = TrapCameraUrlParser.parseUrl("192.168.1.100:8080")
        assertEquals("http://192.168.1.100:8080", result)
    }

    @Test
    fun testParseUrlWithFullHttpAndPortAndPath() {
        val result = TrapCameraUrlParser.parseUrl("http://192.168.1.50:8080/mjpeg/stream")
        assertEquals("http://192.168.1.50:8080/mjpeg/stream", result)
    }

    @Test
    fun testParseUrlWithHttpsAndPort() {
        val result = TrapCameraUrlParser.parseUrl("https://10.0.0.5:8443/feed")
        assertEquals("https://10.0.0.5:8443/feed", result)
    }

    @Test
    fun testParseUrlWithWhitespace() {
        val result = TrapCameraUrlParser.parseUrl("   192.168.1.200:9000/shot.jpg   ")
        assertEquals("http://192.168.1.200:9000/shot.jpg", result)
    }

    @Test
    fun testEmptyUrlThrowsException() {
        try {
            TrapCameraUrlParser.parseUrl("")
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun testFetcherTargetUrl() {
        val fetcher = TrapCameraFetcher("192.168.1.10:8080", {}, {})
        assertEquals("http://192.168.1.10:8080", fetcher.targetUrl)
    }
}
