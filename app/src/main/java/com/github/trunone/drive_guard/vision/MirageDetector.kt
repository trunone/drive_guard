package com.github.trunone.drive_guard.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.github.trunone.drive_guard.Det
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream

/**
 * Cactus Mirage Vision Detector
 * Runs the Cactus Compute Mirage vision-language model (`mirage.cact`) for scene perception
 * and multi-class object detection (traffic light, car, truck, bus, motorcycle, pedestrian, bicycle, stop sign).
 */
class MirageDetector(
    private val modelBytes: ByteArray?
) : AutoCloseable {

    constructor(context: Context, modelAssetPath: String = DEFAULT_MODEL_NAME) : this(
        loadModelBytes(context, modelAssetPath)
    )

    private var isLoaded = false

    init {
        if (modelBytes != null && modelBytes.isNotEmpty() && CactusNative.isAvailable) {
            val res = CactusNative.load(modelBytes)
            isLoaded = (res == 0)
        }
    }

    val isModelLoaded: Boolean
        get() = isLoaded

    /**
     * Runs object detection using Mirage on the input bitmap.
     * Parses the returned structured JSON and maps bounding boxes (scale 0-1000) to pixel coordinates.
     */
    fun detectObjects(bitmap: Bitmap, minConfidence: Float = CONFIDENCE_THRESHOLD): List<Det> {
        if (!isLoaded || !CactusNative.isAvailable) return emptyList()

        val width = bitmap.width
        val height = bitmap.height
        val rgbBytes = bitmapToRgbBytes(bitmap)

        val optionsJson = "{\"sections\":[\"objects\"],\"conf\":$minConfidence}"
        val resultJsonStr = CactusNative.describe(rgbBytes, width, height, optionsJson) ?: return emptyList()

        return parseDetections(resultJsonStr, width, height, minConfidence)
    }

    override fun close() {
        // Native cleanup if required
    }

    companion object {
        const val DEFAULT_MODEL_NAME = "mirage.cact"
        const val CONFIDENCE_THRESHOLD = 0.35f

        fun loadModelBytes(context: Context, modelAssetPath: String): ByteArray? {
            return try {
                val customFile = File(context.filesDir, modelAssetPath)
                if (customFile.exists() && customFile.length() > 0) {
                    FileInputStream(customFile).use { it.readBytes() }
                } else {
                    context.assets.open(modelAssetPath).use { it.readBytes() }
                }
            } catch (e: Exception) {
                null
            }
        }

        fun bitmapToRgbBytes(bmp: Bitmap): ByteArray {
            val w = bmp.width
            val h = bmp.height
            val pixels = IntArray(w * h)
            bmp.getPixels(pixels, 0, w, 0, 0, w, h)
            val rgb = ByteArray(w * h * 3)
            var offset = 0
            for (p in pixels) {
                rgb[offset++] = ((p shr 16) and 0xFF).toByte()
                rgb[offset++] = ((p shr 8) and 0xFF).toByte()
                rgb[offset++] = (p and 0xFF).toByte()
            }
            return rgb
        }

        fun parseDetections(
            jsonStr: String,
            imageWidth: Int,
            imageHeight: Int,
            minConfidence: Float
        ): List<Det> {
            val detections = mutableListOf<Det>()
            try {
                val root = JSONObject(jsonStr)
                val objects = root.optJSONArray("objects") ?: JSONArray()
                for (i in 0 until objects.length()) {
                    val obj = objects.getJSONObject(i)
                    val rawName = obj.optString("name", "").trim().lowercase()
                    val score = obj.optDouble("score", 0.0).toFloat()
                    if (score < minConfidence) continue

                    val bbox = obj.optJSONArray("bbox")
                    if (bbox != null && bbox.length() == 4) {
                        val x0 = bbox.getDouble(0).toFloat()
                        val y0 = bbox.getDouble(1).toFloat()
                        val x1 = bbox.getDouble(2).toFloat()
                        val y1 = bbox.getDouble(3).toFloat()

                        // Convert 0-1000 coordinate scale to actual image pixel coordinates
                        val left = (x0 / 1000f) * imageWidth
                        val top = (y0 / 1000f) * imageHeight
                        val right = (x1 / 1000f) * imageWidth
                        val bottom = (y1 / 1000f) * imageHeight

                        val label = mapLabelToRelevantClass(rawName)
                        if (label != null) {
                            detections.add(Det(label, score, RectF(left, top, right, bottom)))
                        }
                    }
                }
            } catch (e: Exception) {
                // Return whatever parsed successfully
            }
            return detections
        }

        /**
         * Maps open-vocabulary Mirage object descriptions to standard DriveGuard hazard labels.
         */
        fun mapLabelToRelevantClass(name: String): String? {
            return when {
                name.contains("traffic light") || name.contains("traffic signal") || name.contains("stoplight") -> "traffic light"
                name.contains("stop sign") -> "stop sign"
                name.contains("car") || name.contains("automobile") || name.contains("sedan") || name.contains("suv") || name.contains("van") || name.contains("taxi") -> "car"
                name.contains("truck") || name.contains("pickup") || name.contains("semi") -> "truck"
                name.contains("bus") -> "bus"
                name.contains("motorcycle") || name.contains("motorbike") || name.contains("scooter") -> "motorcycle"
                name.contains("person") || name.contains("pedestrian") || name.contains("walker") || name.contains("man") || name.contains("woman") || name.contains("child") -> "person"
                name.contains("bicycle") || name.contains("bike") || name.contains("cyclist") -> "bicycle"
                else -> null
            }
        }
    }
}
