package com.example.driveguard.mqtt

import com.example.driveguard.data.TrafficLightStateEntity
import org.json.JSONObject

/**
 * Decodes incoming iVRI (NDW/TLEX) MQTT messages into TrafficLightStateEntity database models.
 */
object iVriPayloadParser {

    /**
     * Parses payload string into [TrafficLightStateEntity].
     * @param topic MQTT topic from which message was received.
     * @param payload JSON message payload.
     * @return Parsed state entity or null if required fields are missing or invalid JSON.
     */
    fun parsePayload(topic: String, payload: String): TrafficLightStateEntity? {
        return try {
            val json = JSONObject(payload)

            // Extract intersection ID from payload or fallback to topic hierarchy
            val intersectionId = json.optString("intersection_id").ifEmpty {
                json.optString("intersectionId").ifEmpty {
                    extractIntersectionIdFromTopic(topic) ?: "UNKNOWN_VRI"
                }
            }

            // Extract lane ID
            val laneId = json.optString("lane_id").ifEmpty {
                json.optString("laneId").ifEmpty {
                    json.optString("lane").ifEmpty {
                        "${intersectionId}_lane_1"
                    }
                }
            }

            // Extract color/state
            val rawColor = json.optString("current_color").ifEmpty {
                json.optString("color").ifEmpty {
                    json.optString("state").ifEmpty {
                        json.optString("signalState", "UNKNOWN")
                    }
                }
            }
            val currentColor = normalizeColor(rawColor)

            // Extract countdown seconds
            val countdownSeconds = when {
                json.has("countdown_seconds") -> json.getInt("countdown_seconds")
                json.has("countdown") -> json.getInt("countdown")
                json.has("timeToChange") -> json.getInt("timeToChange")
                else -> 0
            }

            // Extract emergency vehicle override flag with immediate safety prioritization
            val emergencyOverride = json.optBoolean("emergency_override_active", false) ||
                    json.optBoolean("emergency_vehicle", false) ||
                    json.optBoolean("emergencyVehicle", false) ||
                    json.optBoolean("priorityActive", false)

            TrafficLightStateEntity(
                laneId = laneId,
                intersectionId = intersectionId,
                currentColor = currentColor,
                countdownSeconds = countdownSeconds.coerceAtLeast(0),
                emergencyOverrideActive = emergencyOverride,
                lastUpdatedMs = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun normalizeColor(raw: String): String {
        val upper = raw.uppercase()
        return when {
            upper.contains("RED") || upper.contains("STOP") -> "RED"
            upper.contains("AMBER") || upper.contains("YELLOW") || upper.contains("CLEARANCE") -> "AMBER"
            upper.contains("GREEN") || upper.contains("GO") || upper.contains("PROTECTED") || upper.contains("PERMISSIVE") -> "GREEN"
            else -> "UNKNOWN"
        }
    }

    private fun extractIntersectionIdFromTopic(topic: String): String? {
        val parts = topic.split("/")
        return parts.getOrNull(parts.size - 2) ?: parts.lastOrNull()
    }
}
