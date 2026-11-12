package com.tempo.app.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tempo.app.data.AppSettings
import com.tempo.app.data.Quotes
import com.tempo.app.data.Stats
import com.tempo.app.data.Task
import com.tempo.app.data.TaskStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// ============================================================================
// 1. SYSTÈME DE THÈME & DESIGN APPLE MODERNISÉ
// ============================================================================

enum class ThemeStyle(val label: String, val isLight: Boolean = false, val mono: Boolean = false) {
    LIQUID_GLASS("Verre Liquide"),
    GRADIENT("Dégradé Profond"),
    HALFTONE("Trame", isLight = true),
    BLUEPRINT("Blueprint futuriste", mono = true),
    CONTOUR("Topographie", mono = false),
    ASCII("ASCII Tech", mono = true);

    val fontFamily: FontFamily get() = if (mono) FontFamily.Monospace else FontFamily.Default

    companion object {
        fun from(name: String) = entries.firstOrNull { it.name == name } ?: LIQUID_GLASS
    }
}

enum class ThemePalette(val label: String, val colors: List<Color>, val dark: Color, accentOverride: Color? = null) {
    INDIGO("Indigo Apple", listOf(Color(0xFF5E5CE6), Color(0xFFAF52DE), Color(0xFFFF2D55)), Color(0xFF0F0E17)),
    SUNSET("Titanium Solaire", listOf(Color(0xFFFF9500), Color(0xFFFF2D55), Color(0xFFBF5AF2)), Color(0xFF1C1216)),
    OCEAN("Océan Profond", listOf(Color(0xFF0A84FF), Color(0xFF5AC8FA), Color(0xFF30B0C7)), Color(0xFF0A101D)),
    FOREST("Minéral Vert", listOf(Color(0xFF30D158), Color(0xFF64D2FF), Color(0xFF5856D6)), Color(0xFF0E1A14)),
    ROSE("Rose Quartz", listOf(Color(0xFFFF375F), Color(0xFFFF6482), Color(0xFFAF52DE)), Color(0xFF1D0F16)),
    AURORA("Cyber Aurore", listOf(Color(0xFFBF5AF2), Color(0xFF32D74B), Color(0xFF64D2FF)), Color(0xFF0B0B16)),
    MONO("Onyx Monolithique", listOf(Color(0xFF8E8E93), Color(0xFFC7C7CC), Color(0xFF3A3A3C)), Color(0xFF080808), Color(0xFFEBEBF5));

    val accent: Color = accentOverride ?: colors[0]

    companion object {
        fun from(name: String) = entries.firstOrNull { it.name == name } ?: INDIGO
    }
}

