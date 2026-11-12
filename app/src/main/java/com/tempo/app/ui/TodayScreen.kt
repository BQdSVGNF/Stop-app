package com.tempo.app.ui

import android.content.Intent
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PauseCircleOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item { Header() }
        item { EncouragementCard() }
        item { ProgressCard(done.size, today.size, Stats.streak(all)) }
        if (today.isEmpty() && paused.isEmpty()) item { EmptyState() }
        if (pending.isNotEmpty()) item { TaskGroup("À faire", pending, onEdit, onPause) }
        if (paused.isNotEmpty()) item { PausedGroup(paused, onEdit) }
        if (done.isNotEmpty()) item { TaskGroup("Terminées", done, onEdit, null) }
    }
}

@Composable
private fun Header() {
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)).uppercase(Locale.FRENCH)
    val hour = LocalTime.now().hour
    val base = when (hour) {
        in 5..11 -> "Bonjour"
        in 12..17 -> "Bon après-midi"
        else -> "Bonsoir"
    }
    val name = AppSettings.userName.trim()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold)
        Text(if (name.isEmpty()) base else "$base, $name", fontSize = 32.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EncouragementCard(styleOverride: ThemeStyle? = null) {
    val style = styleOverride ?: currentStyle()
    val palette = currentPalette()
    val ink = if (style.isLight) palette.dark else Color.White
    val context = LocalContext.current
    val quote = Quotes.today

    Box(
        Modifier.fillMaxWidth()
            .shadow(18.dp, RoundedCornerShape(24.dp), ambientColor = palette.colors[0], spotColor = palette.colors[0])
            .clip(RoundedCornerShape(24.dp))
            .heightIn(min = 160.dp),
    ) {
        ThemeBackground(style, palette, Modifier.matchParentSize())
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = ink.copy(alpha = 0.9f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Ton élan du jour", color = ink.copy(alpha = 0.9f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.Share, "Partager", tint = ink.copy(alpha = 0.9f),
                    modifier = Modifier.size(20.dp).clickable {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, quote)
                        context.startActivity(Intent.createChooser(send, null))
                    })
            }
            Text(
                quote,
                color = ink,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = style.fontFamily,
                style = TextStyle(shadow = if (style.isLight) null else Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 2f), 12f)),
            )
        }
    }
}

@Composable
private fun ProgressCard(done: Int, total: Int, streak: Int) {
    val palette = currentPalette()
    val progress = if (total == 0) 0f else done.toFloat() / total
    val animated = animateFloatAsState(progress, spring(dampingRatio = 0.8f), label = "progress").value
    val message = when {
        total == 0 -> "Journée libre"
        done == total -> "Journée parfaite ! 🎉"
        progress >= 0.5f -> "Tu y es presque"
        done > 0 -> "Bien lancé"
        else -> "C'est parti"
    }
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                Canvas(Modifier.matchParentSize()) {
                    val stroke = 10.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    drawArc(
                        Brush.sweepGradient(listOf(palette.colors[0], palette.colors[1], palette.colors[2], palette.colors[0])),
                        -90f, 360f * animated, false, Offset(inset, inset), arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                Text("${(progress * 100).toInt()}%", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(message, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text("$done sur $total terminée${if (done > 1) "s" else ""}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                if (streak > 0) {
                    Row(
                        Modifier.background(Orange.copy(alpha = 0.12f), CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.LocalFireDepartment, null, tint = Orange, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("$streak jour${if (streak > 1) "s" else ""} d'affilée", color = Orange, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Rounded.WbTwilight, null, tint = Orange, modifier = Modifier.size(44.dp))
        Text("Rien de prévu aujourd'hui", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Text("Appuie sur + pour ajouter ta première tâche.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

// MARK: Groupes et lignes

@Composable
fun TaskGroup(title: String, tasks: List<Task>, onEdit: (Task) -> Unit, onPause: ((Task) -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.animateContentSize()) {
                tasks.forEachIndexed { i, task ->
                    TaskRow(task, onClick = { onEdit(task) }, onPause = if (onPause != null) ({ onPause(task) }) else null)
                    if (i < tasks.lastIndex) HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
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
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape)
                .background(if (done) color else Color.Transparent)
                .border(2.dp, if (done) Color.Transparent else color.copy(alpha = 0.6f), CircleShape)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    TaskStore.update(task.id) { it.toggled() }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(Icons.Rounded.Check, "Fait", tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Box(Modifier.size(40.dp).background(color.copy(alpha = 0.14f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Text(task.emoji, fontSize = 20.sp)
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
    if (parts.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        parts.forEach { (icon, text, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
                Text(text, color = color, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PausedGroup(tasks: List<Task>, onEdit: (Task) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("En pause", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("${tasks.size}", color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Orange.copy(alpha = 0.14f), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp))
        }
        Surface(
            shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.border(1.dp, Orange.copy(alpha = 0.25f), RoundedCornerShape(22.dp)),
        ) {
            Column {
                tasks.forEachIndexed { i, task ->
                    PausedRow(task, onClick = { onEdit(task) })
                    if (i < tasks.lastIndex) HorizontalDivider(Modifier.padding(start = 66.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
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
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).background(color.copy(alpha = 0.14f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Text(task.emoji, fontSize = 20.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(task.title, fontWeight = FontWeight.Medium, fontSize = 16.sp, maxLines = 2)
            if (task.pauseNote.isNotBlank()) {
                Row(
                    Modifier.background(Orange.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Bookmark, null, tint = Orange, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(task.pauseNote, color = Color(0xFFC76A00), fontSize = 12.sp)
                }
            }
            Text(task.resumeText() ?: task.pausedSinceText(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Row(
            Modifier.align(Alignment.CenterVertically).clip(CircleShape).background(color)
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
