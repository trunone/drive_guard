package com.example.driveguard.mqtt

import android.util.Log
import com.example.driveguard.data.TrafficLightRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID

/**
 * Manages persistent background MQTT connection to Dutch traffic brokers (NDW/TLEX),
 * dynamic regional topic filtering, and asynchronous high-throughput message ingestion.
 */
class MqttBrokerManager(
    private val repository: TrafficLightRepository,
    private val serverUri: String = DEFAULT_BROKER_URI,
    private val clientId: String = "driveguard_${UUID.randomUUID().toString().take(8)}",
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    private var client: MqttAsyncClient? = null
    @Volatile private var isConnected = false
    @Volatile var currentSubscribedTopic: String? = null
        private set

    private var reconnectDelayMs = 1000L
    private val maxReconnectDelayMs = 30000L

    fun connect() {
        if (isConnected || client?.isConnected == true) return

        scope.launch(Dispatchers.IO) {
            try {
                val mqttClient = MqttAsyncClient(serverUri, clientId, MemoryPersistence())
                client = mqttClient

                val options = MqttConnectOptions().apply {
                    isAutomaticReconnect = true
                    isCleanSession = true
                    connectionTimeout = 10
                    keepAliveInterval = 30
                }

                mqttClient.setCallback(object : MqttCallbackExtended {
                    override fun connectComplete(reconnect: Boolean, serverURI: String) {
                        Log.i(TAG, "MQTT Connected to $serverURI (reconnect: $reconnect)")
                        isConnected = true
                        reconnectDelayMs = 1000L // Reset backoff

                        // Re-subscribe to current region if set
                        currentSubscribedTopic?.let { topic ->
                            subscribeToTopicInternal(topic)
                        }
                    }

                    override fun connectionLost(cause: Throwable?) {
                        Log.w(TAG, "MQTT Connection lost: ${cause?.message}")
                        isConnected = false
                        scheduleReconnectWithBackoff()
                    }

                    override fun messageArrived(topic: String, message: MqttMessage) {
                        // High-throughput offloading to Dispatchers.Default to keep socket read loop unblocked
                        val payloadStr = String(message.payload)
                        scope.launch(Dispatchers.Default) {
                            val entity = iVriPayloadParser.parsePayload(topic, payloadStr)
                            if (entity != null) {
                                repository.saveTrafficLightState(entity)
                            }
                        }
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })

                mqttClient.connect(options, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.i(TAG, "MQTT Connect action succeeded")
                        isConnected = true
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e(TAG, "MQTT Connect action failed: ${exception?.message}")
                        isConnected = false
                        scheduleReconnectWithBackoff()
                    }
                })

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize MQTT client: ${e.message}")
                scheduleReconnectWithBackoff()
            }
        }
    }

    /**
     * Dynamically updates regional topic filters based on current user location or region tag.
     * Unsubscribes from stale regional topics to minimize bandwidth.
     */
    fun updateRegionSubscription(regionTopicTag: String) {
        val newTopic = "$regionTopicTag/+/#"
        if (newTopic == currentSubscribedTopic) return

        val oldTopic = currentSubscribedTopic
        currentSubscribedTopic = newTopic

        scope.launch(Dispatchers.IO) {
            if (isConnected && client?.isConnected == true) {
                if (oldTopic != null) {
                    try {
                        client?.unsubscribe(oldTopic)
                        Log.i(TAG, "Unsubscribed from stale regional topic: $oldTopic")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to unsubscribe from $oldTopic: ${e.message}")
                    }
                }
                subscribeToTopicInternal(newTopic)
            }
        }
    }

    private fun subscribeToTopicInternal(topic: String) {
        try {
            client?.subscribe(topic, 0, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i(TAG, "Subscribed to topic: $topic")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Failed to subscribe to $topic: ${exception?.message}")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error issuing subscribe for $topic: ${e.message}")
        }
    }

    private fun scheduleReconnectWithBackoff() {
        scope.launch(Dispatchers.IO) {
            delay(reconnectDelayMs)
            reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(maxReconnectDelayMs)
            Log.i(TAG, "Attempting MQTT reconnection with backoff delay ${reconnectDelayMs}ms...")
            connect()
        }
    }

    fun disconnect() {
        scope.launch(Dispatchers.IO) {
            try {
                if (client?.isConnected == true) {
                    client?.disconnect()
                }
                client?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting MQTT client: ${e.message}")
            } finally {
                isConnected = false
            }
        }
    }

    companion object {
        private const val TAG = "MqttBrokerManager"
        const val DEFAULT_BROKER_URI = "tcp://broker.hivemq.com:1883"
    }
}
