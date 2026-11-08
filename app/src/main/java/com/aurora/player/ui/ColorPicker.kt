package com.aurora.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Presets = listOf(
    0xFFFFFFFF, 0xFF9E9E9E, 0xFF000000, 0xFFFF1744, 0xFFFF4FA3, 0xFFD500F9,
    0xFF8A5CFF, 0xFF3D5AFE, 0xFF00B0FF, 0xFF00E5FF, 0xFF1DE9B6, 0xFF00E676,
    0xFFC6FF00, 0xFFFFEA00, 0xFFFFC400, 0xFFFF6D00, 0xFF1B0B3A, 0xFF0A2A5E,
).map { Color(it) }

/** Riga di cerchi colorati con anteprima del gradiente: tocca un cerchio per cambiarlo. */
@Composable
fun ColorStopsEditor(label: String, colors: List<Int>, onChange: (List<Int>) -> Unit) {
    var editing by remember { mutableIntStateOf(-1) }
    Column {
        Text(label, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            colors.forEachIndexed { i, c ->
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                        .clickable { editing = i }
                )
                Spacer(Modifier.width(10.dp))
            }
            if (colors.size > 1) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(Brush.horizontalGradient(colors.map { Color(it) }))
                )
            }
        }
    }
    if (editing in colors.indices) {
        val idx = editing
        ColorPickerDialog(
            initial = Color(colors[idx]),
            onDismiss = { editing = -1 },
            onConfirm = { picked ->
                onChange(colors.toMutableList().also { it[idx] = picked.toArgb() })
                editing = -1
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(initial: Color, onDismiss: () -> Unit, onConfirm: (Color) -> Unit) {
    val start = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    val current = Color.hsv(hue.coerceIn(0f, 360f), sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f))

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF16161E),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("Scegli il colore") },
        confirmButton = { TextButton(onClick = { onConfirm(current) }) { Text("Ok", color = Color.White) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla", color = Color.White.copy(alpha = 0.7f)) } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Quadrato saturazione / luminosità
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.5f)
                        .clip(RoundedCornerShape(16.dp))
                        .pointerInput(Unit) {
                            fun set(o: Offset) {
                                sat = (o.x / size.width).coerceIn(0f, 1f)
                                value = 1f - (o.y / size.height).coerceIn(0f, 1f)
                            }
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                set(down.position)
                                drag(down.id) { set(it.position); it.consume() }
                            }
                        }
                        .drawBehind {
                            drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue.coerceIn(0f, 360f), 1f, 1f))))
                            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                            val p = Offset(sat * size.width, (1f - value) * size.height)
                            drawCircle(Color.Black.copy(alpha = 0.4f), 11.dp.toPx(), p, style = Stroke(4.dp.toPx()))
                            drawCircle(Color.White, 10.dp.toPx(), p, style = Stroke(2.5.dp.toPx()))
                        }
                )
                // Barra della tinta
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .pointerInput(Unit) {
                            fun set(o: Offset) { hue = (o.x / size.width).coerceIn(0f, 1f) * 360f }
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                set(down.position)
                                drag(down.id) { set(it.position); it.consume() }
                            }
                        }
                        .drawBehind {
                            drawRect(Brush.horizontalGradient((0..6).map { Color.hsv((it * 60f).coerceAtMost(360f), 1f, 1f) }))
                            val x = hue / 360f * size.width
                            drawCircle(Color.White, size.height / 2f - 2.dp.toPx(), Offset(x, size.height / 2f), style = Stroke(3.dp.toPx()))
                        }
                )
                // Colori rapidi
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Presets.forEach { p ->
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(p)
                                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                .clickable {
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(p.toArgb(), hsv)
                                    hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                                }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(current))
                    Spacer(Modifier.width(12.dp))
                    Text("#%06X".format(current.toArgb() and 0xFFFFFF), fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
                }
            }
        },
    )
}
