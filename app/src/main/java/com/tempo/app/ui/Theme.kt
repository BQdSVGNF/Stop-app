package com.tempo.app.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tempo.app.data.AppSettings
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// MARK: Styles

enum class ThemeStyle(val label: String, val isLight: Boolean = false, val mono: Boolean = false) {
    GRADIENT("Dégradé"),
    FLUTED("Verre cannelé"),
    HALFTONE("Trame", isLight = true),
    BLUEPRINT("Blueprint", mono = true),
    CONTOUR("Topographie"),
    ASCII("ASCII", mono = true);

    val fontFamily: FontFamily get() = if (mono) FontFamily.Monospace else FontFamily.Default

    companion object {
        fun from(name: String) = ThemeStyle.entries.firstOrNull { it.name == name } ?: GRADIENT
    }
}

// MARK: Palettes

enum class ThemePalette(val label: String, val colors: List<Color>, val dark: Color, accentOverride: Color? = null) {
    INDIGO("Indigo", listOf(Color(0xFF5C5CF2), Color(0xFFBF59F2), Color(0xFFFF5E8A)), Color(0xFF14123A)),
    SUNSET("Coucher de soleil", listOf(Color(0xFFFF5A36), Color(0xFFFFB13B), Color(0xFFFF3D7F)), Color(0xFF2A1220)),
    OCEAN("Océan", listOf(Color(0xFF1E5FE0), Color(0xFF26C6DA), Color(0xFF7C8CFF)), Color(0xFF081A33)),
    FOREST("Forêt", listOf(Color(0xFF1F7A4D), Color(0xFFB5E853), Color(0xFF26A69A)), Color(0xFF0E2A21), Color(0xFF2E9E5B)),
    ROSE("Rose", listOf(Color(0xFFE83E7A), Color(0xFFF7A1C4), Color(0xFFA54BD6)), Color(0xFF2A0F22)),
    AURORA("Aurore", listOf(Color(0xFF7C4DFF), Color(0xFF00E5A0), Color(0xFFFF4081)), Color(0xFF0C0C24)),
    MONO("Monochrome", listOf(Color(0xFF3A3A3C), Color(0xFFC7C7CC), Color(0xFF8E8E93)), Color(0xFF121212), Color(0xFF636366));

    val accent: Color = accentOverride ?: colors[0]

    companion object {
        fun from(name: String) = ThemePalette.entries.firstOrNull { it.name == name } ?: INDIGO
    }
}

/** Couleurs des tâches */
val TaskColors = linkedMapOf(
    "indigo" to Color(0xFF5E5CE6), "blue" to Color(0xFF007AFF), "teal" to Color(0xFF30B0C7),
    "mint" to Color(0xFF00C7BE), "green" to Color(0xFF34C759), "yellow" to Color(0xFFFFCC00),
    "orange" to Color(0xFFFF9500), "red" to Color(0xFFFF3B30), "pink" to Color(0xFFFF2D55),
    "purple" to Color(0xFFAF52DE),
)

fun taskColor(name: String): Color = TaskColors[name] ?: Color(0xFF5E5CE6)

val Orange = Color(0xFFFF9500)

@Composable
fun currentPalette(): ThemePalette = ThemePalette.from(AppSettings.palette)

@Composable
fun currentStyle(): ThemeStyle = ThemeStyle.from(AppSettings.style)

