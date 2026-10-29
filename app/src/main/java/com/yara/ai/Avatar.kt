package com.yara.ai

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

val Pink = Color(0xFFFF4D8D)
val BgDark = Color(0xFF0B0F14)
val Mine = Color(0xFF7A1F4D)
val Theirs = Color(0xFF1F2933)

@Composable
fun YaraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Pink, secondary = Color(0xFFB388FF), background = BgDark, surface = Color(0xFF151B23),
            surfaceVariant = Color(0xFF1B222C), onBackground = Color.White, onSurface = Color.White, onPrimary = Color.White
        ), content = content
    )
}

/** Animated 2D anime-style Yara: blinking, breathing, talking mouth and mood-driven brows/eyes/blush. */
@Composable
fun YaraAvatar(mood: String, speaking: Boolean, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "yara")
    val breathe by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse), label = "br")
    val blinkT by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3800, easing = LinearEasing), RepeatMode.Restart), label = "bl")
    val talk by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(200, easing = LinearEasing), RepeatMode.Reverse), label = "tk")

    Canvas(modifier.aspectRatio(1f)) {
        val s = size.minDimension
        val cx = size.width / 2
        val cy = size.height / 2 + breathe * s * 0.01f
        val skin = Color(0xFFFFDCC8)
        val hair = Color(0xFF8A2F2F)
        val hairDark = Color(0xFF5E1F22)
        val open = when { blinkT > 0.95f -> 0.08f; mood == "sleepy" -> 0.45f; else -> 1f }

        drawCircle(Brush.verticalGradient(listOf(Color(0xFF3A1C4A), Color(0xFF120E1C))), radius = s / 2, center = Offset(size.width / 2, size.height / 2))
        drawRoundRect(hairDark, Offset(cx - 0.36f * s, cy - 0.30f * s), Size(0.72f * s, 0.85f * s), CornerRadius(0.3f * s))
        drawOval(Color(0xFF1B1B26), Offset(cx - 0.38f * s, cy + 0.36f * s), Size(0.76f * s, 0.5f * s))
        drawRect(skin, Offset(cx - 0.06f * s, cy + 0.24f * s), Size(0.12f * s, 0.16f * s))
        drawOval(skin, Offset(cx - 0.23f * s, cy - 0.28f * s), Size(0.46f * s, 0.58f * s))
        drawArc(hair, 180f, 180f, true, Offset(cx - 0.27f * s, cy - 0.40f * s), Size(0.54f * s, 0.46f * s))
        val bang = Path().apply {
            moveTo(cx - 0.05f * s, cy - 0.30f * s); lineTo(cx - 0.17f * s, cy - 0.03f * s); lineTo(cx + 0.02f * s, cy - 0.20f * s); close()
        }
        drawPath(bang, hair)
        val bang2 = Path().apply {
            moveTo(cx + 0.02f * s, cy - 0.30f * s); lineTo(cx + 0.19f * s, cy - 0.05f * s); lineTo(cx - 0.01f * s, cy - 0.20f * s); close()
        }
        drawPath(bang2, hair)
        drawRoundRect(hair, Offset(cx - 0.29f * s, cy - 0.22f * s), Size(0.07f * s, 0.52f * s), CornerRadius(0.04f * s))
        drawRoundRect(hair, Offset(cx + 0.22f * s, cy - 0.22f * s), Size(0.07f * s, 0.52f * s), CornerRadius(0.04f * s))

        val eyeY = cy - 0.02f * s
        val happyEyes = mood == "love" || mood == "happy" || mood == "excited"
        for (dx in listOf(-0.1f, 0.1f)) {
            val ex = cx + dx * s
            if (happyEyes) {
                drawArc(Color(0xFF2B1B1B), 200f, 140f, false, Offset(ex - 0.06f * s, eyeY - 0.03f * s), Size(0.12f * s, 0.08f * s), style = Stroke(s * 0.012f, cap = StrokeCap.Round))
            } else {
                drawOval(Color.White, Offset(ex - 0.065f * s, eyeY - 0.045f * s * open), Size(0.13f * s, 0.09f * s * open))
                drawOval(Color(0xFFFFC400), Offset(ex - 0.042f * s, eyeY - 0.042f * s * open), Size(0.084f * s, 0.084f * s * open))
                drawOval(Color(0xFF3B1A00), Offset(ex - 0.016f * s, eyeY - 0.016f * s * open), Size(0.032f * s, 0.032f * s * open))
                drawCircle(Color.White, 0.008f * s, Offset(ex + 0.012f * s, eyeY - 0.015f * s * open))
                drawArc(Color(0xFF2B1B1B), 200f, 140f, false, Offset(ex - 0.07f * s, eyeY - 0.055f * s), Size(0.14f * s, 0.1f * s), style = Stroke(s * 0.009f, cap = StrokeCap.Round))
            }
            val k = when (mood) { "angry" -> 1f; "sad", "worried" -> -1f; else -> 0f }
            val sign = if (dx < 0) 1f else -1f
            val by = eyeY - 0.085f * s
            val y1 = by - k * sign * 0.015f * s
            val y2 = by + k * sign * 0.015f * s
            drawLine(hairDark, Offset(ex - 0.06f * s, y1), Offset(ex + 0.06f * s, y2), s * 0.008f, StrokeCap.Round)
            if (mood == "shy" || mood == "love" || mood == "flirty")
                drawOval(Color(0x66FF4D6D), Offset(ex - 0.055f * s, eyeY + 0.07f * s), Size(0.11f * s, 0.05f * s))
        }

        val my = cy + 0.16f * s
        val mw = 0.12f * s
        val stroke = Stroke(s * 0.008f, cap = StrokeCap.Round)
        if (speaking) {
            drawOval(Color(0xFF7A1030), Offset(cx - 0.035f * s, my - 0.01f * s), Size(0.07f * s, (0.012f + 0.035f * talk) * s))
        } else when (mood) {
            "sad", "worried", "angry" -> drawArc(Color(0xFFB03060), 200f, 140f, false, Offset(cx - mw / 2, my), Size(mw, 0.06f * s), style = stroke)
            "neutral", "sleepy" -> drawLine(Color(0xFFB03060), Offset(cx - 0.04f * s, my + 0.02f * s), Offset(cx + 0.04f * s, my + 0.02f * s), s * 0.008f, StrokeCap.Round)
            else -> drawArc(Color(0xFFB03060), 20f, 140f, false, Offset(cx - mw / 2, my - 0.03f * s), Size(mw, 0.08f * s), style = stroke)
        }
    }
}
