@file:OptIn(ExperimentalMaterial3Api::class)

package com.tempo.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tempo.app.data.Task
import com.tempo.app.data.TaskStore
import com.tempo.app.data.Weekdays
import com.tempo.app.data.toMillis
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val Emojis = listOf("✨", "📚", "💼", "🏃", "💧", "🧹", "🛒", "💊", "🍎", "📞", "✉️", "💰", "🧘", "🎯", "🛏️", "🍽️", "🐶", "🌱", "🎨", "❤️")

private enum class RepeatMode(val label: String) { ONCE("Une fois"), DAILY("Chaque jour"), WEEKDAYS("Semaine"), CUSTOM("Choisir") }

@Composable
private fun NotificationPermissionRequester(): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return {
        if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(Locale.FRENCH), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 14.dp, bottom = 6.dp, top = 18.dp))
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun SheetHeader(title: String, cancel: () -> Unit, confirm: String, enabled: Boolean = true, onConfirm: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = cancel) { Text("Annuler") }
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        TextButton(onClick = onConfirm, enabled = enabled) { Text(confirm, fontWeight = FontWeight.Bold) }
    }
}

// MARK: - Éditeur de tâche

@Composable
fun EditorSheet(task: Task?, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val askPermission = NotificationPermissionRequester()

    var title by remember { mutableStateOf(task?.title ?: "") }
    var notes by remember { mutableStateOf(task?.notes ?: "") }
    var emoji by remember { mutableStateOf(task?.emoji ?: "✨") }
    var colorName by remember { mutableStateOf(task?.color ?: "indigo") }
    val initialDays = task?.repeatDays?.toSet() ?: emptySet()
    var mode by remember {
        mutableStateOf(
            when {
                initialDays.isEmpty() -> RepeatMode.ONCE
                initialDays.size == 7 -> RepeatMode.DAILY
                initialDays == setOf(1, 2, 3, 4, 5) -> RepeatMode.WEEKDAYS
                else -> RepeatMode.CUSTOM
            },
        )
    }
    var customDays by remember { mutableStateOf(if (initialDays.isEmpty()) setOf(1, 3, 5) else initialDays) }
    var hasDate by remember { mutableStateOf(task?.dueDate != null) }
    var date by remember { mutableStateOf(task?.due ?: LocalDate.now()) }
    var reminder by remember { mutableStateOf(task?.reminderEnabled ?: false) }
    var hour by remember { mutableIntStateOf(task?.reminderHour ?: 9) }
    var minute by remember { mutableIntStateOf(task?.reminderMinute ?: 0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val color = taskColor(colorName)
    val canSave = title.isNotBlank() && (mode != RepeatMode.CUSTOM || customDays.isNotEmpty())

    fun save() {
        val days = when (mode) {
            RepeatMode.ONCE -> emptyList()
            RepeatMode.DAILY -> listOf(1, 2, 3, 4, 5, 6, 7)
            RepeatMode.WEEKDAYS -> listOf(1, 2, 3, 4, 5)
            RepeatMode.CUSTOM -> customDays.sorted()
        }
        val base = task ?: Task()
        var updated = base.copy(
            title = title.trim(), notes = notes.trim(), emoji = emoji, color = colorName,
            repeatDays = days,
            dueDate = if (mode == RepeatMode.ONCE && hasDate) date.toString() else null,
            reminderEnabled = reminder, reminderHour = hour, reminderMinute = minute,
        )
        if (hasDate || reminder || mode != RepeatMode.ONCE) updated = updated.copy(isLater = false)
        if (reminder) askPermission()
        TaskStore.upsert(updated)
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        SheetHeader(if (task == null) "Nouvelle tâche" else "Modifier", onDismiss, if (task == null) "Ajouter" else "Enregistrer", canSave) { save() }
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Card {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(84.dp).background(color.copy(alpha = 0.16f), RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                        Text(emoji, fontSize = 44.sp)
                    }
                }
                PlainField(title, { title = it }, "Que veux-tu faire ?", big = true)
                PlainField(notes, { notes = it }, "Notes (facultatif)", singleLine = false)
            }

            SectionLabel("Icône et couleur")
            Card {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Emojis.forEach { e ->
                        Box(
                            Modifier.size(44.dp)
                                .background(if (e == emoji) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .border(2.dp, if (e == emoji) color else Color.Transparent, RoundedCornerShape(12.dp))
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 22.sp) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TaskColors.forEach { (name, c) ->
                        Box(
                            Modifier.size(26.dp).background(c, CircleShape).clickable { colorName = name },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (name == colorName) Box(Modifier.size(18.dp).border(2.5.dp, Color.White, CircleShape))
                        }
                    }
                }
            }

            SectionLabel("Quand")
            Card {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    RepeatMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = mode == m, onClick = { mode = m },
                            shape = SegmentedButtonDefaults.itemShape(i, RepeatMode.entries.size),
                            icon = {},
                        ) { Text(m.label, fontSize = 12.sp, maxLines = 1) }
                    }
                }
                when (mode) {
                    RepeatMode.ONCE -> {
                        SwitchRow(Icons.Rounded.CalendarToday, "Date", hasDate) { hasDate = it }
                        if (hasDate) {
                            ValueRow("Jour", date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH))) { showDatePicker = true }
                        }
                    }
                    RepeatMode.CUSTOM -> WeekdayPicker(customDays, color) { customDays = it }
                    else -> {}
                }
                Text(
                    when (mode) {
                        RepeatMode.ONCE -> if (hasDate) "Une seule fois, le jour choisi." else "Une seule fois, sans date précise."
                        RepeatMode.DAILY -> "Tous les jours de la semaine."
                        RepeatMode.WEEKDAYS -> "Du lundi au vendredi."
                        RepeatMode.CUSTOM -> "Choisis les jours où cette routine revient."
                    },
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionLabel("Rappel")
            Card {
                SwitchRow(Icons.Rounded.NotificationsActive, "Me notifier", reminder) { reminder = it }
                if (reminder) ValueRow("Heure", "%02d:%02d".format(hour, minute)) { showTimePicker = true }
            }

            if (task != null) {
                Spacer(Modifier.height(24.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().clickable { confirmDelete = true },
                ) {
                    Text("Supprimer la tâche", color = Color(0xFFFF3B30), textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Annuler") } },
        ) { DatePicker(state) }
    }
    if (showTimePicker) {
        TimeDialog(hour, minute, onDismiss = { showTimePicker = false }) { h, m -> hour = h; minute = m; showTimePicker = false }
    }
    if (confirmDelete && task != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer cette tâche ?") },
            confirmButton = {
                TextButton(onClick = { TaskStore.delete(task.id); confirmDelete = false; onDismiss() }) {
                    Text("Supprimer", color = Color(0xFFFF3B30))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun PlainField(value: String, onChange: (String) -> Unit, placeholder: String, big: Boolean = false, singleLine: Boolean = true) {
    TextField(
        value = value, onValueChange = onChange,
        placeholder = { Text(placeholder, fontSize = if (big) 19.sp else 15.sp) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontSize = if (big) 19.sp else 15.sp,
            fontWeight = if (big) FontWeight.SemiBold else FontWeight.Normal,
        ),
        singleLine = singleLine, maxLines = if (singleLine) 1 else 4,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SwitchRow(icon: ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@Composable
private fun WeekdayPicker(selection: Set<Int>, tint: Color, onChange: (Set<Int>) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Weekdays.ordered.forEach { day ->
            val on = day in selection
            Box(
                Modifier.weight(1f).aspectRatio(1f).background(if (on) tint else MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .clickable { onChange(if (on) selection - day else selection + day) },
                contentAlignment = Alignment.Center,
            ) {
                Text(Weekdays.letters.getValue(day), fontWeight = FontWeight.Bold, color = if (on) Color.White else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun TimeDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        text = { TimePicker(state) },
    )
}

// MARK: - Mettre en pause

private enum class ResumeChoice(val label: String, val icon: ImageVector) {
    ONE_HOUR("Dans 1 heure", Icons.Rounded.Schedule),
    TONIGHT("Ce soir", Icons.Rounded.NightsStay),
    TOMORROW("Demain matin", Icons.Rounded.WbTwilight),
    WEEKEND("Ce week-end", Icons.Rounded.Weekend),
    CUSTOM("Choisir le moment", Icons.Rounded.Event),
    NONE("Sans rappel", Icons.Rounded.NotificationsOff);

    fun time(custom: LocalDateTime): LocalDateTime? {
        val now = LocalDateTime.now()
        return when (this) {
            ONE_HOUR -> now.plusHours(1)
            TONIGHT -> now.toLocalDate().atTime(19, 0).let { if (it.isAfter(now)) it else now.plusHours(2) }
            TOMORROW -> now.toLocalDate().plusDays(1).atTime(9, 0)
            WEEKEND -> {
                val saturday = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)).atTime(10, 0)
                if (saturday.isAfter(now)) saturday else saturday.plusWeeks(1)
            }
            CUSTOM -> custom
            NONE -> null
        }
    }
}

private fun previewText(t: LocalDateTime): String {
    val today = LocalDate.now()
    val time = t.format(DateTimeFormatter.ofPattern("HH:mm"))
    return when (t.toLocalDate()) {
        today -> "Aujourd'hui, $time"
        today.plusDays(1) -> "Demain, $time"
        else -> t.format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.FRENCH)) + ", $time"
    }
}

@Composable
fun PauseSheet(task: Task, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val askPermission = NotificationPermissionRequester()
    var note by remember { mutableStateOf("") }
    var choice by remember { mutableStateOf(ResumeChoice.TOMORROW) }
    var custom by remember { mutableStateOf(LocalDateTime.now().plusHours(1).withSecond(0).withNano(0)) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val color = taskColor(task.color)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        SheetHeader("Mettre en pause", onDismiss, "Pause") {
            val at = choice.time(custom)
            if (at != null) askPermission()
            TaskStore.update(task.id) { it.paused(note.trim(), at?.let { t -> toMillis(t) }) }
            onDismiss()
        }
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Card {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        Box(Modifier.size(76.dp).background(color.copy(alpha = 0.16f), RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                            Text(task.emoji, fontSize = 40.sp)
                        }
                        Box(
                            Modifier.align(Alignment.BottomEnd).padding(0.dp).size(26.dp).background(Orange, CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Pause, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                    }
                    Text(task.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, textAlign = TextAlign.Center)
                    Text("Mets-la de côté sans l'oublier. Tempo te rappellera où tu en étais.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }

            SectionLabel("Où tu t'es arrêté ?")
            Card {
                PlainField(note, { note = it }, "Ex. : chapitre 3, exercice 5", singleLine = false)
                Text("Ça t'évitera de chercher quand tu reprendras.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionLabel("Quand veux-tu reprendre ?")
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column {
                    ResumeChoice.entries.forEach { item ->
                        val selected = item == choice
                        Row(
                            Modifier.fillMaxWidth().clickable { choice = item }.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(item.icon, null, tint = if (selected) Orange else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.label)
                                if (item != ResumeChoice.CUSTOM) item.time(custom)?.let {
                                    Text(previewText(it), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (selected) Icon(Icons.Rounded.Check, null, tint = Orange)
                        }
                    }
                    if (choice == ResumeChoice.CUSTOM) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(custom.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)),
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .clickable { pickDate = true }.padding(horizontal = 12.dp, vertical = 8.dp))
                            Text(custom.format(DateTimeFormatter.ofPattern("HH:mm")),
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .clickable { pickTime = true }.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = custom.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val d = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        custom = LocalDateTime.of(d, custom.toLocalTime())
                    }
                    pickDate = false
                }) { Text("OK") }
            },
        ) { DatePicker(state) }
    }
    if (pickTime) {
        TimeDialog(custom.hour, custom.minute, onDismiss = { pickTime = false }) { h, m ->
            custom = LocalDateTime.of(custom.toLocalDate(), LocalTime.of(h, m)); pickTime = false
        }
    }
}

// MARK: - Noter pour plus tard

@Composable
fun CaptureSheet(onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var count by remember { mutableIntStateOf(0) }
    fun save() {
        val t = text.trim()
        if (t.isEmpty()) return
        TaskStore.upsert(Task(title = t, emoji = "💡", color = "yellow", isLater = true))
        text = ""
        count++
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lightbulb, null, tint = Color(0xFFFFC400))
                Spacer(Modifier.width(8.dp))
                Text("Noter pour plus tard", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("OK", fontWeight = FontWeight.Bold) }
            }
            Text("Note l'idée et continue ce que tu faisais. Tu t'en occuperas après.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(
                    value = text, onValueChange = { text = it },
                    placeholder = { Text("Une idée, une chose à faire…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                )
                val enabled = text.isNotBlank()
                Box(
                    Modifier.size(48.dp)
                        .background(if (enabled) Brush.linearGradient(listOf(Color(0xFFFFC400), Color(0xFFFF9500)))
                        else Brush.linearGradient(listOf(Color.Gray.copy(alpha = 0.4f), Color.Gray.copy(alpha = 0.4f))), CircleShape)
                        .clickable(enabled = enabled) { save() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.ArrowUpward, "Noter", tint = Color.White) }
            }
            if (count > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF34C759), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("$count idée${if (count > 1) "s" else ""} notée${if (count > 1) "s" else ""} · retrouve-les dans Tâches",
                        color = Color(0xFF34C759), fontSize = 12.sp)
                }
            }
        }
    }
}
