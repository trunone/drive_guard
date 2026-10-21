package com.example.driveguard.vision

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Output data structure emitted to HUD layers when a traffic light state is processed.
 */
data class TrafficLightVisionResult(
    val active_color: String,  // "Red", "Amber", "Green", "Left Arrow Green", "Right Arrow Green", "Unknown"
    val is_arrow: Boolean,     // True if state is left or right turn green arrow
    val confidence: Float,     // Classification confidence score (0.0 to 1.0)
    val state: TrafficLightState = TrafficLightState.UNKNOWN
)

/**
 * Temporal Smoothing Ring Buffer & SharedFlow Dispatcher
 *
 * Vehicle vibrations or shadows can cause a model to misclassify a single frame.
 * A traffic light state change must be observed consistently over 3 consecutive frames
 * before a state transition is confirmed.
 */
class TemporalSmoothingDispatcher(
    private val bufferSize: Int = 3,
    extraBufferCapacity: Int = 16
) {
    private val ringBuffer = ArrayDeque<ClassificationResult>(bufferSize)
    private var currentConfirmedState: TrafficLightState = TrafficLightState.UNKNOWN

    private val _resultFlow = MutableSharedFlow<TrafficLightVisionResult>(
        replay = 1,
        extraBufferCapacity = extraBufferCapacity
    )
    val resultFlow: SharedFlow<TrafficLightVisionResult> = _resultFlow.asSharedFlow()

    /**
     * Adds a new classification result to the ring buffer.
     * Evaluates whether the last [bufferSize] consecutive frames agree on the state.
     * If 3 consecutive frames match a new state, updates the confirmed state and dispatches the result.
     * Returns the emitted result if a state transition occurred or if updated, or null if no update was dispatched.
     */
    fun processFrame(result: ClassificationResult): TrafficLightVisionResult? {
        ringBuffer.addLast(result)
        while (ringBuffer.size > bufferSize) {
            ringBuffer.removeFirst()
        }

        if (ringBuffer.size < bufferSize) {
            return null
        }

        val firstState = ringBuffer.first().state
        val isConsistent = ringBuffer.all { it.state == firstState }

        if (isConsistent) {
            val isTransition = (firstState != currentConfirmedState)
            currentConfirmedState = firstState
            val avgConfidence = ringBuffer.map { it.confidence }.average().toFloat()

            val activeColorStr = when (firstState) {
                TrafficLightState.RED -> "Red"
                TrafficLightState.AMBER -> "Amber"
                TrafficLightState.GREEN -> "Green"
                TrafficLightState.LEFT_ARROW_GREEN -> "Left Arrow Green"
                TrafficLightState.RIGHT_ARROW_GREEN -> "Right Arrow Green"
                TrafficLightState.UNKNOWN -> "Unknown"
            }

            val visionResult = TrafficLightVisionResult(
                active_color = activeColorStr,
                is_arrow = firstState.isArrow,
                confidence = avgConfidence,
                state = firstState
            )

            if (isTransition) {
                _resultFlow.tryEmit(visionResult)
            }
            return visionResult
        } else if (currentConfirmedState != TrafficLightState.UNKNOWN) {
            val activeColorStr = when (currentConfirmedState) {
                TrafficLightState.RED -> "Red"
                TrafficLightState.AMBER -> "Amber"
                TrafficLightState.GREEN -> "Green"
                TrafficLightState.LEFT_ARROW_GREEN -> "Left Arrow Green"
                TrafficLightState.RIGHT_ARROW_GREEN -> "Right Arrow Green"
                TrafficLightState.UNKNOWN -> "Unknown"
            }
            return TrafficLightVisionResult(
                active_color = activeColorStr,
                is_arrow = currentConfirmedState.isArrow,
                confidence = ringBuffer.map { it.confidence }.average().toFloat(),
                state = currentConfirmedState
            )
        }

        return null
    }

    /**
     * Resets ring buffer and confirmed state.
     */
    fun reset() {
        ringBuffer.clear()
        currentConfirmedState = TrafficLightState.UNKNOWN
    }
}
