package com.example.driveguard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import android.os.SystemClock
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.driveguard.sensor.GForceData
import com.example.driveguard.sensor.GForcePoint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Glanceable G-Force Meter card displaying XY-plane g-force via a Radar Chart
 * and Z-axis g-force via a vertical bar meter defined strictly by gravity direction.
 */
@Composable
fun GForceMeterCard(
    data: GForceData,
    onCalibrate: () -> Unit = {},
    onResetPeaks: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xDD111827)),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .width(280.dp)
            .border(1.dp, Color(0xFF374151), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "G-FORCE METER",
                    color = Color(0xFF38BDF8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onCalibrate,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1F2937),
                            contentColor = Color(0xFFE5E7EB)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("CALIB", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onResetPeaks,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1F2937),
                            contentColor = Color(0xFF9CA3AF)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("RESET", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main display area: Radar Chart (XY) + Vertical Bar (Z)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Radar Chart for XY-Plane G-Force
                GForceRadarChart(
                    gx = data.gx,
                    gy = data.gy,
                    peakXy = data.peakXy,
                    trajectory = data.trajectory,
                    modifier = Modifier.size(140.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Bar Meter for Z-Axis G-Force
                GForceZBar(
                    gz = data.gz,
                    peakZMax = data.peakZMax,
                    peakZMin = data.peakZMin,
                    modifier = Modifier
                        .height(140.dp)
                        .width(95.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Summary Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1F2937), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "XY: ${"%.2f".format(data.gXyMag)}g (Pk ${"%.2f".format(data.peakXy)}g)",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = "Z: ${"%.2f".format(abs(data.gz))}g",
                    color = Color(0xFF10B981),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Radar chart drawing XY-plane horizontal g-force.
 * Center is 0g, outer boundary is 1.0g (or dynamic scaling for higher g-forces).
 */
@Composable
fun GForceRadarChart(
    gx: Float,
    gy: Float,
    peakXy: Float,
    trajectory: List<GForcePoint> = emptyList(),
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = min(size.width, size.height) / 2f - 24f
        val maxGScale = if (peakXy > 1.0f || abs(gx) > 1.0f || abs(gy) > 1.0f) 2.0f else 1.0f

        // Grid rings: 0.25g, 0.50g, 0.75g, 1.00g (scaled)
        val rings = listOf(0.25f, 0.50f, 0.75f, 1.0f)
        for (rRatio in rings) {
            val r = maxRadius * (rRatio / maxGScale)
            drawCircle(
                color = Color(0xFF374151),
                radius = r,
                center = center,
                style = Stroke(
                    width = if (rRatio == 1.0f) 1.5f else 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
            )
        }

        // Radar Spokes: Crosshair lines
        drawLine(
            color = Color(0xFF4B5563),
            start = Offset(center.x - maxRadius, center.y),
            end = Offset(center.x + maxRadius, center.y),
            strokeWidth = 1f
        )
        drawLine(
            color = Color(0xFF4B5563),
            start = Offset(center.x, center.y - maxRadius),
            end = Offset(center.x, center.y + maxRadius),
            strokeWidth = 1f
        )

        // Radar Polygon / Web along principal axes
        val currentX = center.x + (gx / maxGScale) * maxRadius
        // Gy is positive forward (up on canvas) -> minus in Y coordinate
        val currentY = center.y - (gy / maxGScale) * maxRadius

        val polyPath = Path().apply {
            moveTo(center.x, center.y - abs(gy.coerceAtLeast(0f) / maxGScale) * maxRadius) // Fwd
            lineTo(center.x + abs(gx.coerceAtLeast(0f) / maxGScale) * maxRadius, center.y) // Right
            lineTo(center.x, center.y + abs(gy.coerceAtMost(0f) / maxGScale) * maxRadius)  // Rear/Brake
            lineTo(center.x - abs(gx.coerceAtMost(0f) / maxGScale) * maxRadius, center.y)  // Left
            close()
        }
        drawPath(
            path = polyPath,
            color = Color(0x4038BDF8)
        )
        drawPath(
            path = polyPath,
            color = Color(0xFF38BDF8),
            style = Stroke(width = 1.5f)
        )

        // Peak XY Ring
        if (peakXy > 0.05f) {
            val peakR = maxRadius * (peakXy / maxGScale).coerceAtMost(1.0f)
            drawCircle(
                color = Color(0x80F59E0B),
                radius = peakR,
                center = center,
                style = Stroke(
                    width = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)
                )
            )
        }

        // 10-second Trajectory Trail (Older points have lighter/fainter line color)
        if (trajectory.size >= 2) {
            val nowMs = SystemClock.elapsedRealtime()
            for (i in 0 until trajectory.size - 1) {
                val p1 = trajectory[i]
                val p2 = trajectory[i + 1]

                val ageMs = (nowMs - p2.timestampMs).coerceIn(0L, 10_000L)
                val ageRatio = ageMs / 10_000f // 0.0 = newest/now, 1.0 = 10s old
                // Newest points -> alpha 0.85f (bright cyan), 10s old -> alpha 0.12f (faint light color)
                val lineAlpha = (0.85f * (1.0f - ageRatio)).coerceIn(0.12f, 0.85f)
                val lineColor = Color(0xFF38BDF8).copy(alpha = lineAlpha)

                val x1 = center.x + (p1.gx / maxGScale) * maxRadius
                val y1 = center.y - (p1.gy / maxGScale) * maxRadius
                val x2 = center.x + (p2.gx / maxGScale) * maxRadius
                val y2 = center.y - (p2.gy / maxGScale) * maxRadius

                drawLine(
                    color = lineColor,
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = 2.0f
                )
            }
        }

        // Live G Vector Line & Dot
        val clampedCurrentX = currentX.coerceIn(center.x - maxRadius, center.x + maxRadius)
        val clampedCurrentY = currentY.coerceIn(center.y - maxRadius, center.y + maxRadius)

        drawLine(
            color = Color(0xFF38BDF8),
            start = center,
            end = Offset(clampedCurrentX, clampedCurrentY),
            strokeWidth = 2.5f
        )
        drawCircle(
            color = Color(0xFF0EA5E9),
            radius = 6f,
            center = Offset(clampedCurrentX, clampedCurrentY)
        )
        drawCircle(
            color = Color.White,
            radius = 3f,
            center = Offset(clampedCurrentX, clampedCurrentY)
        )

        // Direction Labels
        drawContext.canvas.nativeCanvas.apply {
            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#9CA3AF")
                textSize = 20f
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
            }

            drawText("FWD", center.x, center.y - maxRadius - 6f, textPaint)
            drawText("BRK", center.x, center.y + maxRadius + 18f, textPaint)

            textPaint.textAlign = android.graphics.Paint.Align.LEFT
            drawText("RGT", center.x + maxRadius + 6f, center.y + 6f, textPaint)

            textPaint.textAlign = android.graphics.Paint.Align.RIGHT
            drawText("LFT", center.x - maxRadius - 6f, center.y + 6f, textPaint)
        }
    }
}

/**
 * Vertical bar meter showing Z-axis g-force aligned with gravity vector.
 * Displays 1.0g baseline marker, live vertical force level, and peak indicators.
 */
@Composable
fun GForceZBar(
    gz: Float,
    peakZMax: Float,
    peakZMin: Float,
    modifier: Modifier = Modifier
) {
    val displayGz = abs(gz)
    Box(
        modifier = modifier
            .background(Color(0xFF1F2937), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Z-GRAVITY",
                color = Color(0xFF9CA3AF),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )

            // Bar Canvas
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                val barWidth = 24.dp.toPx()
                val left = (size.width - barWidth) / 2f
                val top = 0f
                val bottom = size.height
                val height = bottom - top

                // Scale: 0.0g at bottom, 2.0g at top, 1.0g in middle
                val baselineY = top + height * 0.5f // 1.0g

                // Draw background track
                drawRoundRect(
                    color = Color(0xFF111827),
                    topLeft = Offset(left, top),
                    size = Size(barWidth, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                )

                // Fill level: calculate target Y for gz (0.0g at bottom, 2.0g at top)
                val clampedGz = displayGz.coerceIn(0.0f, 2.0f)
                val currentY = top + height * (1.0f - (clampedGz / 2.0f))

                // Dynamic Bar Color based on deviation from 1.0g
                val dev = abs(displayGz - 1.0f)
                val barColor = when {
                    dev < 0.2f -> Color(0xFF10B981) // Green
                    dev < 0.4f -> Color(0xFFF59E0B) // Amber
                    else -> Color(0xFFEF4444)        // Red
                }

                // Fill from bottom (0.0g) up to currentY level
                val fillHeight = (bottom - currentY).coerceAtLeast(2f)

                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(left, currentY),
                    size = Size(barWidth, fillHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                )

                // 1.0g Baseline Reference Line
                drawLine(
                    color = Color(0xCCFFFFFF),
                    start = Offset(left - 4f, baselineY),
                    end = Offset(left + barWidth + 4f, baselineY),
                    strokeWidth = 2f
                )

                // Active Live Level Indicator Cap Line
                drawLine(
                    color = Color.White,
                    start = Offset(left - 2f, currentY),
                    end = Offset(left + barWidth + 2f, currentY),
                    strokeWidth = 3f
                )

                // Peak Max Tick Line
                val clampedMax = peakZMax.coerceIn(0.0f, 2.0f)
                val peakMaxY = top + height * (1.0f - (clampedMax / 2.0f))
                drawLine(
                    color = Color(0xFFEF4444),
                    start = Offset(left, peakMaxY),
                    end = Offset(left + barWidth, peakMaxY),
                    strokeWidth = 2f
                )

                // Peak Min Tick Line
                val clampedMin = peakZMin.coerceIn(0.0f, 2.0f)
                val peakMinY = top + height * (1.0f - (clampedMin / 2.0f))
                drawLine(
                    color = Color(0xFF3B82F6),
                    start = Offset(left, peakMinY),
                    end = Offset(left + barWidth, peakMinY),
                    strokeWidth = 2f
                )

                // Scale Text
                drawContext.canvas.nativeCanvas.apply {
                    val labelPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#9CA3AF")
                        textSize = 18f
                        textAlign = android.graphics.Paint.Align.RIGHT
                    }
                    drawText("1.0g", left - 6f, baselineY + 6f, labelPaint)
                    drawText("2.0g", left - 6f, top + 14f, labelPaint)
                    drawText("0.0g", left - 6f, bottom - 4f, labelPaint)
                }
            }

            // Live Z-value
            Text(
                text = "${"%.2f".format(displayGz)} G",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}
