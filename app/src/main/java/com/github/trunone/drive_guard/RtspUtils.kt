package com.github.trunone.drive_guard

import java.net.URI

object RtspUtils {
    /**
     * Parses and normalizes an input RTSP URL string (e.g. "192.168.1.100:554/stream1" or "rtsp://192.168.1.100:8554/live").
     * Returns a valid `rtsp://host:port/path` string, or null if invalid.
     */
    fun parseRtspUrl(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val urlWithScheme = if (!trimmed.contains("://")) {
            "rtsp://$trimmed"
        } else {
            trimmed
        }

        return try {
            val uri = URI(urlWithScheme)
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme != "rtsp" && scheme != "rtsps") return null

            val host = uri.host ?: return null
            val port = if (uri.port != -1) uri.port else 554
            if (port !in 1..65535) return null

            val pathAndQuery = buildString {
                if (!uri.rawPath.isNullOrEmpty()) {
                    if (!uri.rawPath.startsWith("/")) append("/")
                    append(uri.rawPath)
                }
                if (!uri.rawQuery.isNullOrEmpty()) {
                    append("?").append(uri.rawQuery)
                }
            }

            "$scheme://$host:$port$pathAndQuery"
        } catch (e: Exception) {
            null
        }
    }
}
