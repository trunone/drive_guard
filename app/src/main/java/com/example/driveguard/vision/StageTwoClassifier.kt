package com.example.driveguard.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

enum class TrafficLightState {
    RED,
    AMBER,
    GREEN,
    LEFT_ARROW_GREEN,
    RIGHT_ARROW_GREEN,
    UNKNOWN;

    val isArrow: Boolean
        get() = this == LEFT_ARROW_GREEN || this == RIGHT_ARROW_GREEN
}

data class ClassificationResult(
    val state: TrafficLightState,
    val probabilities: FloatArray, // [Red, Amber, Green, Left Arrow Green, Right Arrow Green]
    val confidence: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ClassificationResult
        if (state != other.state) return false
        if (!probabilities.contentEquals(other.probabilities)) return false
        if (confidence != other.confidence) return false
        return true
    }

    override fun hashCode(): Int {
        var result = state.hashCode()
        result = 31 * result + probabilities.contentHashCode()
        result = 31 * result + confidence.hashCode()
        return result
    }
}

/**
 * Stage 2 Inference — Crop-and-Classify Micro Engine
 * Loads `traffic_state_classifier.tflite`.
 * Implements coordinate translation from relative ROI bounding box to high-res frame coordinates,
 * crops precise sub-region from original high-resolution frame memory, resizes to micro-model input size (64x64),
 * and computes state probabilities for Red, Amber, Green, Left Arrow Green, Right Arrow Green.
 */
