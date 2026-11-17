package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisCyanLight
import com.example.ui.theme.JarvisElectricBlue

@Composable
fun AudioWaveVisualizer(
    isListening: Boolean,
    isSpeaking: Boolean,
    rmsLevel: Float,
    modifier: Modifier = Modifier
) {
    val barCount = 19
    val infiniteTransition = rememberInfiniteTransition(label = "wave_anim")

    // Oscillating wave pattern
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave_offset"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val centerIndex = (barCount - 1) / 2
            val distFromCenter = kotlin.math.abs(i - centerIndex).toFloat() / centerIndex
            val centerWeight = 1f - (distFromCenter * 0.5f)

            val baseHeightDp = when {
                isListening -> {
                    // Reactively scales with rmsLevel (0.0 to 1.0)
                    val dynamicFactor = (rmsLevel * 36f) + (waveOffset * 8f)
                    (8f + (dynamicFactor * centerWeight)).coerceIn(6f, 44f)
                }
                isSpeaking -> {
                    // Rhythmic speaking animation
                    val waveFactor = kotlin.math.sin((i * 0.45f) + (waveOffset * 6.28f))
                    (10f + (kotlin.math.abs(waveFactor).toFloat() * 26f * centerWeight)).coerceIn(6f, 38f)
                }
                else -> 4f
            }

            val barColor = when {
                isListening -> if (i % 2 == 0) JarvisCyan else JarvisCyanLight
                isSpeaking -> if (i % 2 == 0) JarvisElectricBlue else JarvisCyan
                else -> Color(0xFF162D54)
            }

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(baseHeightDp.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}
