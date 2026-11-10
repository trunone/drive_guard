package com.github.trunone.drive_guard

import android.content.Context
import android.graphics.Bitmap
import com.github.trunone.drive_guard.vision.MirageDetector
import com.github.trunone.drive_guard.vision.StageOneDetector

/**
 * Object detector using Cactus Compute Mirage (with StageOneDetector fallback).
 * Runs synchronously on the analysis thread.
 */
class HazardDetector(context: Context) {
    private val mirageDetector = MirageDetector(context, MODEL)
    private val stageOneDetector: StageOneDetector? = try {
        StageOneDetector(context)
    } catch (e: Throwable) {
        null
    }

    init {
        if (!mirageDetector.isModelLoaded && stageOneDetector == null) {
            throw IllegalStateException("Neither Mirage model ($MODEL) nor fallback model (${StageOneDetector.DEFAULT_MODEL_NAME}) could be loaded.")
        }
    }

    fun detect(bmp: Bitmap): List<Det> {
        if (mirageDetector.isModelLoaded) {
            return mirageDetector.detectObjects(bmp)
        }
        return stageOneDetector?.detectAll(bmp)?.map { detection ->
            Det("traffic light", detection.confidence, detection.box)
        } ?: emptyList()
    }

    fun close() {
        mirageDetector.close()
        stageOneDetector?.close()
    }

    companion object {
        const val MODEL = MirageDetector.DEFAULT_MODEL_NAME
        const val FALLBACK_MODEL = StageOneDetector.DEFAULT_MODEL_NAME
        val RELEVANT = setOf("person", "bicycle", "car", "motorcycle", "bus", "truck", "traffic light", "stop sign")
    }
}
