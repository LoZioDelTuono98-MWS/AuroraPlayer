package com.aurora.player.ui.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Pannello "liquid glass": sfocatura reale di ciò che c'è dietro (backdrop blur),
 * riflesso speculare in alto, bordo luminoso che si accende sul battito.
 */
@Composable
fun LiquidGlass(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    accent: Color = Color.White,
    pulse: FloatState? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .hazeEffect(state = hazeState) {
                blurRadius = 20.dp
                backgroundColor = Color(0xFF0B0B12)
                tints = listOf(HazeTint(Color.White.copy(alpha = 0.05f)))
                noiseFactor = 0.07f
            }
            .drawWithContent {
                val p = pulse?.floatValue ?: 0f
                // velatura con riflesso di colore
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.07f), Color.Transparent, accent.copy(alpha = 0.06f + 0.14f * p)),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    )
                )
                // lucentezza superiore
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.12f),
                        0.5f to Color.White.copy(alpha = 0.015f),
                        1f to Color.Transparent,
                    )
                )
                // punto speculare
                val hl = Offset(size.width * 0.18f, 0f)
                val rr = size.minDimension * 1.1f
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), center = hl, radius = rr),
                    radius = rr,
                    center = hl,
                )
                drawContent()
                // bordo rifrangente
                val outline = shape.createOutline(size, layoutDirection, this)
                drawOutline(
                    outline,
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.05f), accent.copy(alpha = (0.25f + 0.5f * p).coerceAtMost(1f))),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                    style = Stroke(1.2.dp.toPx()),
                )
            },
        content = content,
    )
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    hazeState: HazeState,
    accent: Color,
    pulse: FloatState?,
    size: Dp = 46.dp,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f, label = "press")
    LiquidGlass(
        hazeState = hazeState,
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = CircleShape,
        accent = accent,
        pulse = pulse,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(size * 0.48f))
        }
    }
}
