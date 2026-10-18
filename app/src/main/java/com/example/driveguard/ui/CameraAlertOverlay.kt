package com.example.driveguard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.driveguard.data.EnforcementCameraType

@Composable
fun CameraAlertOverlay(
    alertState: CameraAlertState?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = alertState != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        if (alertState != null) {
            CameraAlertCard(alertState = alertState)
        }
    }
}

@Composable
fun CameraAlertCard(alertState: CameraAlertState) {
    val isCritical = alertState.isCriticalViolation
    val cardBg = if (isCritical) Color(0xDDE53935) else Color(0xDD1E88E5)
    val borderColor = if (isCritical) Color.Yellow else Color.White

    Surface(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .padding(8.dp)
            .border(2.dp, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TypeBadge(type = alertState.camera.type)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = getCameraTypeTitle(alertState.camera.type),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isCritical) {
                            Text(
                                text = "CRITICAL VIOLATION THREAT",
                                color = Color.Yellow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                if (alertState.camera.max_speed != null) {
                    SpeedLimitCircle(maxSpeed = alertState.camera.max_speed)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val distanceMeters = alertState.distanceMeters.coerceIn(0.0, 300.0)
            val progress = (distanceMeters / 300.0).toFloat()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Distance remaining:",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp
                )
                Text(
                    text = "${distanceMeters.toInt()} m",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = if (isCritical) Color.Yellow else Color.Cyan,
                trackColor = Color.Black.copy(alpha = 0.3f),
            )
        }
    }
}

@Composable
fun TypeBadge(type: EnforcementCameraType) {
    val symbol = when (type) {
        EnforcementCameraType.SPEED -> "📷"
        EnforcementCameraType.RED_LIGHT -> "🚦"
        EnforcementCameraType.COMBINED -> "📸"
        EnforcementCameraType.AVERAGE_SPEED_START -> "⏱️"
        EnforcementCameraType.AVERAGE_SPEED_END -> "⏱️"
    }
    Box(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = symbol, fontSize = 18.sp)
    }
}

@Composable
fun SpeedLimitCircle(maxSpeed: Int) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .background(Color.White, CircleShape)
            .border(3.dp, Color.Red, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$maxSpeed",
            color = Color.Black,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun getCameraTypeTitle(type: EnforcementCameraType): String {
    return when (type) {
        EnforcementCameraType.SPEED -> "Speed Camera Ahead"
        EnforcementCameraType.RED_LIGHT -> "Red Light Camera"
        EnforcementCameraType.COMBINED -> "Speed & Red Light Camera"
        EnforcementCameraType.AVERAGE_SPEED_START -> "Trajectcontrole Start"
        EnforcementCameraType.AVERAGE_SPEED_END -> "Trajectcontrole End"
    }
}
