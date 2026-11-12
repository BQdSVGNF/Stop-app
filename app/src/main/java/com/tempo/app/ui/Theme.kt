package com.tempo.app.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tempo.app.data.AppSettings
import kotlin.math.PI
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
    LIQUID_GLASS("Verre liquide"),
    GRADIENT("Dégradé"),
    FLUTED("Verre cannelé"),
    HALFTONE("Trame", isLight = true),
    BLUEPRINT("Blueprint", mono = true),
    CONTOUR("Topographie"),
    ASCII("ASCII", mono = true);

    /** Les styles « techniques » passent toute l'app en police monospace. */
    val fontFamily: FontFamily get() = if (mono) FontFamily.Monospace else FontFamily.Default

    /** Styles dont le décor bouge doucement. */
    val animated: Boolean get() = this == LIQUID_GLASS || this == GRADIENT

    companion object {
        fun from(name: String) = ThemeStyle.entries.firstOrNull { it.name == name } ?: LIQUID_GLASS
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
    MONO("Monochrome", listOf(Color(0xFF3A3A3C), Color(0xFFC7C7CC), Color(0xFF8E8E93)), Color(0xFF121212), Color(0xFF8E8E93));

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

/** Vrai quand l'app est affichée en sombre. */
val LocalDarkTheme = compositionLocalOf { false }

/** Inclinaison du téléphone (x, y entre -1 et 1), pour les effets 3D. */
val LocalTilt = staticCompositionLocalOf<State<Offset>> { mutableStateOf(Offset.Zero) }

@Composable
fun TempoTheme(content: @Composable () -> Unit) {
    val palette = currentPalette()
    val style = currentStyle()
    val dark = when (AppSettings.appearance) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    // Surfaces translucides : le thème se voit à travers les cartes (effet verre)
    val scheme = if (dark) darkColorScheme(
        primary = palette.accent, secondary = palette.colors[1], tertiary = palette.colors[2],
        background = Color(0xFF000000), surface = Color(0xFF1C1C1E).copy(alpha = 0.66f), surfaceVariant = Color(0xFF2C2C2E).copy(alpha = 0.85f),
        onBackground = Color.White, onSurface = Color.White, onSurfaceVariant = Color(0xFFA1A1A8),
        outline = Color(0xFF3A3A3C), onPrimary = Color.White,
    ) else lightColorScheme(
        primary = palette.accent, secondary = palette.colors[1], tertiary = palette.colors[2],
        background = Color(0xFFF2F2F7), surface = Color.White.copy(alpha = 0.74f), surfaceVariant = Color(0xFFE9E9EE).copy(alpha = 0.9f),
        onBackground = Color.Black, onSurface = Color.Black, onSurfaceVariant = Color(0xFF6E6E73),
        outline = Color(0xFFD1D1D6), onPrimary = Color.White,
    )
    val typography = remember(style) { Typography().withFamily(style.fontFamily) }

    // Icônes de la barre d'état lisibles quel que soit le mode choisi dans l'app
    val context = LocalContext.current
    LaunchedEffect(dark) {
        val activity = context as? ComponentActivity
        if (activity != null) {
            val transparent = android.graphics.Color.TRANSPARENT
            val bars = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
            activity.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        }
    }

    val tilt = rememberDeviceTilt(AppSettings.motion3d)
    CompositionLocalProvider(LocalDarkTheme provides dark, LocalTilt provides tilt) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

private fun Typography.withFamily(f: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f), headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f), headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f), titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f), bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f), bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f), labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f),
)

// MARK: 3D : inclinaison du téléphone

