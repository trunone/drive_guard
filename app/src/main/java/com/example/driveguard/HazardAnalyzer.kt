package com.example.driveguard

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import com.example.driveguard.vision.TrafficLightState
import com.example.driveguard.vision.TrafficLightVisionPipeline
import com.example.driveguard.vision.TrafficLightVisionPipeline.Companion.toLightColor
import kotlin.math.max
import kotlin.math.tan

private val VEHICLE_WIDTH_M = mapOf("car" to 1.8f, "truck" to 2.5f, "bus" to 2.5f, "motorcycle" to 0.8f)
private val VRU_HEIGHT_M = mapOf("person" to 1.7f, "bicycle" to 1.1f)

/** Turns raw detections into driver hazards. Distances are rough monocular (pinhole) estimates. */
class HazardAnalyzer(
    var visionPipeline: TrafficLightVisionPipeline? = null
) {
    @Volatile var hFovDeg = 66f
    @Volatile var safeHeadwayS = 2.0f   // warn below this time gap to the car ahead
    @Volatile var critHeadwayS = 1.0f   // critical time gap
    @Volatile var critTtcS = 2.5f       // critical time-to-collision

    private val colors = ArrayDeque<LightColor>()
    private var leadDist: Float? = null
    private var leadTime = 0L
    private var closing = 0f // m/s, smoothed

    fun analyze(bmp: Bitmap, dets: List<Det>, speed: Float?, nowMs: Long): List<Hazard> {
        val w = bmp.width.toFloat().coerceAtLeast(1f)
        val h = bmp.height.toFloat().coerceAtLeast(1f)
        val fov = hFovDeg.coerceIn(1f, 179f)
        val f = (w / 2f) / tan(Math.toRadians(fov / 2.0)).toFloat() // focal length in px
        val out = ArrayList<Hazard>()
        fun cx(d: Det) = d.box.centerX() / w
        fun inPath(d: Det) = cx(d) in 0.30f..0.70f && d.box.bottom > 0.45f * h
        fun nearPath(d: Det) = cx(d) in 0.15f..0.85f

        // 1) Car ahead: following distance + time to collision
        val lead = dets.filter { it.label in VEHICLE_WIDTH_M && inPath(it) && it.box.width() > 0f }.maxByOrNull { it.box.width() }
        if (lead != null) {
            val d = f * VEHICLE_WIDTH_M.getValue(lead.label) / lead.box.width()
            val prev = leadDist
            if (prev != null && (nowMs - leadTime) in 50L..1000L) {
                val v = (prev - d) / ((nowMs - leadTime) / 1000f)
                closing = 0.7f * closing + 0.3f * v
            } else closing = 0f
            leadDist = d
            leadTime = nowMs
            val ttc = if (closing > 0.5f) d / closing else Float.MAX_VALUE
            val gap = if (speed != null && speed >= 3f) d / speed else null
            when {
                ttc < critTtcS -> out += Hazard("ttc", "Brake! Collision risk", Severity.CRITICAL, lead.box)
                gap != null && gap < critHeadwayS ->
                    out += Hazard("gap", "Too close to car ahead, ${"%.0f".format(d)} meters", Severity.CRITICAL, lead.box)
                gap != null && gap < safeHeadwayS -> out += Hazard("gap", "Keep more distance", Severity.WARNING, lead.box)
                gap == null && d < 8f -> out += Hazard("gap", "Car very close ahead", Severity.WARNING, lead.box)
            }
        } else {
            leadDist = null
            closing = 0f
        }

        // 2) Pedestrians / cyclists
        val stopDist = max(12f, (speed ?: 0f) * 2.5f)
        for (p in dets.filter { it.label in VRU_HEIGHT_M && nearPath(it) && it.box.height() > 0f }) {
            val d = f * VRU_HEIGHT_M.getValue(p.label) / p.box.height()
            val who = if (p.label == "person") "Pedestrian" else "Cyclist"
            when {
                inPath(p) && d < stopDist / 2 -> out += Hazard("ped", "$who ahead, stop!", Severity.CRITICAL, p.box)
                inPath(p) && d < stopDist -> out += Hazard("ped", "$who ahead", Severity.WARNING, p.box)
                d < 20f -> out += Hazard("ped", "$who near the road", Severity.WARNING, p.box)
            }
        }

        // 3) Traffic light state
        var chosenTl: Det? = null
        var stable: LightColor?

        val pipelineResult = visionPipeline?.processFrame(bmp)
        if (pipelineResult != null && pipelineResult.state != TrafficLightState.UNKNOWN) {
            stable = pipelineResult.state.toLightColor()
        } else {
            val tlCandidates = dets.filter { it.label == "traffic light" && cx(it) in 0.05f..0.95f && it.box.centerY() < 0.6f * h }
                .sortedByDescending { it.box.height() * it.box.width() }
            var chosenColor = LightColor.UNKNOWN
            for (cand in tlCandidates) {
                val color = LightClassifier.classify(bmp, cand.box)
                if (color != LightColor.UNKNOWN) {
                    chosenColor = color
                    chosenTl = cand
                    break
                }
            }
            if (chosenTl == null && tlCandidates.isNotEmpty()) {
                chosenTl = tlCandidates.first()
            }
            colors.addLast(chosenColor)
            while (colors.size > 7) colors.removeFirst()
            stable = listOf(LightColor.RED, LightColor.YELLOW, LightColor.GREEN, LightColor.GREEN_LEFT, LightColor.GREEN_RIGHT)
                .maxByOrNull { c -> colors.count { it == c } }
                ?.takeIf { s -> colors.count { it == s } >= 4 }
        }
        val fast = (speed ?: 0f) > 6f
        val lightX = chosenTl?.let { cx(it) } ?: 0.5f
        val posDesc = when {
            lightX < 0.35f -> " on left"
            lightX > 0.65f -> " on right"
            else -> " ahead"
        }
        when (stable) {
            LightColor.RED -> out += Hazard(
                "red", if (fast) "Red light$posDesc, slow down" else "Red light$posDesc",
                if (fast) Severity.CRITICAL else Severity.WARNING, chosenTl?.box
            )
            LightColor.YELLOW -> out += Hazard("yellow", "Yellow light$posDesc", Severity.WARNING, chosenTl?.box)
            LightColor.GREEN -> out += Hazard("green", "Green light$posDesc", Severity.INFO, chosenTl?.box)
            LightColor.GREEN_LEFT -> out += Hazard("green_left", "Green left arrow$posDesc", Severity.INFO, chosenTl?.box)
            LightColor.GREEN_RIGHT -> out += Hazard("green_right", "Green right arrow$posDesc", Severity.INFO, chosenTl?.box)
            else -> {}
        }

        // 4) Stop sign
        dets.firstOrNull { it.label == "stop sign" && cx(it) in 0.4f..0.95f && it.box.height() > 0.07f * h }
            ?.let { out += Hazard("stop", "Stop sign ahead", Severity.WARNING, it.box) }

        return out.sortedByDescending { it.severity.ordinal }
    }
}