val TaskColors = linkedMapOf(
    "indigo" to Color(0xFF5E5CE6), "blue" to Color(0xFF0A84FF), "teal" to Color(0xFF30B0C7),
    "mint" to Color(0xFF32D74B), "green" to Color(0xFF34C759), "yellow" to Color(0xFFFFD60A),
    "orange" to Color(0xFFFF9500), "red" to Color(0xFFFF453A), "pink" to Color(0xFFFF375F),
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
        background = Color(0xFF000000), surface = Color(0xFF1C1C1E).copy(alpha = 0.85f),
        surfaceVariant = Color(0xFF2C2C2E), onBackground = Color.White, onSurface = Color.White,
        onSurfaceVariant = Color(0xFF98989F), outline = Color(0xFF3A3A3C), onPrimary = Color.White,
    ) else lightColorScheme(
        primary = palette.accent, secondary = palette.colors[1], tertiary = palette.colors[2],
        background = Color(0xFFF2F2F7), surface = Color.White.copy(alpha = 0.92f),
        surfaceVariant = Color(0xFFE5E5EA), onBackground = Color.Black, onSurface = Color.Black,
        onSurfaceVariant = Color(0xFF8E8E93), outline = Color(0xFFD1D1D6), onPrimary = Color.White,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun ThemeBackground(style: ThemeStyle, palette: ThemePalette, modifier: Modifier = Modifier) {
    val c1 = palette.colors[0]
    val c2 = palette.colors[1]
    val c3 = palette.colors[2]
    val base = palette.dark

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        when (style) {
            ThemeStyle.LIQUID_GLASS -> {
                drawRect(Brush.linearGradient(listOf(base, c1.copy(alpha = 0.45f), c2.copy(alpha = 0.25f)), start = Offset(0f, 0f), end = Offset(w, h)))
                drawCircle(c1.copy(alpha = 0.35f), radius = w * 0.45f, center = Offset(w * 0.15f, h * 0.2f))
                drawCircle(c2.copy(alpha = 0.25f), radius = w * 0.55f, center = Offset(w * 0.85f, h * 0.85f))
            }
            ThemeStyle.GRADIENT -> {
                drawRect(Brush.linearGradient(listOf(c1, c2, c3), start = Offset(0f, 0f), end = Offset(w, h)))
            }
            ThemeStyle.HALFTONE -> {
                drawRect(Color(0xFFF8F8FA))
                val radius = 3.5f
                val spacing = 14f
                var y = spacing / 2
                while (y < h) {
                    var x = spacing / 2
                    while (x < w) {
                        drawCircle(c1.copy(alpha = 0.15f), radius, center = Offset(x, y))
                        x += spacing
                    }
                    y += spacing
                }
            }
            ThemeStyle.BLUEPRINT -> {
                drawRect(Color(0xFF0D1B2A))
                val grid = 28f
                var x = 0f
                while (x < w) {
                    drawLine(Color.White.copy(alpha = 0.1f), Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                    x += grid
                }
                var y = 0f
                while (y < h) {
                    drawLine(Color.White.copy(alpha = 0.1f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                    y += grid
                }
            }
            ThemeStyle.CONTOUR -> {
                drawRect(base)
                val path = Path()
                for (i in 1..6) {
                    path.reset()
                    val startY = h * (i / 7f)
                    path.moveTo(0f, startY)
                    var x = 0f
                    while (x <= w) {
                        val y = startY + sin(x * 0.025f + i) * 25f + cos(x * 0.012f) * 12f
                        path.lineTo(x, y)
                        x += 8f
                    }
                    drawPath(path, c1.copy(alpha = 0.25f), style = Stroke(width = 2.5f))
                }
            }
            ThemeStyle.ASCII -> {
                drawRect(Color(0xFF0A0A0A))
                val paint = Paint().apply {
                    color = c1.toArgb()
                    textSize = 22f
                    typeface = Typeface.MONOSPACE
                }
                val chars = arrayOf("•", "○", "+", "x", "*", "#")
                val step = 26f
                var y = step
                while (y < h) {
                    var x = step
                    while (x < w) {
                        val char = chars[((x + y) / step).toInt() % chars.size]
                        drawContext.canvas.nativeCanvas.drawText(char, x, y, paint)
                        x += step
                    }
                    y += step
                }
            }
        }
    }
}

// ============================================================================
// 2. MICRO-INTERACTIONS & FLUIDITÉ (EFFET RESSORT APPLE)
// ============================================================================

fun Modifier.appleSpringPress(onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "applePress"
    )
    this
        .scale(scale)
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}

// ============================================================================
// 3. COMPOSANTS DE L'ÉCRAN AUJOURD'HUI & CARTES
// ============================================================================

@Composable
fun TodayScreen(onEdit: (Task) -> Unit, onPause: (Task) -> Unit) {
    val tasks = TaskStore.tasks.toList().filter { it.belongsToToday() }.sortedBy { it.sortMinutes }
    val streak = Stats.streak(TaskStore.tasks)

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        val name = AppSettings.userName.trim()
                        val greeting = if (name.isNotEmpty()) "Bonjour, $name" else "Aujourd'hui"
                        Text(greeting, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                        Text(
                            LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)).replaceFirstChar { it.uppercase() },
                            fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (streak > 0) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Rounded.LocalFireDepartment, null, tint = Orange, modifier = Modifier.size(20.dp))
                                Text("$streak", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }
                EncouragementCard()
            }
        }

        item {
            Text("Tâches du jour", fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
        }

        if (tasks.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                ) {
                    Column(Modifier.padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("✨", fontSize = 40.sp)
                        Text("Tout est accompli pour l'instant", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Text("Profitez de votre journée ou planifiez de nouvelles ambitions.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, fontSize = 14.sp)
                    }
                }
            }
        } else {
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                ) {
                    Column {
                        tasks.forEachIndexed { i, task ->
                            TaskRow(task, onClick = { onEdit(task) }, onPause = { onPause(task) })
                            if (i < tasks.lastIndex) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EncouragementCard() {
    val palette = currentPalette()
    val style = currentStyle()
    val quote = Quotes.today

    Surface(
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(28.dp)),
        shadowElevation = 6.dp
    ) {
        Box(Modifier.fillMaxWidth().height(170.dp)) {
            ThemeBackground(style, palette, Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.30f)))
            Column(Modifier.matchParentSize().padding(22.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.WbSunny, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
                    Text("Élan quotidien", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(quote, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp, letterSpacing = (-0.2).sp)
            }
        }
    }
}

@Composable
fun TaskRow(task: Task, onClick: () -> Unit, onPause: (() -> Unit)?) {
    val done = task.isDone()
    val color = taskColor(task.color)
    val badgeBg by animateColorAsState(
        targetValue = if (done) Color(0xFF34C759).copy(alpha = 0.2f) else color.copy(alpha = 0.18f),
        label = "badgeColor"
    )

    Row(
        Modifier
            .fillMaxWidth()
            .appleSpringPress(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(badgeBg)
                .clickable { TaskStore.update(task.id) { it.toggled() } },
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(Icons.Rounded.Check, null, tint = Color(0xFF34C759), modifier = Modifier.size(22.dp))
            } else {
                Text(task.emoji, fontSize = 22.sp)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                style = if (done) TextStyle(textDecoration = TextDecoration.LineThrough) else TextStyle(),
            )
            val sub = when {
                task.isRepeating -> task.repeatSummary()
                task.reminderEnabled -> "Rappel à ${task.reminderText}"
                task.dueText() != null -> task.dueText()
                else -> null
            }
            sub?.let {
                Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (onPause != null && !done) {
            IconButton(onClick = onPause) {
                Icon(Icons.Rounded.Pause, "Mettre en pause", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun TaskGroup(title: String, tasks: List<Task>, onEdit: (Task) -> Unit, onPause: ((Task) -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
        ) {
            Column {
                tasks.forEachIndexed { i, task ->
                    TaskRow(task, onClick = { onEdit(task) }, onPause = onPause?.let { p -> { p(task) } })
                    if (i < tasks.lastIndex) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                }
            }
        }
    }
}