@Composable
fun TempoTheme(content: @Composable () -> Unit) {
    val palette = currentPalette()
    val dark = when (AppSettings.appearance) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val scheme = if (dark) darkColorScheme(
        primary = palette.accent, secondary = palette.colors[1], tertiary = palette.colors[2],
        background = Color(0xFF000000), surface = Color(0xFF1C1C1E), surfaceVariant = Color(0xFF2C2C2E),
        onBackground = Color.White, onSurface = Color.White, onSurfaceVariant = Color(0xFF98989F),
        outline = Color(0xFF3A3A3C), onPrimary = Color.White,
    ) else lightColorScheme(
        primary = palette.accent, secondary = palette.colors[1], tertiary = palette.colors[2],
        background = Color(0xFFF2F2F7), surface = Color.White, surfaceVariant = Color(0xFFE9E9EE),
        onBackground = Color.Black, onSurface = Color.Black, onSurfaceVariant = Color(0xFF8E8E93),
        outline = Color(0xFFD1D1D6), onPrimary = Color.White,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

// MARK: Fonds décoratifs

@Composable
fun ThemeBackground(style: ThemeStyle, palette: ThemePalette, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        when (style) {
            ThemeStyle.GRADIENT -> drawGradient(palette)
            ThemeStyle.FLUTED -> drawFluted(palette)
            ThemeStyle.HALFTONE -> drawHalftone(palette)
            ThemeStyle.BLUEPRINT -> drawBlueprint(palette)
            ThemeStyle.CONTOUR -> drawContour(palette)
            ThemeStyle.ASCII -> drawAscii(palette)
        }
    }
}

private fun DrawScope.drawGradient(p: ThemePalette) {
    drawRect(Brush.linearGradient(listOf(p.colors[0], p.colors[1]), Offset.Zero, Offset(size.width, size.height)))
    drawCircle(Color.White.copy(alpha = 0.12f), radius = size.height * 0.6f, center = Offset(size.width * 0.92f, size.height * 0.05f))
    val c = Offset(size.width * 0.05f, size.height * 1.0f)
    drawCircle(Brush.radialGradient(listOf(p.colors[2].copy(alpha = 0.45f), Color.Transparent), c, size.height * 0.6f),
        radius = size.height * 0.6f, center = c)
}

private fun DrawScope.drawFluted(p: ThemePalette) {
    drawRect(p.dark)
    val c = p.colors
    val blobs = listOf(
        Triple(Offset(0.18f, 0.30f), c[2], 0.55f), Triple(Offset(0.82f, 0.22f), c[1], 0.50f),
        Triple(Offset(0.52f, 0.62f), c[0], 0.60f), Triple(Offset(0.30f, 1.00f), c[1], 0.45f),
        Triple(Offset(0.88f, 0.95f), c[2], 0.45f),
    )
    blobs.forEach { (pos, color, r) ->
        val center = Offset(pos.x * size.width, pos.y * size.height)
        val radius = r * size.height
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0f)), center, radius),
            radius = radius, center = center)
    }
    val stripe = 10.dp.toPx()
    var x = 0f
    while (x < size.width) {
        drawRect(
            Brush.horizontalGradient(
                0f to Color.White.copy(alpha = 0.22f), 0.35f to Color.White.copy(alpha = 0.03f),
                0.7f to Color.Black.copy(alpha = 0.05f), 1f to Color.Black.copy(alpha = 0.28f),
                startX = x, endX = x + stripe,
            ),
            topLeft = Offset(x, 0f), size = Size(stripe, size.height),
        )
        drawRect(Color.White.copy(alpha = 0.28f), topLeft = Offset(x, 0f), size = Size(1f, size.height))
        x += stripe
    }
}

private fun DrawScope.drawHalftone(p: ThemePalette) {
    drawRect(Color(0xFFF5F0E6))
    val spacing = 8.dp.toPx()
    val maxRadius = spacing * 0.5f
    val cx = size.width * 0.72f
    val cy = size.height * 0.45f
    val reach = size.width * 0.55f
    var y = spacing / 2
    var row = 0
    while (y < size.height + spacing) {
        var x = if (row % 2 == 0) spacing / 2 else spacing
        while (x < size.width + spacing) {
            val d = hypot(x - cx, (y - cy) * 1.3f) / reach
            val r = maxRadius * max(0f, 1 - d)
            if (r > 0.8f) drawCircle(if (d < 0.45f) p.colors[0] else p.colors[2], r, Offset(x, y))
            x += spacing
        }
        y += spacing * 0.87f
        row++
    }
}

