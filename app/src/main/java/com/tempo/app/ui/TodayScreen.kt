package com.tempo.app.ui

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PauseCircleOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tempo.app.data.AppSettings
import com.tempo.app.data.Quotes
import com.tempo.app.data.Stats
import com.tempo.app.data.Task
import com.tempo.app.data.TaskStore
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayScreen(onEdit: (Task) -> Unit, onPause: (Task) -> Unit) {
    val all = TaskStore.tasks.toList()
    val today = all.filter { it.belongsToToday() }.sortedBy { it.sortMinutes }
    val pending = today.filter { !it.isDone() }
    val done = today.filter { it.isDone() }
    val paused = all.filter { it.isPaused }.sortedBy { it.pausedAt }
    val nowMinutes = LocalTime.now().let { it.hour * 60 + it.minute }
    val next = pending.firstOrNull { it.reminderEnabled && it.sortMinutes > nowMinutes }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 190.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { Header(Stats.streak(all)) }
        item { EncouragementCard() }
        item { ProgressCard(done.size, today.size, next) }
        if (today.isEmpty() && paused.isEmpty()) item { EmptyState() }
        if (pending.isNotEmpty()) item { TaskGroup("À faire", pending, onEdit, onPause) }
        if (paused.isNotEmpty()) item { PausedGroup(paused, onEdit) }
        if (done.isNotEmpty()) item { TaskGroup("Terminées", done, onEdit, null) }
    }
}

@Composable
private fun Header(streak: Int) {
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)).uppercase(Locale.FRENCH)
    val base = when (LocalTime.now().hour) {
        in 5..11 -> "Bonjour"
        in 12..17 -> "Bon après-midi"
        else -> "Bonsoir"
    }
    val name = AppSettings.userName.trim()
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
            Text(if (name.isEmpty()) base else "$base, $name", fontSize = 32.sp, fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp, lineHeight = 36.sp)
        }
        if (streak > 0) {
            Glass(shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.LocalFireDepartment, null, tint = Orange, modifier = Modifier.size(20.dp))
                    Text("$streak", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

/** La carte « élan du jour », en relief : elle pivote quand on incline le téléphone. */
@Composable
fun EncouragementCard(styleOverride: ThemeStyle? = null) {
    val style = styleOverride ?: currentStyle()
    val palette = currentPalette()
    val ink = if (style.isLight) palette.dark else Color.White
    val context = LocalContext.current
    val quote = Quotes.today
    val tilt = LocalTilt.current
    val phase = rememberThemePhase(style)
    val shape = RoundedCornerShape(28.dp)

    Box(
        Modifier.fillMaxWidth()
            .graphicsLayer {
                val t = tilt.value
                rotationY = t.x * 9f
                rotationX = -t.y * 7f
                cameraDistance = 14f * density
            }
            .shadow(22.dp, shape, ambientColor = palette.colors[0], spotColor = palette.colors[0])
            .clip(shape)
            .heightIn(min = 172.dp),
    ) {
        ThemeBackground(style, palette, Modifier.matchParentSize(), phase = { phase.value })
        // Reflet lumineux qui glisse avec l'inclinaison
        Canvas(Modifier.matchParentSize()) {
            val t = tilt.value
            val c = Offset(size.width * (0.3f + t.x * 0.5f), size.height * (0.05f + t.y * 0.6f))
            drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.26f), Color.Transparent), c, size.width * 0.75f))
        }
        Box(
            Modifier.matchParentSize().border(
                1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.05f))), shape,
            ),
        )
        // Le texte flotte légèrement au-dessus du décor (parallaxe)
        Column(
            Modifier
                .graphicsLayer {
                    val t = tilt.value
                    translationX = -t.x * 6.dp.toPx()
                    translationY = -t.y * 4.dp.toPx()
                }
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = ink.copy(alpha = 0.9f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Ton élan du jour", color = ink.copy(alpha = 0.9f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(ink.copy(alpha = 0.14f))
                        .clickable {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, quote)
                            context.startActivity(Intent.createChooser(send, null))
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Share, "Partager", tint = ink, modifier = Modifier.size(18.dp)) }
            }
            Text(
                quote,
                color = ink,
                fontSize = 21.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = style.fontFamily,
                style = TextStyle(shadow = if (style.isLight) null else Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 2f), 12f)),
            )
        }
    }
}

