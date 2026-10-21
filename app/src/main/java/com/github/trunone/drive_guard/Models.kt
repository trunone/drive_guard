package com.github.trunone.drive_guard

import android.graphics.RectF

enum class Severity { INFO, WARNING, CRITICAL }
enum class LightColor { RED, YELLOW, GREEN, GREEN_LEFT, GREEN_RIGHT, UNKNOWN }

/** A detection; box is in upright-frame pixel coordinates. */
data class Det(val label: String, val score: Float, val box: RectF)

data class Hazard(val key: String, val message: String, val severity: Severity, val box: RectF? = null)

data class FrameResult(val dets: List<Det>, val hazards: List<Hazard>, val imgW: Int, val imgH: Int)