/** Colors a detected traffic-light crop using HSV analysis and spatial position weighting. */
object LightClassifier {
    fun classify(bmp: Bitmap, b: RectF): LightColor {
        if (bmp.width < 2 || bmp.height < 2) return LightColor.UNKNOWN
        val l = b.left.toInt().coerceIn(0, bmp.width - 2)
        val t = b.top.toInt().coerceIn(0, bmp.height - 2)
        val maxW = (bmp.width - l).coerceAtLeast(1)
        val maxH = (bmp.height - t).coerceAtLeast(1)
        val w = b.width().toInt().coerceIn(1, maxW)
        val h = b.height().toInt().coerceIn(1, maxH)

        val cropped = Bitmap.createBitmap(bmp, l, t, w, h)
        val gridW = 12
        val gridH = 24
        val small = Bitmap.createScaledBitmap(cropped, gridW, gridH, true)
        if (cropped != bmp && cropped != small) {
            cropped.recycle()
        }

        val px = IntArray(gridW * gridH)
        small.getPixels(px, 0, gridW, 0, 0, gridW, gridH)
        if (small != bmp && small != cropped) {
            small.recycle()
        }

        val isVertical = h >= w
        var rScore = 0f
        var yScore = 0f
        var gScore = 0f

        val hsv = FloatArray(3)
        for (yIdx in 0 until gridH) {
            val normY = (yIdx + 0.5f) / gridH
            for (xIdx in 0 until gridW) {
                val normX = (xIdx + 0.5f) / gridW
                val p = px[yIdx * gridW + xIdx]
                val redComponent = Color.red(p)
                val greenComponent = Color.green(p)
                val blueComponent = Color.blue(p)

                Color.colorToHSV(p, hsv)
                val hue = hsv[0]
                val sat = hsv[1]
                val valB = hsv[2]

                // Classify color candidates based on HSV + RGB overexposed/bright center rules
                var detectedColor: LightColor? = null

                // High brightness (>= 0.50)
                if (valB >= 0.50f) {
                    // Standard saturated check OR overexposed / bright light center check
                    val isRed = (sat >= 0.35f && (hue < 22f || hue > 335f)) ||
                            (valB >= 0.75f && redComponent > 180 && redComponent > greenComponent + 30 && redComponent > blueComponent + 30)
                    val isYellow = (sat >= 0.35f && hue in 22f..68f) ||
                            (valB >= 0.75f && redComponent > 180 && greenComponent > 150 && blueComponent < 150 && kotlin.math.abs(redComponent - greenComponent) < 60)
                    val isGreen = (sat >= 0.30f && hue in 80f..190f) ||
                            (valB >= 0.75f && greenComponent > 160 && greenComponent > redComponent + 20 && greenComponent > blueComponent + 20)

                    when {
                        isRed -> detectedColor = LightColor.RED
                        isGreen -> detectedColor = LightColor.GREEN
                        isYellow -> detectedColor = LightColor.YELLOW
                    }
                }

                if (detectedColor == null) continue

                // Compute spatial position weight
                val positionWeight = if (isVertical) {
                    when (detectedColor) {
                        LightColor.RED -> if (normY < 0.45f) 2.0f else 0.4f
                        LightColor.YELLOW -> if (normY in 0.25f..0.75f) 2.0f else 0.4f
                        LightColor.GREEN -> if (normY > 0.55f) 2.0f else 0.4f
                        else -> 1.0f
                    }
                } else {
                    // For horizontal traffic lights, red is standard on left, green on right; or green on either end for left/right turn green arrows.
                    when (detectedColor) {
                        LightColor.RED -> if (normX < 0.45f) 2.0f else 0.5f
                        LightColor.YELLOW -> if (normX in 0.25f..0.75f) 2.0f else 0.5f
                        LightColor.GREEN -> if (normX > 0.55f || normX < 0.45f) 1.8f else 0.5f
                        else -> 1.0f
                    }
                }

                val score = valB * positionWeight
                when (detectedColor) {
                    LightColor.RED -> rScore += score
                    LightColor.YELLOW -> yScore += score
                    LightColor.GREEN -> gScore += score
                    else -> {}
                }
            }
        }

        val maxScore = maxOf(rScore, yScore, gScore)
        if (maxScore < 2.0f) return LightColor.UNKNOWN

        if (rScore == maxScore && rScore > yScore * 1.2f && rScore > gScore * 1.2f) {
            return LightColor.RED
        }
        if (yScore == maxScore && yScore > rScore * 1.15f && yScore > gScore * 1.15f) {
            return LightColor.YELLOW
        }
        if (gScore == maxScore && gScore > rScore * 1.2f && gScore > yScore * 1.2f) {
            // Determine if the green light is a left or right turn arrow or standard green
            // by inspecting green pixel spatial distribution (left vs middle vs right subregions)
            var leftGreenPixels = 0
            var rightGreenPixels = 0
            var midGreenPixels = 0

            for (yIdx in 0 until gridH) {
                for (xIdx in 0 until gridW) {
                    val p = px[yIdx * gridW + xIdx]
                    Color.colorToHSV(p, hsv)
                    val hue = hsv[0]
                    val sat = hsv[1]
                    val valB = hsv[2]
                    val gComp = Color.green(p)
                    val rComp = Color.red(p)
                    val bComp = Color.blue(p)

                    val isGreenPixel = valB >= 0.50f && ((sat >= 0.30f && hue in 80f..190f) ||
                            (valB >= 0.75f && gComp > 160 && gComp > rComp + 20 && gComp > bComp + 20))

                    if (isGreenPixel) {
                        val normX = (xIdx + 0.5f) / gridW
                        when {
                            normX < 0.38f -> leftGreenPixels++
                            normX > 0.62f -> rightGreenPixels++
                            else -> midGreenPixels++
                        }
                    }
                }
            }

            val totalGreen = leftGreenPixels + rightGreenPixels + midGreenPixels
            if (totalGreen >= 3) {
                if (!isVertical) {
                    // For horizontal signal heads
                    if (leftGreenPixels > totalGreen * 0.55f && leftGreenPixels > rightGreenPixels * 2) {
                        return LightColor.GREEN_LEFT
                    }
                    if (rightGreenPixels > totalGreen * 0.55f && rightGreenPixels > leftGreenPixels * 2) {
                        return LightColor.GREEN_RIGHT
                    }
                } else {
                    // For vertical signal heads, arrow signals often have distinct horizontal asymmetry or offset
                    if (leftGreenPixels > rightGreenPixels + midGreenPixels + 2 && leftGreenPixels > rightGreenPixels * 2) {
                        return LightColor.GREEN_LEFT
                    }
                    if (rightGreenPixels > leftGreenPixels + midGreenPixels + 2 && rightGreenPixels > leftGreenPixels * 2) {
                        return LightColor.GREEN_RIGHT
                    }
                }
            }

            return LightColor.GREEN
        }

        return LightColor.UNKNOWN
    }
}
