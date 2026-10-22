package com.github.trunone.drive_guard.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.abs

data class TrafficLightDetection(
    val box: RectF, // Coordinates (xmin, ymin, xmax, ymax) relative to the ROI frame
    val confidence: Float
)

/**
 * Stage 1 Inference — Object Detector Execution
 * Loads `yolov8n_traffic_detector.tflite` via GPU delegate (with CPU fallback).
 * Passes cropped ROI frame into the model, parses output array to extract bounding boxes
 * with prediction confidence > 65%, and prioritizes the bounding box closest to the horizontal
 * center line of the vehicle's vector.
 */
class StageOneDetector(
    private val interpreter: Interpreter?,
    private val gpuDelegate: GpuDelegate? = null
) : AutoCloseable {

    constructor(context: Context, modelAssetPath: String = DEFAULT_MODEL_NAME) : this(
        createInterpreter(context, modelAssetPath)
    )

    private constructor(pair: Pair<Interpreter?, GpuDelegate?>) : this(pair.first, pair.second)

    companion object {
        const val DEFAULT_MODEL_NAME = "yolov8n_traffic_detector.tflite"
        const val CONFIDENCE_THRESHOLD = 0.65f

        private fun createInterpreter(context: Context, modelAssetPath: String): Pair<Interpreter?, GpuDelegate?> {
            return try {
                val customFile = java.io.File(context.filesDir, modelAssetPath)
                val modelBuffer = if (customFile.exists() && customFile.length() > 0) {
                    val inputStream = FileInputStream(customFile)
                    val fileChannel = inputStream.channel
                    fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, customFile.length())
                } else {
                    val assetFileDescriptor = context.assets.openFd(modelAssetPath)
                    val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
                    val fileChannel = inputStream.channel
                    val startOffset = assetFileDescriptor.startOffset
                    val declaredLength = assetFileDescriptor.declaredLength
                    fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
                }

                var delegate: GpuDelegate?
                val options = Interpreter.Options()
                try {
                    val gpu = GpuDelegate()
                    options.addDelegate(gpu)
                    delegate = gpu
                } catch (e: Throwable) {
                    delegate = null
                }

                val interpreter = try {
                    Interpreter(modelBuffer, options)
                } catch (e: Throwable) {
                    delegate?.close()
                    delegate = null
                    Interpreter(modelBuffer, Interpreter.Options())
                }
                Pair(interpreter, delegate)
            } catch (e: Exception) {
                Pair(null, null)
            }
        }

        /**
         * Prioritizes the bounding box closest to the horizontal center line.
         */
        fun prioritize(detections: List<TrafficLightDetection>, centerLineX: Float): TrafficLightDetection? {
            return detections
                .filter { it.confidence > CONFIDENCE_THRESHOLD }
                .minByOrNull { abs(it.box.centerX() - centerLineX) }
        }
    }

    /**
     * Executes object detection on the cropped ROI frame.
     * Returns all detections with confidence > 65%.
     */
    fun detectAll(roiBitmap: Bitmap): List<TrafficLightDetection> {
        val interp = interpreter ?: return emptyList()

        val inputTensor = interp.getInputTensor(0)
        val inputShape = inputTensor.shape()
        val inputWidth = if (inputShape.size >= 4) inputShape[2] else 640
        val inputHeight = if (inputShape.size >= 4) inputShape[1] else 640

        val scaledBmp = if (roiBitmap.width != inputWidth || roiBitmap.height != inputHeight) {
            Bitmap.createScaledBitmap(roiBitmap, inputWidth, inputHeight, true)
        } else {
            roiBitmap
        }

        val imgData = ByteBuffer.allocateDirect(4 * inputWidth * inputHeight * 3).apply {
            order(ByteOrder.nativeOrder())
        }

        val intValues = IntArray(inputWidth * inputHeight)
        scaledBmp.getPixels(intValues, 0, inputWidth, 0, 0, inputWidth, inputHeight)

        for (pixelValue in intValues) {
            imgData.putFloat(((pixelValue shr 16) and 0xFF) / 255.0f)
            imgData.putFloat(((pixelValue shr 8) and 0xFF) / 255.0f)
            imgData.putFloat((pixelValue and 0xFF) / 255.0f)
        }

        if (scaledBmp != roiBitmap) {
            scaledBmp.recycle()
        }

        val outputTensor = interp.getOutputTensor(0)
        val outputShape = outputTensor.shape()

        val results = mutableListOf<TrafficLightDetection>()

        if (outputShape.size == 3) {
            val dim1 = outputShape[1]
            val dim2 = outputShape[2]

            if (dim1 <= 10 && dim2 > 10) {
                // Shape [1, 5, N] or [1, 4+C, N]
                val outputArray = Array(1) { Array(dim1) { FloatArray(dim2) } }
                interp.run(imgData, outputArray)
                val numAnchors = dim2
                val numCoordsAndClasses = dim1
                val rawOut = outputArray[0]

                for (i in 0 until numAnchors) {
                    val cx = rawOut[0][i]
                    val cy = rawOut[1][i]
                    val w = rawOut[2][i]
                    val h = rawOut[3][i]

                    var score = if (numCoordsAndClasses >= 5) rawOut[4][i] else 1.0f
                    for (c in 5 until numCoordsAndClasses) {
                        if (rawOut[c][i] > score) score = rawOut[c][i]
                    }

                    if (score > CONFIDENCE_THRESHOLD) {
                        val scaleX = roiBitmap.width.toFloat() / inputWidth
                        val scaleY = roiBitmap.height.toFloat() / inputHeight

                        val isNorm = cx <= 1.0f && cy <= 1.0f && w <= 1.0f && h <= 1.0f
                        val actualCx = if (isNorm) cx * roiBitmap.width else cx * scaleX
                        val actualCy = if (isNorm) cy * roiBitmap.height else cy * scaleY
                        val actualW = if (isNorm) w * roiBitmap.width else w * scaleX
                        val actualH = if (isNorm) h * roiBitmap.height else h * scaleY

                        val xmin = actualCx - actualW / 2f
                        val ymin = actualCy - actualH / 2f
                        val xmax = actualCx + actualW / 2f
                        val ymax = actualCy + actualH / 2f

                        results.add(TrafficLightDetection(RectF(xmin, ymin, xmax, ymax), score))
                    }
                }
            } else if (dim2 <= 10 && dim1 > 10) {
                // Shape [1, N, 5]
                val outputArray = Array(1) { Array(dim1) { FloatArray(dim2) } }
                interp.run(imgData, outputArray)
                val numAnchors = dim1
                val rawOut = outputArray[0]

                for (i in 0 until numAnchors) {
                    val cx = rawOut[i][0]
                    val cy = rawOut[i][1]
                    val w = rawOut[i][2]
                    val h = rawOut[i][3]

                    var score = if (dim2 >= 5) rawOut[i][4] else 1.0f
                    for (c in 5 until dim2) {
                        if (rawOut[i][c] > score) score = rawOut[i][c]
                    }

                    if (score > CONFIDENCE_THRESHOLD) {
                        val scaleX = roiBitmap.width.toFloat() / inputWidth
                        val scaleY = roiBitmap.height.toFloat() / inputHeight

                        val isNorm = cx <= 1.0f && cy <= 1.0f && w <= 1.0f && h <= 1.0f
                        val actualCx = if (isNorm) cx * roiBitmap.width else cx * scaleX
                        val actualCy = if (isNorm) cy * roiBitmap.height else cy * scaleY
                        val actualW = if (isNorm) w * roiBitmap.width else w * scaleX
                        val actualH = if (isNorm) h * roiBitmap.height else h * scaleY

                        val xmin = actualCx - actualW / 2f
                        val ymin = actualCy - actualH / 2f
                        val xmax = actualCx + actualW / 2f
                        val ymax = actualCy + actualH / 2f

                        results.add(TrafficLightDetection(RectF(xmin, ymin, xmax, ymax), score))
                    }
                }
            }
        }

        return results
    }

    /**
     * Executes object detection on the cropped ROI frame and returns the single prioritized detection
     * closest to the horizontal center line of the ROI frame.
     */
    fun detect(roiBitmap: Bitmap): TrafficLightDetection? {
        val detections = detectAll(roiBitmap)
        if (detections.isEmpty()) return null
        val centerLineX = roiBitmap.width / 2f
        return prioritize(detections, centerLineX)
    }

    override fun close() {
        interpreter?.close()
        gpuDelegate?.close()
    }
}
