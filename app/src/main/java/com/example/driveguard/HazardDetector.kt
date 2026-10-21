package com.example.driveguard

import android.content.Context
import android.graphics.Bitmap
import com.example.driveguard.vision.StageOneDetector

/** Object detector using StageOneDetector (YOLOv8). Runs synchronously on the analysis thread. */
class HazardDetector(context: Context) {
    private val stageOneDetector = StageOneDetector(context, MODEL)

    fun detect(bmp: Bitmap): List<Det> =
        stageOneDetector.detectAll(bmp).map { detection ->
            Det("traffic light", detection.confidence, detection.box)
        }

    fun close() = stageOneDetector.close()

    companion object {
        const val MODEL = StageOneDetector.DEFAULT_MODEL_NAME
        val RELEVANT = setOf("person", "bicycle", "car", "motorcycle", "bus", "truck", "traffic light", "stop sign")
    }
}
