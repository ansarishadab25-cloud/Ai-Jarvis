package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.AssistantState
import com.example.ui.theme.JarvisAlertRed
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisCyanGlow
import com.example.ui.theme.JarvisCyanLight
import com.example.ui.theme.JarvisElectricBlue
import com.example.ui.theme.JarvisSurfaceVariant

@Composable
fun JarvisArcReactor(
    state: AssistantState,
    rmsLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isListening = state is AssistantState.Listening
    val isSpeaking = state is AssistantState.Speaking
    val isProcessing = state is AssistantState.Processing
    val isError = state is AssistantState.Error

    val infiniteTransition = rememberInfiniteTransition(label = "reactor_anim")

    // Continuous rotation for outer reactor rings
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isListening) 4000 else if (isProcessing) 2000 else 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_rotation"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isListening) 3000 else if (isProcessing) 1500 else 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_rotation"
    )

    // Breathing pulse for idle & speaking
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.25f else if (isSpeaking) 1.15f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isListening) 600 else 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val activeColor = when {
        isError -> JarvisAlertRed
        isListening -> JarvisCyan
        isSpeaking -> JarvisCyanLight
        isProcessing -> JarvisElectricBlue
        else -> JarvisCyan.copy(alpha = 0.85f)
    }

    Box(
        modifier = modifier
            .size(240.dp)
            .testTag("jarvis_arc_reactor_container"),
        contentAlignment = Alignment.Center
    ) {
        // Glowing background canvas with dynamic RMS aura and sci-fi arc segments
        Canvas(
            modifier = Modifier
                .size(230.dp)
                .rotate(outerRotation)
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.width / 2f - 16.dp.toPx()
            val dynamicRadius = baseRadius + (rmsLevel * 20.dp.toPx())

            // Ambient background pulse
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        activeColor.copy(alpha = if (isListening) 0.35f else 0.12f),
                        activeColor.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = dynamicRadius * pulseScale
                ),
                radius = dynamicRadius * pulseScale
            )

            // Segmented Arc Reactor Outer Ring
            val segments = 6
            val sweep = 42f
            for (i in 0 until segments) {
                val startAngle = i * 60f + 8f
                drawArc(
                    color = activeColor.copy(alpha = 0.8f),
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(center.x - baseRadius, center.y - baseRadius),
                    size = androidx.compose.ui.geometry.Size(baseRadius * 2, baseRadius * 2),
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Inner counter-rotating tech ring
        Canvas(
            modifier = Modifier
                .size(175.dp)
                .rotate(innerRotation)
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val ringRadius = size.width / 2f - 10.dp.toPx()

            // Dashed cyan inner ring
            val subSegments = 12
            for (i in 0 until subSegments) {
                drawArc(
                    color = JarvisCyanLight.copy(alpha = if (i % 2 == 0) 0.9f else 0.4f),
                    startAngle = i * 30f + 4f,
                    sweepAngle = 18f,
                    useCenter = false,
                    topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                    size = androidx.compose.ui.geometry.Size(ringRadius * 2, ringRadius * 2),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Center Button: Tappable Arc Core
        Box(
            modifier = Modifier
                .size(105.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            JarvisSurfaceVariant,
                            JarvisBackground
                        )
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = activeColor),
                    onClick = onClick
                )
                .testTag("microphone_main_button"),
            contentAlignment = Alignment.Center
        ) {
            // Core rim
            Canvas(modifier = Modifier.size(105.dp)) {
                drawCircle(
                    color = activeColor,
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }

            // Central icon
            when {
                isListening -> {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "माइक सक्रिय (Listening)",
                        tint = JarvisCyan,
                        modifier = Modifier.size(46.dp)
                    )
                }
                isSpeaking -> {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "जार्विस बोल रहा है (Speaking)",
                        tint = JarvisCyanLight,
                        modifier = Modifier.size(46.dp)
                    )
                }
                isProcessing -> {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "प्रोसेसिंग (Processing)",
                        tint = JarvisElectricBlue,
                        modifier = Modifier.size(46.dp)
                    )
                }
                isError -> {
                    Icon(
                        imageVector = Icons.Default.MicOff,
                        contentDescription = "माइक त्रुटि (Microphone Error)",
                        tint = JarvisAlertRed,
                        modifier = Modifier.size(44.dp)
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "बोलने के लिए दबाएं (Tap to Speak)",
                        tint = JarvisCyan,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }
        }
    }
}
