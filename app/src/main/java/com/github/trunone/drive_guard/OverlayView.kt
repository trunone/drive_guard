package com.github.trunone.drive_guard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/** Draws detections and HUD visual guides on top of the camera preview. */
class OverlayView(c: Context, a: AttributeSet?) : View(c, a) {
    private var res: FrameResult? = null

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        isFakeBoldText = true
        color = Color.WHITE
    }

    private val corridorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0x663B82F6.toInt() // Subtle blue glow for ego lane corridor
        pathEffect = DashPathEffect(floatArrayOf(16f, 12f), 0f)
    }

    fun show(r: FrameResult) {
        res = r
        invalidate()
    }

    fun clear() {
        res = null
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val r = res ?: return
        val s = min(width / r.imgW.toFloat(), height / r.imgH.toFloat())
        val dx = (width - r.imgW * s) / 2f
        val dy = (height - r.imgH * s) / 2f

        // 1. Draw "Ego Lane" corridor guides with dashed aesthetic lines
        val laneLeft = dx + 0.30f * r.imgW * s
        val laneRight = dx + 0.70f * r.imgW * s
        val laneTop = dy
        val laneBottom = dy + r.imgH * s

        c.drawLine(laneLeft, laneTop, laneLeft, laneBottom, corridorPaint)
        c.drawLine(laneRight, laneTop, laneRight, laneBottom, corridorPaint)

        // 2. Draw Object Detections with modern rounded corners & pill labels
        for (d in r.dets) {
            val dSev = r.hazards.firstOrNull { h -> h.box != null && (h.box === d.box || h.box == d.box) }?.severity
            val mainColor = when (dSev) {
                Severity.CRITICAL -> 0xFFEF4444.toInt() // Modern vivid Red
                Severity.WARNING -> 0xFFF59E0B.toInt()  // Modern vivid Amber/Yellow
                Severity.INFO -> 0xFF10B981.toInt()     // Modern Emerald Green
                null -> 0xCCF3F4F6.toInt()              // Clean White/Grey
            }

            val rect = RectF(
                dx + d.box.left * s,
                dy + d.box.top * s,
                dx + d.box.right * s,
                dy + d.box.bottom * s
            )

            // Semi-transparent box fill for highlighted hazards
            if (dSev != null) {
                fillPaint.color = (mainColor and 0x00FFFFFF) or 0x22000000
                c.drawRoundRect(rect, 12f, 12f, fillPaint)
            }

            // Outer stroke box
            strokePaint.color = mainColor
            strokePaint.strokeWidth = if (dSev != null) 6f else 3f
            c.drawRoundRect(rect, 12f, 12f, strokePaint)

            // Label pill badge
            val labelText = "${d.label} ${(d.score * 100).toInt()}%"
            val textWidth = textPaint.measureText(labelText)
            val textHeight = textPaint.textSize
            val badgePaddingX = 12f
            val badgePaddingY = 8f

            val badgeRect = RectF(
                rect.left,
                (rect.top - textHeight - badgePaddingY * 2).coerceAtLeast(dy),
                rect.left + textWidth + badgePaddingX * 2,
                (rect.top).coerceAtLeast(dy + textHeight + badgePaddingY * 2)
            )

            // Fill badge background
            fillPaint.color = mainColor
            c.drawRoundRect(badgeRect, 8f, 8f, fillPaint)

            // Draw text inside badge
            textPaint.color = if (dSev != null || mainColor == 0xFFEF4444.toInt()) Color.WHITE else Color.BLACK
            c.drawText(
                labelText,
                badgeRect.left + badgePaddingX,
                badgeRect.bottom - badgePaddingY - 2f,
                textPaint
            )
        }
    }
}
