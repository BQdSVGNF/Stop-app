package com.tempo.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tempo.app.data.Task
import com.tempo.app.data.TaskStore
import java.time.LocalDate

@Composable
fun TasksScreen(onEdit: (Task) -> Unit, onPause: (Task) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var showCompleted by rememberSaveable { mutableStateOf(false) }

    val query = search.trim()
    val all = TaskStore.tasks.toList().sortedByDescending { it.createdAt }
    val filtered = if (query.isEmpty()) all else all.filter {
        it.title.contains(query, ignoreCase = true) || it.notes.contains(query, ignoreCase = true)
    }
    val later = filtered.filter { it.isLater && it.completedAt == null }
    val upcoming = filtered.filter { !it.isRepeating && !it.isLater && it.completedAt == null }
        .sortedBy { it.due ?: LocalDate.MAX }
    val routines = filtered.filter { it.isRepeating }.sortedBy { it.sortMinutes }
    val completed = filtered.filter { !it.isRepeating && it.completedAt != null }.sortedByDescending { it.completedAt }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 190.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tâches", fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                TextField(
                    value = search, onValueChange = { search = it },
                    placeholder = { Text("Rechercher") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (all.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 60.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.Checklist, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(44.dp))
                    Text("Aucune tâche", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text("Ajoute ta première tâche avec le bouton +.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (later.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lightbulb, null, tint = Color(0xFFE0A800), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pour plus tard", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                }
                Glass(Modifier.fillMaxWidth()) {
                    Column {
                        later.forEachIndexed { i, task ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { TaskRow(task, onClick = { onEdit(task) }, onPause = null) }
                                Row(
                                    Modifier.padding(end = 12.dp).clickable {
                                        TaskStore.update(task.id) { it.copy(isLater = false, dueDate = LocalDate.now().toString()) }
                                    }.padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Rounded.WbSunny, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("Aujourd'hui", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (i < later.lastIndex) HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }

        if (upcoming.isNotEmpty()) item { TaskGroup("À faire", upcoming, onEdit, onPause) }
        if (routines.isNotEmpty()) item { TaskGroup("Routines", routines, onEdit, null) }

        if (completed.isNotEmpty()) item {
            val rotation by animateFloatAsState(if (showCompleted) 90f else 0f, label = "chevron")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth().clickable { showCompleted = !showCompleted }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Terminées (${completed.size})", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.rotate(rotation))
                }
                if (showCompleted) {
                    Glass(Modifier.fillMaxWidth()) {
                        Column {
                            completed.forEachIndexed { i, task ->
                                TaskRow(task, onClick = { onEdit(task) }, onPause = null)
                                if (i < completed.lastIndex) HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }
    }
}