/** Lit l'accéléromètre uniquement quand l'app est au premier plan (économie de batterie). */
@Composable
private fun rememberDeviceTilt(enabled: Boolean): State<Offset> {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val tilt = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(context, lifecycle, enabled) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = (-event.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
                // 0 quand le téléphone est tenu normalement (incliné d'environ 35°)
                val y = (event.values[1] / SensorManager.GRAVITY_EARTH - 0.57f).coerceIn(-1f, 1f)
                val old = tilt.value
                tilt.value = Offset(old.x + (x - old.x) * 0.12f, old.y + (y - old.y) * 0.12f)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        var listening = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (enabled && !listening && manager != null && sensor != null) {
                    listening = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
                }
                Lifecycle.Event.ON_PAUSE -> if (listening) {
                    manager?.unregisterListener(listener)
                    listening = false
                }
                else -> {}
            }
        }
        if (!enabled) tilt.value = Offset.Zero
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (listening) manager?.unregisterListener(listener)
        }
    }
    return tilt
}

/** Horloge 0 → 1 en boucle pour les décors animés (rien ne tourne pour les décors fixes). */
@Composable
fun rememberThemePhase(style: ThemeStyle): State<Float> {
    val animated = AppSettings.motion3d && style.animated
    return if (animated) {
        val transition = rememberInfiniteTransition(label = "phase")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween<Float>(durationMillis = 26_000, easing = LinearEasing)),
            label = "phase",
        )
    } else {
        remember { mutableStateOf(0f) }
    }
}

// MARK: Décor plein écran

/**
 * Le thème choisi, sur TOUTE la page, avec un effet de profondeur :
 * le décor glisse et pivote légèrement quand on incline le téléphone.
 */
@Composable
fun AppBackdrop(modifier: Modifier = Modifier) {
    val style = currentStyle()
    val palette = currentPalette()
    val dark = LocalDarkTheme.current
    val tilt = LocalTilt.current
    val phase = rememberThemePhase(style)
    val wash = when {
        dark && style.isLight -> Color.Black.copy(alpha = 0.70f)
        dark -> Color.Black.copy(alpha = 0.28f)
        style.isLight -> Color.White.copy(alpha = 0.25f)
        else -> Color(0xFFF2F2F7).copy(alpha = 0.78f)
    }
    Box(modifier.fillMaxSize().background(if (dark) Color.Black else Color(0xFFF2F2F7))) {
        ThemeBackground(
            style, palette,
            Modifier.fillMaxSize().graphicsLayer {
                val t = tilt.value
                scaleX = 1.2f
                scaleY = 1.2f
                translationX = t.x * 22.dp.toPx()
                translationY = t.y * 18.dp.toPx()
                rotationY = t.x * 3f
                rotationX = -t.y * 3f
                cameraDistance = 18f * density
            },
            phase = { phase.value },
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(wash.copy(alpha = wash.alpha * 0.7f), wash, wash.copy(alpha = min(1f, wash.alpha * 1.2f))),
                ),
            ),
        )
    }
}

// MARK: Verre

/** Carte translucide « verre » avec un liseré lumineux. */
@Composable
fun Glass(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    accent: Color? = null,
    content: @Composable () -> Unit,
) {
    val dark = LocalDarkTheme.current
    val border = if (accent != null) BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    else BorderStroke(
        1.dp,
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (dark) 0.22f else 0.95f),
                Color.White.copy(alpha = if (dark) 0.04f else 0.35f),
            ),
        ),
    )
    Surface(modifier = modifier, shape = shape, color = MaterialTheme.colorScheme.surface, border = border, content = content)
}

/** Appui « ressort » façon iOS : l'élément s'enfonce puis rebondit. */
fun Modifier.appleSpringPress(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "press",
    )
    this
        .scale(scale)
        .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
}

// MARK: Fonds décoratifs

@Composable
fun ThemeBackground(style: ThemeStyle, palette: ThemePalette, modifier: Modifier = Modifier, phase: () -> Float = { 0f }) {
    Canvas(modifier) {
        when (style) {
            ThemeStyle.LIQUID_GLASS -> drawLiquid(palette, phase())
            ThemeStyle.GRADIENT -> drawGradient(palette, phase())
            ThemeStyle.FLUTED -> drawFluted(palette)
            ThemeStyle.HALFTONE -> drawHalftone(palette)
            ThemeStyle.BLUEPRINT -> drawBlueprint(palette)
            ThemeStyle.CONTOUR -> drawContour(palette)
            ThemeStyle.ASCII -> drawAscii(palette)
        }
    }
}

