package com.example.driveguard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Glanceable Jetpack Compose Dashboard UI displaying real-time iVRI traffic light color,
 * countdown timer, and high-priority emergency vehicle override warnings.
 */
@Composable
fun TrafficLightDashboard(
    viewModel: TrafficLightViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    TrafficLightDashboardContent(state = state, modifier = modifier)
}

@Composable
fun TrafficLightDashboardContent(
    state: TrafficLightUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // High-priority Emergency Vehicle Override Warning Banner
        val emergencyActive = state.activeState?.emergencyOverrideActive == true
        AnimatedVisibility(
            visible = emergencyActive,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFD32F2F)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "🚨 EMERGENCY VEHICLE OVERRIDE ACTIVE - CLEAR LANE!",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Glanceable Signal Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xCC1E1E1E)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Active Traffic Light Color Indicator Circle
                val lightColor = when (state.activeState?.currentColor?.uppercase()) {
                    "RED" -> Color(0xFFE53935)
                    "AMBER", "YELLOW" -> Color(0xFFFFB300)
                    "GREEN" -> Color(0xFF43A047)
                    else -> Color.DarkGray
                }

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(lightColor)
                        .border(4.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val activeColorText = state.activeState?.currentColor ?: "SEARCH"
                    Text(
                        text = activeColorText.take(1),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Information & Countdown Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    if (state.isSearchingSignal || state.nearestIntersection == null) {
                        Text(
                            text = "Searching for nearest iVRI signal...",
                            color = Color.LightGray,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        val distanceStr = state.distanceMeters?.let { "%.0fm".format(it) } ?: ""
                        Text(
                            text = "Signal: ${state.nearestIntersection.id} ($distanceStr)",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        val stateText = state.activeState?.currentColor ?: "ACTIVE"
                        Text(
                            text = "State: $stateText",
                            color = lightColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Countdown Timer Display
                if (state.activeState != null && state.activeState.countdownSeconds > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF333333))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${state.activeState.countdownSeconds}s",
                            color = Color.Yellow,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
