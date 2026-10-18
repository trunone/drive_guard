package com.example.driveguard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/** Draws detections on top of the (letterboxed, FIT_CENTER) camera preview. */
class OverlayView(c: Context, a: AttributeSet?) : View(c, a) {
    private var res: FrameResult? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f }

    fun show(r: FrameResult) { res = r; invalidate() }
    fun clear() { res = null; invalidate() }

    override fun onDraw(c: Canvas) {
        val r = res ?: return
        val s = min(width / r.imgW.toFloat(), height / r.imgH.toFloat())
        val dx = (width - r.imgW * s) / 2f
        val dy = (height - r.imgH * s) / 2f
        // "ego lane" corridor used by the analyzer
        p.color = 0x55FFFFFF; p.strokeWidth = 2f
        c.drawRect(dx + 0.30f * r.imgW * s, dy, dx + 0.70f * r.imgW * s, dy + r.imgH * s, p)

        for (d in r.dets) {
            val dSev = r.hazards.firstOrNull { h -> h.box != null && (h.box === d.box || h.box == d.box) }?.severity
            val col = when (dSev) {
                Severity.CRITICAL -> 0xFFE53935
                Severity.WARNING -> 0xFFFFA000
                Severity.INFO -> 0xFF43A047
                null -> 0xAAFFFFFF
            }.toInt()
            p.color = col
            p.strokeWidth = if (dSev != null) 8f else 3f
            val rect = RectF(dx + d.box.left * s, dy + d.box.top * s, dx + d.box.right * s, dy + d.box.bottom * s)
            c.drawRect(rect, p)
            t.color = col
            c.drawText("${d.label} ${(d.score * 100).toInt()}%", rect.left, rect.top - 6f, t)
        }
    }
}
