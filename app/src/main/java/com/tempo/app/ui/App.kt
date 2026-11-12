package com.tempo.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tempo.app.data.AppSettings
import com.tempo.app.data.Task
import com.tempo.app.data.TaskStore
import java.time.LocalDate

private enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Aujourd'hui", Icons.Rounded.WbSunny),
    TASKS("Tâches", Icons.Rounded.Checklist),
    SETTINGS("Réglages", Icons.Rounded.Settings),
}

/** Ce qu'on est en train de modifier dans l'éditeur. */
data class EditTarget(val task: Task?)

@Composable
fun TempoApp() {
    Crossfade(targetState = AppSettings.onboarded, label = "onboarding") { done ->
        if (done) MainScreen() else Onboarding()
    }
}

@Composable
private fun MainScreen() {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    var pausing by remember { mutableStateOf<Task?>(null) }
    var capturing by remember { mutableStateOf(false) }
    val palette = currentPalette()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab != Tab.SETTINGS) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallFloatingActionButton(
                        onClick = { capturing = true },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = Color(0xFFFFC400),
                        shape = CircleShape,
                    ) { Icon(Icons.Rounded.Lightbulb, contentDescription = "Noter pour plus tard") }
                    FloatingActionButton(
                        onClick = { editing = EditTarget(null) },
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.background(Brush.linearGradient(palette.colors.take(2)), CircleShape),
                    ) { Icon(Icons.Rounded.Add, contentDescription = "Ajouter une tâche") }
                }
            }
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            when (tab) {
                Tab.TODAY -> TodayScreen(onEdit = { editing = EditTarget(it) }, onPause = { pausing = it })
                Tab.TASKS -> TasksScreen(onEdit = { editing = EditTarget(it) }, onPause = { pausing = it })
                Tab.SETTINGS -> SettingsScreen()
            }
        }
    }

    editing?.let { target -> EditorSheet(target.task) { editing = null } }
    pausing?.let { task -> PauseSheet(task) { pausing = null } }
    if (capturing) CaptureSheet { capturing = false }
}

// MARK: Premier lancement

@Composable
private fun Onboarding() {
    var page by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(AppSettings.userName) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        finishOnboarding(name)
    }
    val pages = listOf(
        Triple(Icons.Rounded.AutoAwesome, "Bienvenue sur Tempo", "L'app qui t'aide enfin à t'organiser, simplement, un jour à la fois."),
        Triple(Icons.Rounded.NotificationsActive, "Des rappels à ton heure", "Choisis pour chaque tâche si tu veux une notification, à quelle heure et quels jours."),
        Triple(Icons.Rounded.PauseCircle, "Pause, puis reprise", "Tu changes d'activité ? Mets en pause, note où tu en es, et Tempo te rappelle de reprendre."),
    )
    val gradients = listOf(
        listOf(Color(0xFF5C5CF2), Color(0xFFBF59F2)),
        listOf(Color(0xFFFF9500), Color(0xFFFF2D55)),
        listOf(Color(0xFF30B0C7), Color(0xFF007AFF)),
    )

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Crossfade(targetState = page, label = "page") { index ->
            val (icon, title, text) = pages[index]
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(
                    Modifier.size(132.dp).background(Brush.linearGradient(gradients[index]), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(60.dp)) }
                Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp))
                if (index == pages.lastIndex) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        placeholder = { Text("Ton prénom (facultatif)") },
                        singleLine = true, shape = RoundedCornerShape(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.weight(1.4f))
        Box(Modifier.padding(bottom = 20.dp)) {
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.indices.forEach { i ->
                    Box(
                        Modifier.height(8.dp).size(width = if (i == page) 22.dp else 8.dp, height = 8.dp)
                            .background(
                                if (i == page) MaterialTheme.colorScheme.onBackground
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(4.dp),
                            ),
                    )
                }
            }
        }
        Button(
            onClick = {
                if (page < pages.lastIndex) page++
                else if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else finishOnboarding(name)
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onBackground,
                contentColor = MaterialTheme.colorScheme.background,
            ),
        ) { Text(if (page < pages.lastIndex) "Continuer" else "Commencer", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) }
    }
}

private fun finishOnboarding(name: String) {
    AppSettings.userName = name.trim()
    if (TaskStore.tasks.isEmpty()) {
        TaskStore.upsert(Task(title = "Boire un grand verre d'eau", emoji = "💧", color = "blue", repeatDays = listOf(1, 2, 3, 4, 5, 6, 7)))
        TaskStore.upsert(Task(title = "Ranger mon bureau", emoji = "🧹", color = "orange", dueDate = LocalDate.now().toString()))
    }
    AppSettings.onboarded = true
}
