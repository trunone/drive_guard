package com.github.trunone.drive_guard.vision

import android.content.Context
import android.graphics.Bitmap
import com.github.trunone.drive_guard.LightColor
import kotlinx.coroutines.flow.SharedFlow

/**
 * Integrated Traffic Light Vision Engine combining Stage 1 Detection,
 * Stage 2 Crop-and-Classify, and 3-frame Temporal Smoothing with SharedFlow dispatching.
 */
class TrafficLightVisionPipeline(
    val stageOneDetector: StageOneDetector,
    val stageTwoClassifier: StageTwoClassifier,
    val dispatcher: TemporalSmoothingDispatcher = TemporalSmoothingDispatcher(bufferSize = 3)
) : AutoCloseable {

    constructor(context: Context) : this(
        stageOneDetector = StageOneDetector(context),
        stageTwoClassifier = StageTwoClassifier(context)
    )

    val visionResultFlow: SharedFlow<TrafficLightVisionResult>
        get() = dispatcher.resultFlow

    /**
     * Runs full 2-stage vision analysis pipeline on high-resolution image frame.
     * @param frame High-resolution camera frame
     * @param roiFrame Optional cropped ROI frame (defaults to frame if not cropped)
     * @param roiOffsetLeft Left offset of ROI frame relative to high-res frame
     * @param roiOffsetTop Top offset of ROI frame relative to high-res frame
     */
    fun processFrame(
        frame: Bitmap,
        roiFrame: Bitmap = frame,
        roiOffsetLeft: Float = 0f,
        roiOffsetTop: Float = 0f
    ): TrafficLightVisionResult? {
        val detection = stageOneDetector.detect(roiFrame)

        if (detection == null) {
            return dispatcher.processFrame(
                ClassificationResult(TrafficLightState.UNKNOWN, FloatArray(5) { 0f }, 0f)
            )
        }

        val classification = stageTwoClassifier.classifyFromRoiBox(
            highResFrame = frame,
            relativeBox = detection.box,
            roiOffsetLeft = roiOffsetLeft,
            roiOffsetTop = roiOffsetTop,
            roiWidth = roiFrame.width.toFloat(),
            roiHeight = roiFrame.height.toFloat(),
            roiImageWidth = roiFrame.width,
            roiImageHeight = roiFrame.height
        )

        return dispatcher.processFrame(classification)
    }

    override fun close() {
        stageOneDetector.close()
        stageTwoClassifier.close()
    }

    companion object {
        fun TrafficLightState.toLightColor(): LightColor {
            return when (this) {
                TrafficLightState.RED -> LightColor.RED
                TrafficLightState.AMBER -> LightColor.YELLOW
                TrafficLightState.GREEN -> LightColor.GREEN
                TrafficLightState.LEFT_ARROW_GREEN -> LightColor.GREEN_LEFT
                TrafficLightState.RIGHT_ARROW_GREEN -> LightColor.GREEN_RIGHT
                TrafficLightState.UNKNOWN -> LightColor.UNKNOWN
            }
        }
    }
}
