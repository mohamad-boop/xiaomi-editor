package app.xeditor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val SWATCHES = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFF1C1C1E, 0xFF8E8E93, 0xFFE53935, 0xFFFF7043, 0xFFFFB300,
    0xFF43A047, 0xFF00ACC1, 0xFF1E88E5, 0xFF3949AB, 0xFF8E24AA, 0xFFD81B60, 0x00000000L,
).map { it.toInt() }

fun parseArgb(s: String?): Int? = s?.let { runCatching { android.graphics.Color.parseColor(it.trim()) }.getOrNull() }
fun argbHex(c: Int) = "#%08X".format(c)

@Composable
fun ColorPickerDialog(title: String, initial: Int, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var s by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }
    var a by remember { mutableFloatStateOf(android.graphics.Color.alpha(initial) / 255f) }
    val color = android.graphics.Color.HSVToColor((a * 255).toInt(), floatArrayOf(h, s, v))
    var hex by remember { mutableStateOf(argbHex(initial)) }
    fun set(c: Int) {
        val t = FloatArray(3); android.graphics.Color.colorToHSV(c, t)
        h = t[0]; s = t[1]; v = t[2]; a = android.graphics.Color.alpha(c) / 255f
        hex = argbHex(c)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Color.LightGray, Color.White, Color.LightGray)))
                        .background(Color(color)),
                )
                Text("Hue", style = MaterialTheme.typography.labelMedium)
                Box(
                    Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(
                        Brush.horizontalGradient((0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) }),
                    ),
                )
                Slider(h, { h = it; hex = argbHex(android.graphics.Color.HSVToColor((a * 255).toInt(), floatArrayOf(it, s, v))) }, valueRange = 0f..360f)
                Text("Saturation", style = MaterialTheme.typography.labelMedium)
                Slider(s, { s = it; hex = argbHex(android.graphics.Color.HSVToColor((a * 255).toInt(), floatArrayOf(h, it, v))) })
                Text("Brightness", style = MaterialTheme.typography.labelMedium)
                Slider(v, { v = it; hex = argbHex(android.graphics.Color.HSVToColor((a * 255).toInt(), floatArrayOf(h, s, it))) })
                Text("Opacity", style = MaterialTheme.typography.labelMedium)
                Slider(a, { a = it; hex = argbHex(android.graphics.Color.HSVToColor((it * 255).toInt(), floatArrayOf(h, s, v))) })
                OutlinedTextField(
                    hex,
                    { txt -> hex = txt; parseArgb(txt)?.let { c -> set(c); hex = txt } },
                    label = { Text("Hex (#AARRGGBB)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SWATCHES.take(7).forEach { Swatch(it) { set(it) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SWATCHES.drop(7).forEach { Swatch(it) { set(it) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(argbHex(color)) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Swatch(c: Int, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(CircleShape)
            .background(if (android.graphics.Color.alpha(c) == 0) Color.LightGray else Color(c))
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick),
    )
}

/** Small colour chip; checkerboard-ish grey stands in for transparent. */
@Composable
fun ColorChip(color: Int?, modifier: Modifier = Modifier) {
    Box(
        modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    color == null -> MaterialTheme.colorScheme.surfaceVariant
                    android.graphics.Color.alpha(color) == 0 -> Color.LightGray
                    else -> Color(color)
                },
            )
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
    )
}