private fun DrawScope.drawLiquid(p: ThemePalette, t: Float) {
    drawRect(p.dark)
    val c = p.colors
    val w = size.width
    val h = size.height
    val m = max(w, h)
    val a = t * 2f * PI.toFloat()
    val blobs = listOf(
        Triple(Offset(0.18f + 0.10f * sin(a), 0.20f + 0.07f * cos(a)), c[0], 0.55f),
        Triple(Offset(0.85f + 0.07f * cos(a + 1.3f), 0.38f + 0.10f * sin(a + 0.7f)), c[1], 0.48f),
        Triple(Offset(0.42f + 0.12f * sin(a + 2.1f), 0.80f + 0.06f * cos(a + 2.4f)), c[2], 0.58f),
        Triple(Offset(0.95f + 0.04f * sin(a + 3.0f), 0.98f), c[0], 0.38f),
    )
    blobs.forEach { (pos, color, r) ->
        val center = Offset(pos.x * w, pos.y * h)
        val radius = r * m
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0f)), center, radius), radius, center)
    }
    // Reflet de verre en diagonale
    drawRect(
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.14f), Color.Transparent, Color.White.copy(alpha = 0.06f)),
            Offset.Zero, Offset(w, h),
        ),
    )
}

private fun DrawScope.drawGradient(p: ThemePalette, t: Float) {
    val a = t * 2f * PI.toFloat()
    drawRect(Brush.linearGradient(listOf(p.colors[0], p.colors[1]), Offset.Zero, Offset(size.width, size.height)))
    drawCircle(
        Color.White.copy(alpha = 0.12f), radius = size.height * 0.6f,
        center = Offset(size.width * (0.92f + 0.04f * sin(a)), size.height * (0.05f + 0.03f * cos(a))),
    )
    val c = Offset(size.width * (0.05f + 0.05f * cos(a)), size.height * (1.0f - 0.04f * sin(a)))
    drawCircle(
        Brush.radialGradient(listOf(p.colors[2].copy(alpha = 0.45f), Color.Transparent), c, size.height * 0.6f),
        radius = size.height * 0.6f, center = c,
    )
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

    // Plan technique : s'adapte à une carte (paysage) comme à l'écran entier (portrait)
    val line = Color.White.copy(alpha = 0.85f)
    val portrait = size.height > size.width
    val w = if (portrait) size.width * 0.56f else min(size.height * 0.6f, size.width * 0.34f)
    val h = if (portrait) w * 0.8f else size.height * 0.5f
    val left = if (portrait) size.width * 0.36f else size.width * 0.62f
    val top = if (portrait) size.height * 0.12f else size.height * 0.2f
    drawRect(line, Offset(left, top), Size(w, h), style = Stroke(1.5f))
    val c = Offset(left + w / 2, top + h / 2)
    val r = min(w, h) * 0.36f
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

private fun DrawScope.drawAscii(p: ThemePalette) {
    drawRect(Color(0xFF0A0A0A))
    var seed = 42L
    fun random(): Float {
        seed = (seed * 6364136223846793005L + 1442695040888963407L)
        return ((seed ushr 33) % 10_000).toFloat() / 10_000f
    }
    val stars = if (size.height > size.width) 260 else 90
    repeat(stars) {
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
    val charH = paint.textSize
    // Sphère en caractères : à droite sur une carte, en haut au centre sur l'écran entier
    val portrait = size.height > size.width
    val radius = min(size.height * 0.38f, size.width * 0.42f)
    val cx = size.width * (if (portrait) 0.58f else 0.72f)
    val cy = size.height * (if (portrait) 0.30f else 0.47f)
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