@Composable
private fun ProgressCard(done: Int, total: Int, next: Task?) {
    val palette = currentPalette()
    val progress = if (total == 0) 0f else done.toFloat() / total
    val animated by animateFloatAsState(progress, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow), label = "progress")
    val message = when {
        total == 0 -> "Journée libre"
        done == total -> "Journée parfaite ! 🎉"
        progress >= 0.5f -> "Tu y es presque"
        done > 0 -> "Bien lancé"
        else -> "C'est parti"
    }
    Glass(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                Canvas(Modifier.matchParentSize()) {
                    val stroke = 10.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val sweep = Brush.sweepGradient(listOf(palette.colors[0], palette.colors[1], palette.colors[2], palette.colors[0]))
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    // Halo lumineux sous l'anneau
                    drawArc(sweep, -90f, 360f * animated, false, Offset(inset, inset), arcSize, alpha = 0.25f,
                        style = Stroke(stroke * 2.2f, cap = StrokeCap.Round))
                    drawArc(sweep, -90f, 360f * animated, false, Offset(inset, inset), arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Text("${(progress * 100).toInt()}%", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(message, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text("$done sur $total terminée${if (done > 1) "s" else ""}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                if (next != null) {
                    Row(
                        Modifier.background(palette.accent.copy(alpha = 0.14f), CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Notifications, null, tint = palette.accent, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${next.reminderText} · ${next.title}", color = palette.accent, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.WbTwilight, null, tint = Orange, modifier = Modifier.size(46.dp))
            Text("Rien de prévu aujourd'hui", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text("Appuie sur + pour ajouter ta première tâche.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
    }
}

// MARK: Groupes et lignes

@Composable
fun TaskGroup(title: String, tasks: List<Task>, onEdit: (Task) -> Unit, onPause: ((Task) -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
            Text("${tasks.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Glass(Modifier.fillMaxWidth()) {
            Column(Modifier.animateContentSize()) {
                tasks.forEachIndexed { i, task ->
                    TaskRow(task, onClick = { onEdit(task) }, onPause = if (onPause != null) ({ onPause(task) }) else null)
                    if (i < tasks.lastIndex) HorizontalDivider(Modifier.padding(start = 70.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@Composable
fun TaskRow(task: Task, onClick: () -> Unit, onPause: (() -> Unit)?) {
    val done = task.isDone()
    val color = taskColor(task.color)
    val haptic = LocalHapticFeedback.current
    // La coche rebondit quand on la valide
    val checkScale by animateFloatAsState(
        if (done) 1f else 0.9f,
        spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "check",
    )
    val fill by animateColorAsState(if (done) color else Color.Transparent, label = "fill")
    Row(
        Modifier.fillMaxWidth().appleSpringPress(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(30.dp).scale(checkScale)
                .shadow(if (done) 6.dp else 0.dp, CircleShape, ambientColor = color, spotColor = color)
                .clip(CircleShape)
                .background(fill)
                .border(2.dp, if (done) Color.Transparent else color.copy(alpha = 0.7f), CircleShape)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    TaskStore.update(task.id) { it.toggled() }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(Icons.Rounded.Check, "Fait", tint = Color.White, modifier = Modifier.size(17.dp))
        }
        Box(
            Modifier.size(42.dp).background(
                Brush.linearGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.10f))), RoundedCornerShape(13.dp),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(task.emoji, fontSize = 21.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                task.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontSize = 16.sp,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
            DetailLine(task)
        }
        if (onPause != null && !done && !task.isRepeating) {
            IconButton(onClick = onPause) {
                Icon(Icons.Rounded.PauseCircleOutline, "Mettre en pause", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DetailLine(task: Task) {
    val parts = mutableListOf<Triple<ImageVector, String, Color>>()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (task.isPaused) parts += Triple(Icons.Rounded.Pause, "En pause", Orange)
    if (task.isLater && !task.isDone()) parts += Triple(Icons.Rounded.Lightbulb, "Pour plus tard", Color(0xFFE0A800))
    if (task.reminderEnabled) parts += Triple(Icons.Rounded.Notifications, task.reminderText, muted)
    if (task.isRepeating) parts += Triple(Icons.Rounded.Repeat, task.repeatSummary(), muted)
    else task.dueText()?.let { due ->
        parts += if (task.isOverdue) Triple(Icons.Rounded.ErrorOutline, "En retard · $due", Color(0xFFFF3B30))
        else Triple(Icons.Rounded.CalendarToday, due, muted)
    }
    // Après une pause : on garde « où j'en étais » sous les yeux
    if (!task.isPaused && !task.isDone() && task.pauseNote.isNotBlank()) parts += Triple(Icons.Rounded.Bookmark, task.pauseNote, Orange)
    if (parts.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        parts.forEach { (icon, text, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
                Text(text, color = color, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PausedGroup(tasks: List<Task>, onEdit: (Task) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("En pause", fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
            Text("${tasks.size}", color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Orange.copy(alpha = 0.14f), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp))
        }
        Glass(Modifier.fillMaxWidth(), accent = Orange) {
            Column {
                tasks.forEachIndexed { i, task ->
                    PausedRow(task, onClick = { onEdit(task) })
                    if (i < tasks.lastIndex) HorizontalDivider(Modifier.padding(start = 66.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@Composable
private fun PausedRow(task: Task, onClick: () -> Unit) {
    val color = taskColor(task.color)
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth().appleSpringPress(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(42.dp).background(color.copy(alpha = 0.16f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Text(task.emoji, fontSize = 21.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(task.title, fontWeight = FontWeight.Medium, fontSize = 16.sp, maxLines = 2)
            if (task.pauseNote.isNotBlank()) {
                Row(
                    Modifier.background(Orange.copy(alpha = 0.14f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Bookmark, null, tint = Orange, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(task.pauseNote, color = Orange, fontSize = 12.sp)
                }
            }
            Text(task.resumeText() ?: task.pausedSinceText(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Row(
            Modifier.align(Alignment.CenterVertically)
                .shadow(8.dp, CircleShape, ambientColor = color, spotColor = color)
                .clip(CircleShape).background(color)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    TaskStore.update(task.id) { it.resumed() }
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(3.dp))
            Text("Reprendre", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