class StageTwoClassifier(
    private val interpreter: Interpreter?,
    private val gpuDelegate: GpuDelegate? = null,
    val inputSize: Int = DEFAULT_INPUT_SIZE
) : AutoCloseable {

    constructor(
        context: Context,
        modelAssetPath: String = DEFAULT_MODEL_NAME,
        inputSize: Int = DEFAULT_INPUT_SIZE
    ) : this(createInterpreter(context, modelAssetPath), inputSize = inputSize)

    private constructor(pair: Pair<Interpreter?, GpuDelegate?>, inputSize: Int) : this(pair.first, pair.second, inputSize)

    companion object {
        const val DEFAULT_MODEL_NAME = "traffic_state_classifier.tflite"
        const val DEFAULT_INPUT_SIZE = 64

        private fun createInterpreter(context: Context, modelAssetPath: String): Pair<Interpreter?, GpuDelegate?> {
            return try {
                val assetFileDescriptor = context.assets.openFd(modelAssetPath)
                val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
                val fileChannel = inputStream.channel
                val startOffset = assetFileDescriptor.startOffset
                val declaredLength = assetFileDescriptor.declaredLength
                val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

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
         * Translates relative ROI bounding box coordinates back to high-resolution original frame coordinates.
         * @param relativeBox Bounding box relative to ROI frame
         * @param roiOffsetLeft Left offset of ROI within original high-res frame
         * @param roiOffsetTop Top offset of ROI within original high-res frame
         * @param roiWidth Width of ROI within original high-res frame
         * @param roiHeight Height of ROI within original high-res frame
         * @param roiImageWidth Actual pixel width of ROI image bitmap passed into Stage 1
         * @param roiImageHeight Actual pixel height of ROI image bitmap passed into Stage 1
         */
        fun translateToOriginalFrame(
            relativeBox: RectF,
            roiOffsetLeft: Float = 0f,
            roiOffsetTop: Float = 0f,
            roiWidth: Float = 1f,
            roiHeight: Float = 1f,
            roiImageWidth: Int = 1,
            roiImageHeight: Int = 1
        ): RectF {
            val scaleX = if (roiImageWidth > 0) roiWidth / roiImageWidth.toFloat() else 1f
            val scaleY = if (roiImageHeight > 0) roiHeight / roiImageHeight.toFloat() else 1f

            val absLeft = roiOffsetLeft + (relativeBox.left * scaleX)
            val absTop = roiOffsetTop + (relativeBox.top * scaleY)
            val absRight = roiOffsetLeft + (relativeBox.right * scaleX)
            val absBottom = roiOffsetTop + (relativeBox.bottom * scaleY)

            return RectF(absLeft, absTop, absRight, absBottom)
        }

        /**
         * Crops a bounding sub-region from the high-resolution bitmap and resizes to micro-model input size.
         */
        fun cropAndResize(
            highResFrame: Bitmap,
            cropRect: RectF,
            targetWidth: Int = DEFAULT_INPUT_SIZE,
            targetHeight: Int = DEFAULT_INPUT_SIZE
        ): Bitmap? {
            val frameW = highResFrame.width
            val frameH = highResFrame.height

            val left = cropRect.left.toInt().coerceIn(0, frameW - 1)
            val top = cropRect.top.toInt().coerceIn(0, frameH - 1)
            val right = cropRect.right.toInt().coerceIn(left + 1, frameW)
            val bottom = cropRect.bottom.toInt().coerceIn(top + 1, frameH)

            val width = right - left
            val height = bottom - top

            if (width <= 0 || height <= 0) return null

            val cropped = Bitmap.createBitmap(highResFrame, left, top, width, height)
            val scaled = if (cropped.width != targetWidth || cropped.height != targetHeight) {
                Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true).also {
                    if (it != cropped) cropped.recycle()
                }
            } else {
                cropped
            }
            return scaled
        }
    }

    /**
     * Translates coordinates, crops sub-region from original high-res frame, and classifies state.
     */
    fun classifyFromRoiBox(
        highResFrame: Bitmap,
        relativeBox: RectF,
        roiOffsetLeft: Float = 0f,
        roiOffsetTop: Float = 0f,
        roiWidth: Float = highResFrame.width.toFloat(),
        roiHeight: Float = highResFrame.height.toFloat(),
        roiImageWidth: Int = highResFrame.width,
        roiImageHeight: Int = highResFrame.height
    ): ClassificationResult {
        val originalFrameRect = translateToOriginalFrame(
            relativeBox,
            roiOffsetLeft,
            roiOffsetTop,
            roiWidth,
            roiHeight,
            roiImageWidth,
            roiImageHeight
        )

        val croppedMicroImage = cropAndResize(highResFrame, originalFrameRect, inputSize, inputSize)
            ?: return ClassificationResult(TrafficLightState.UNKNOWN, FloatArray(5) { 0f }, 0f)

        try {
            return classifyCrop(croppedMicroImage)
        } finally {
            croppedMicroImage.recycle()
        }
    }

    /**
     * Runs micro-model inference on 64x64 cropped bitmap.
     */
    fun classifyCrop(croppedBmp: Bitmap): ClassificationResult {
        val interp = interpreter ?: return ClassificationResult(TrafficLightState.UNKNOWN, FloatArray(5) { 0f }, 0f)

        val inputTensor = interp.getInputTensor(0)
        val shape = inputTensor.shape()
        val targetW = if (shape.size >= 4) shape[2] else inputSize
        val targetH = if (shape.size >= 4) shape[1] else inputSize

        val scaledBmp = if (croppedBmp.width != targetW || croppedBmp.height != targetH) {
            Bitmap.createScaledBitmap(croppedBmp, targetW, targetH, true)
        } else {
            croppedBmp
        }

        val imgData = ByteBuffer.allocateDirect(4 * targetW * targetH * 3).apply {
            order(ByteOrder.nativeOrder())
        }

        val intValues = IntArray(targetW * targetH)
        scaledBmp.getPixels(intValues, 0, targetW, 0, 0, targetW, targetH)

        for (pixelValue in intValues) {
            imgData.putFloat(((pixelValue shr 16) and 0xFF) / 255.0f)
            imgData.putFloat(((pixelValue shr 8) and 0xFF) / 255.0f)
            imgData.putFloat((pixelValue and 0xFF) / 255.0f)
        }

        if (scaledBmp != croppedBmp) {
            scaledBmp.recycle()
        }

        val outputArray = Array(1) { FloatArray(5) }
        interp.run(imgData, outputArray)

        val probs = outputArray[0]
        var maxIdx = 0
        var maxVal = probs[0]
        for (i in 1 until probs.size) {
            if (probs[i] > maxVal) {
                maxVal = probs[i]
                maxIdx = i
            }
        }

        val state = when (maxIdx) {
            0 -> TrafficLightState.RED
            1 -> TrafficLightState.AMBER
            2 -> TrafficLightState.GREEN
            3 -> TrafficLightState.LEFT_ARROW_GREEN
            4 -> TrafficLightState.RIGHT_ARROW_GREEN
            else -> TrafficLightState.UNKNOWN
        }

        return ClassificationResult(state, probs, maxVal)
    }

    override fun close() {
        interpreter?.close()
        gpuDelegate?.close()
    }
}
