package com.example.driveguard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector

/** COCO object detector (MediaPipe). Runs synchronously on the analysis thread. */
class HazardDetector(context: Context) {
    private val detector = ObjectDetector.createFromOptions(
        context,
        ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).build())
            .setRunningMode(RunningMode.IMAGE)
            .setScoreThreshold(0.35f)
            .setMaxResults(25)
            .build()
    )

    fun detect(bmp: Bitmap): List<Det> =
        detector.detect(BitmapImageBuilder(bmp).build()).detections().mapNotNull { d ->
            val c = d.categories().firstOrNull() ?: return@mapNotNull null
            if (c.categoryName() in RELEVANT) Det(c.categoryName(), c.score(), RectF(d.boundingBox())) else null
        }

    fun close() = detector.close()

    companion object {
        const val MODEL = "efficientdet_lite0.tflite" // swap for efficientdet_lite2 for better range
        val RELEVANT = setOf("person", "bicycle", "car", "motorcycle", "bus", "truck", "traffic light", "stop sign")
    }
}
