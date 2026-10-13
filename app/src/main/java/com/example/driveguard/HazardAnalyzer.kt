package com.example.driveguard

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.tan

// ---- Tunable thresholds ----
private const val SAFE_HEADWAY_S = 2.0f   // warn below this time gap to the car ahead
private const val CRIT_HEADWAY_S = 1.0f
private const val CRIT_TTC_S = 2.5f       // time-to-collision
private val VEHICLE_WIDTH_M = mapOf("car" to 1.8f, "truck" to 2.5f, "bus" to 2.5f, "motorcycle" to 0.8f)
private val VRU_HEIGHT_M = mapOf("person" to 1.7f, "bicycle" to 1.1f)

/** Turns raw detections into driver hazards. Distances are rough monocular (pinhole) estimates. */
class HazardAnalyzer {
    @Volatile var hFovDeg = 66f

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
                ttc < CRIT_TTC_S -> out += Hazard("ttc", "Brake! Collision risk", Severity.CRITICAL, lead.box)
                gap != null && gap < CRIT_HEADWAY_S ->
                    out += Hazard("gap", "Too close to car ahead, ${"%.0f".format(d)} meters", Severity.CRITICAL, lead.box)
                gap != null && gap < SAFE_HEADWAY_S -> out += Hazard("gap", "Keep more distance", Severity.WARNING, lead.box)
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

        // 3) Traffic light state (majority vote over recent frames to avoid flicker)
        val tl = dets.filter { it.label == "traffic light" && cx(it) in 0.15f..0.85f && it.box.centerY() < 0.6f * h }
            .maxByOrNull { it.box.height() }
        colors.addLast(tl?.let { LightClassifier.classify(bmp, it.box) } ?: LightColor.UNKNOWN)
        while (colors.size > 7) colors.removeFirst()
        val stable = listOf(LightColor.RED, LightColor.YELLOW, LightColor.GREEN)
            .maxByOrNull { c -> colors.count { it == c } }
            ?.takeIf { s -> colors.count { it == s } >= 4 }
        val fast = (speed ?: 0f) > 6f
        when (stable) {
            LightColor.RED -> out += Hazard(
                "red", if (fast) "Red light ahead, slow down" else "Red light ahead",
                if (fast) Severity.CRITICAL else Severity.WARNING, tl?.box
            )
            LightColor.YELLOW -> out += Hazard("yellow", "Yellow light", Severity.WARNING, tl?.box)
            LightColor.GREEN -> out += Hazard("green", "Green light", Severity.INFO, tl?.box)
            else -> {}
        }

        // 4) Stop sign
        dets.firstOrNull { it.label == "stop sign" && cx(it) in 0.4f..0.95f && it.box.height() > 0.07f * h }
            ?.let { out += Hazard("stop", "Stop sign ahead", Severity.WARNING, it.box) }

        return out.sortedByDescending { it.severity.ordinal }
    }
}

/** Colors a detected traffic-light crop by counting bright, saturated red/yellow/green pixels. */
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
        val small = Bitmap.createScaledBitmap(cropped, 12, 24, true)
        if (cropped != bmp && cropped != small) {
            cropped.recycle()
        }

        val px = IntArray(12 * 24)
        small.getPixels(px, 0, 12, 0, 0, 12, 24)
        if (small != bmp && small != cropped) {
            small.recycle()
        }

        var r = 0; var y = 0; var g = 0
        val hsv = FloatArray(3)
        for (p in px) {
            Color.colorToHSV(p, hsv)
            if (hsv[1] < 0.45f || hsv[2] < 0.65f) continue
            val hue = hsv[0]
            when {
                hue < 18f || hue > 340f -> r++
                hue in 18f..65f -> y++
                hue in 90f..185f -> g++
            }
        }
        val best = maxOf(r, y, g)
        return when {
            best < 3 -> LightColor.UNKNOWN
            r == best -> LightColor.RED
            g == best -> LightColor.GREEN
            else -> LightColor.YELLOW
        }
    }
}
