package dev.maxxing.github.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.maxxing.github.ui.i18n.appStrings
import kotlin.math.abs
import kotlin.math.roundToInt

/** Converts HSV ([hue] 0..360, [saturation] 0..1, [value] 0..1) into an opaque ARGB Long `0xFFRRGGBB`. */
fun hsvToColorHex(hue: Float, saturation: Float, value: Float): Long {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val c = v * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = v - c
    val (r1, g1, b1) = when ((h / 60f).toInt().coerceIn(0, 5)) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val r = ((r1 + m) * 255f).roundToInt().coerceIn(0, 255).toLong()
    val g = ((g1 + m) * 255f).roundToInt().coerceIn(0, 255).toLong()
    val b = ((b1 + m) * 255f).roundToInt().coerceIn(0, 255).toLong()
    return 0xFF000000L or (r shl 16) or (g shl 8) or b
}

/** Converts an ARGB Long color into HSV (`Triple(hue 0..360, saturation 0..1, value 0..1)`). */
fun colorHexToHsv(colorHex: Long): Triple<Float, Float, Float> {
    val r = ((colorHex shr 16) and 0xFF) / 255f
    val g = ((colorHex shr 8) and 0xFF) / 255f
    val b = (colorHex and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val rawHue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    val hue = ((rawHue % 360f) + 360f) % 360f
    val saturation = if (max == 0f) 0f else (delta / max).coerceIn(0f, 1f)
    return Triple(hue, saturation, max.coerceIn(0f, 1f))
}

/** Parses a 6-digit `#RRGGBB` or `RRGGBB` hex string into an opaque `0xFFRRGGBB` color Long, or null if invalid. */
fun parseHexColorInput(raw: String): Long? {
    val cleaned = raw.trim().removePrefix("#").trim()
    if (cleaned.length != 6) return null
    val rgb = cleaned.toLongOrNull(16) ?: return null
    return 0xFF000000L or (rgb and 0xFFFFFFL)
}

/** Formats an ARGB Long color as an uppercase `#RRGGBB` string. */
fun formatSeedColorHex(colorHex: Long): String {
    val rgb = (colorHex and 0xFFFFFFL).toString(16).uppercase().padStart(6, '0')
    return "#$rgb"
}

private val HUE_RAINBOW_STOPS = listOf(
    Color(0xFFFF0000),
    Color(0xFFFFFF00),
    Color(0xFF00FF00),
    Color(0xFF00FFFF),
    Color(0xFF0000FF),
    Color(0xFFFF00FF),
    Color(0xFFFF0000)
)

/**
 * Interactive 2D Saturation/Value + Hue + Hex color picker dialog for selecting custom MD3E seed colors.
 */
@Composable
fun MaxxingColorPickerDialog(
    initialColorHex: Long,
    onDismiss: () -> Unit,
    onSelectColor: (Long) -> Unit
) {
    val initialHsv = remember(initialColorHex) { colorHexToHsv(initialColorHex) }
    var hue by remember(initialColorHex) { mutableStateOf(initialHsv.first) }
    var saturation by remember(initialColorHex) { mutableStateOf(initialHsv.second) }
    var value by remember(initialColorHex) { mutableStateOf(initialHsv.third) }
    var hexInput by remember(initialColorHex) { mutableStateOf(formatSeedColorHex(initialColorHex)) }
    var hexError by remember(initialColorHex) { mutableStateOf(false) }

    val currentColorHex = remember(hue, saturation, value) { hsvToColorHex(hue, saturation, value) }
    val currentColor = Color(currentColorHex)
    val strings = appStrings()

    fun syncHexFromHsv(nextH: Float, nextS: Float, nextV: Float) {
        hue = nextH
        saturation = nextS
        value = nextV
        hexInput = formatSeedColorHex(hsvToColorHex(nextH, nextS, nextV))
        hexError = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.customAccentColorTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(currentColor)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aa",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (currentColor.luminance() > 0.45f) Color.Black else Color.White
                        )
                    }
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { raw ->
                            hexInput = raw.take(7)
                            val parsed = parseHexColorInput(raw)
                            if (parsed != null) {
                                val (h, s, v) = colorHexToHsv(parsed)
                                hue = h
                                saturation = s
                                value = v
                                hexError = false
                            } else {
                                hexError = raw.trim().removePrefix("#").isNotEmpty()
                            }
                        },
                        label = { Text(strings.hexColorLabel) },
                        isError = hexError,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f)
                    )
                }

                // 2D Saturation (X) x Value (Y) gradient pad
                val pureHueColor = Color(hsvToColorHex(hue, 1f, 1f))
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val nextS = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                val nextV = (1f - offset.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                syncHexFromHsv(hue, nextS, nextV)
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val nextS = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                val nextV = (1f - change.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                syncHexFromHsv(hue, nextS, nextV)
                            }
                        }
                ) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.White, pureHueColor)
                        )
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black)
                        )
                    )
                    val cx = saturation * size.width
                    val cy = (1f - value) * size.height
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.65f),
                        radius = 10.dp.toPx(),
                        center = Offset(cx, cy),
                        style = Stroke(width = 3.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 8.dp.toPx(),
                        center = Offset(cx, cy),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // Hue rainbow bar
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val nextH = ((offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)) * 360f
                                syncHexFromHsv(nextH, saturation, value)
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val nextH = ((change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)) * 360f
                                syncHexFromHsv(nextH, saturation, value)
                            }
                        }
                ) {
                    drawRect(brush = Brush.horizontalGradient(HUE_RAINBOW_STOPS))
                    val hx = ((hue / 360f).coerceIn(0f, 1f)) * size.width
                    val cy = size.height / 2f
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.6f),
                        radius = 10.dp.toPx(),
                        center = Offset(hx, cy),
                        style = Stroke(width = 3.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 8.dp.toPx(),
                        center = Offset(hx, cy),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosen = parseHexColorInput(hexInput) ?: currentColorHex
                    onSelectColor(chosen)
                }
            ) {
                Text(strings.apply)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