private fun DrawScope.drawBlueprint(p: ThemePalette) {
    drawRect(p.colors[0])
    fun grid(step: Float, alpha: Float) {
        val color = Color.White.copy(alpha = alpha)
        var x = 0f
        while (x <= size.width) { drawLine(color, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
        var y = 0f
        while (y <= size.height) { drawLine(color, Offset(0f, y), Offset(size.width, y), 1f); y += step }
    }
    grid(12.dp.toPx(), 0.10f)
    grid(48.dp.toPx(), 0.25f)

    val line = Color.White.copy(alpha = 0.85f)
    val h = size.height * 0.5f
    val left = size.width * 0.62f
    val top = size.height * 0.2f
    val w = min(h * 1.2f, size.width * 0.34f)
    drawRect(line, Offset(left, top), Size(w, h), style = Stroke(1.5f))
    val c = Offset(left + w / 2, top + h / 2)
    val r = h * 0.36f
    drawCircle(line, r, c, style = Stroke(1.5f))
    drawCircle(line, r / 2, c, style = Stroke(1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
    val dash = PathEffect.dashPathEffect(floatArrayOf(18f, 6f, 4f, 6f))
    drawLine(line, Offset(c.x - r * 1.3f, c.y), Offset(c.x + r * 1.3f, c.y), 1.2f, pathEffect = dash)
    drawLine(line, Offset(c.x, c.y - r * 1.3f), Offset(c.x, c.y + r * 1.3f), 1.2f, pathEffect = dash)
    val dy = top + h + 12.dp.toPx()
    drawLine(line, Offset(left, dy), Offset(left + w, dy), 1.2f)
    drawLine(line, Offset(left, dy - 8f), Offset(left, dy + 8f), 1.2f)
    drawLine(line, Offset(left + w, dy - 8f), Offset(left + w, dy + 8f), 1.2f)
}

private fun contourField(x: Double, y: Double): Double {
    val a = sin(x * 5.1 + 1.3) * cos(y * 4.3 - 0.7) * 0.6
    val b = sin((x + y) * 3.7) * 0.35
    val g1 = exp(-((x - 0.25) * (x - 0.25) + (y - 0.25) * (y - 0.25)) * 14)
    val g2 = exp(-((x - 0.85) * (x - 0.85) + (y - 0.45) * (y - 0.45)) * 10) * 1.2
    return a + b + g1 + g2
}

private fun DrawScope.drawContour(p: ThemePalette) {
    drawRect(p.dark)
    val cell = 5.dp.toPx()
    val scale = 320.dp.toPx()
    val cols = (size.width / cell).toInt() + 2
    val rows = (size.height / cell).toInt() + 2
    val v = DoubleArray(cols * rows)
    var lo = Double.MAX_VALUE
    var hi = -Double.MAX_VALUE
    for (j in 0 until rows) for (i in 0 until cols) {
        val value = contourField((i * cell / scale).toDouble(), (j * cell / scale).toDouble())
        v[j * cols + i] = value
        lo = min(lo, value); hi = max(hi, value)
    }
    val levels = 16
    for (level in 1 until levels) {
        val t = lo + (hi - lo) * level / levels
        val path = Path()
        fun seg(a: Offset, b: Offset) { path.moveTo(a.x, a.y); path.lineTo(b.x, b.y) }
        for (j in 0 until rows - 1) for (i in 0 until cols - 1) {
            val a = v[j * cols + i]; val b = v[j * cols + i + 1]
            val c = v[(j + 1) * cols + i + 1]; val d = v[(j + 1) * cols + i]
            var index = 0
            if (a >= t) index = index or 8
            if (b >= t) index = index or 4
            if (c >= t) index = index or 2
            if (d >= t) index = index or 1
            if (index == 0 || index == 15) continue
            val x0 = i * cell; val y0 = j * cell
            fun lerp(p0: Double, q0: Double) = ((t - p0) / (q0 - p0)).toFloat()
            val top = Offset(x0 + cell * lerp(a, b), y0)
            val right = Offset(x0 + cell, y0 + cell * lerp(b, c))
            val bottom = Offset(x0 + cell * lerp(d, c), y0 + cell)
            val left = Offset(x0, y0 + cell * lerp(a, d))
            when (index) {
                1, 14 -> seg(left, bottom)
                2, 13 -> seg(bottom, right)
                3, 12 -> seg(left, right)
                4, 11 -> seg(top, right)
                6, 9 -> seg(top, bottom)
                7, 8 -> seg(left, top)
                5 -> { seg(left, top); seg(bottom, right) }
                10 -> { seg(top, right); seg(left, bottom) }
            }
        }
        val major = level % 4 == 0
        drawPath(path, p.colors[1].copy(alpha = if (major) 0.9f else 0.4f), style = Stroke(if (major) 2.2f else 1.2f))
    }
}

private fun DrawScope.drawAscii(p: ThemePalette) {
    drawRect(Color(0xFF0A0A0A))
    var seed = 42L
    fun random(): Float {
        seed = (seed * 6364136223846793005L + 1442695040888963407L)
        return ((seed ushr 33) % 10_000).toFloat() / 10_000f
    }
    repeat(90) {
        val pos = Offset(random() * size.width, random() * size.height)
        drawRect(p.colors[0].copy(alpha = 0.25f + random() * 0.4f), pos, Size(2f, 2f))
    }
    val paint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.MONOSPACE
        textSize = 7.sp.toPx()
        color = p.colors[0].toArgb()
    }
    val ramp = " .:-=+*#%@"
    val charW = paint.measureText("@")
    val charH = textSizeLine(paint)
    val radius = size.height * 0.38f
    val cx = size.width * 0.72f
    val cy = size.height * 0.47f
    var y = cy - radius
    while (y <= cy + radius) {
        val dy = ((y - cy) / radius).toDouble()
        val line = StringBuilder()
        var x = cx - radius
        while (x <= cx + radius) {
            val dx = ((x - cx) / radius).toDouble()
            val d2 = dx * dx + dy * dy
            if (d2 <= 1) {
                val dz = sqrt(1 - d2)
                val l = max(0.0, dx * -0.5 + dy * -0.6 + dz * 0.62)
                line.append(ramp[min(ramp.length - 1, (l * (ramp.length - 1)).roundToInt())])
            } else line.append(' ')
            x += charW
        }
        drawContext.canvas.nativeCanvas.drawText(line.toString(), cx - radius, y + charH / 3, paint)
        y += charH
    }
}

private fun textSizeLine(paint: Paint): Float = paint.textSize * 1.0f
