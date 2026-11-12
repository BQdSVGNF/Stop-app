package com.tempo.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    // Fournit la bonne couleur de texte partout (sinon le texte reste noir en mode sombre)
    Surface(color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) {
        Box(Modifier.fillMaxSize()) {
            AppBackdrop()
            Crossfade(targetState = AppSettings.onboarded, label = "onboarding") { done ->
                if (done) MainScreen() else Onboarding()
            }
        }
    }
}

@Composable
private fun MainScreen() {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    var pausing by remember { mutableStateOf<Task?>(null) }
    var capturing by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        // Contenu centré, largeur limitée sur tablette / paysage
        Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                Crossfade(targetState = tab, label = "tab") { current ->
                    when (current) {
                        Tab.TODAY -> TodayScreen(onEdit = { editing = EditTarget(it) }, onPause = { pausing = it })
                        Tab.TASKS -> TasksScreen(onEdit = { editing = EditTarget(it) }, onPause = { pausing = it })
                        Tab.SETTINGS -> SettingsScreen()
                    }
                }
            }
        }

        if (tab != Tab.SETTINGS) {
            FloatingButtons(
                onCapture = { capturing = true },
                onAdd = { editing = EditTarget(null) },
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 22.dp, bottom = 100.dp),
            )
        }

        GlassTabBar(
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp).widthIn(max = 480.dp).fillMaxWidth(),
        )
    }

    editing?.let { target -> EditorSheet(target.task) { editing = null } }
    pausing?.let { task -> PauseSheet(task) { pausing = null } }
    if (capturing) CaptureSheet { capturing = false }
}

/** Barre d'onglets flottante en verre, avec une pastille qui suit l'onglet actif. */
@Composable
private fun GlassTabBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val palette = currentPalette()
    val dark = LocalDarkTheme.current
    val activeTint = if (palette == ThemePalette.MONO) MaterialTheme.colorScheme.onSurface else palette.accent
    Glass(modifier.height(66.dp), shape = RoundedCornerShape(33.dp)) {
        Row(Modifier.fillMaxSize().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Tab.entries.forEach { item ->
                val on = item == selected
                val bg by animateColorAsState(
                    if (on) activeTint.copy(alpha = if (dark) 0.24f else 0.16f) else Color.Transparent, label = "tabBg",
                )
                val tint by animateColorAsState(
                    if (on) activeTint else MaterialTheme.colorScheme.onSurfaceVariant, label = "tabTint",
                )
                val lift by animateDpAsState(if (on) (-1).dp else 0.dp, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "lift")
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(27.dp)).background(bg)
                        .appleSpringPress { onSelect(item) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(item.icon, null, tint = tint, modifier = Modifier.size(23.dp).graphicsLayer { translationY = lift.toPx() })
                    Text(item.label, fontSize = 11.sp, color = tint, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun FloatingButtons(onCapture: () -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val palette = currentPalette()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier.size(48.dp)
                .appleSpringPress(onClick = onCapture)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .border(1.dp, Color.White.copy(alpha = if (LocalDarkTheme.current) 0.2f else 0.9f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Lightbulb, contentDescription = "Noter pour plus tard", tint = Color(0xFFFFC400)) }
        Box(
            Modifier.size(62.dp)
                .appleSpringPress(onClick = onAdd)
                .shadow(18.dp, CircleShape, ambientColor = palette.colors[0], spotColor = palette.colors[0])
                .background(Brush.linearGradient(palette.colors.take(2)), CircleShape)
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.05f))), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Add, contentDescription = "Ajouter une tâche", tint = Color.White, modifier = Modifier.size(30.dp)) }
    }
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
    val tilt = LocalTilt.current

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Crossfade(targetState = page, label = "page") { index ->
            val (icon, title, text) = pages[index]
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                // Sphère en relief qui suit l'inclinaison du téléphone
                Box(
                    Modifier.size(140.dp)
                        .graphicsLayer {
                            val t = tilt.value
                            rotationY = t.x * 18f
                            rotationX = -t.y * 14f
                            cameraDistance = 10f * density
                        }
                        .shadow(28.dp, CircleShape, ambientColor = gradients[index][0], spotColor = gradients[index][1])
                        .background(Brush.linearGradient(gradients[index]), CircleShape)
                        .background(
                            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent)),
                            CircleShape,
                        )
                        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f), Color.Transparent)), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(62.dp)) }
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
        Row(Modifier.padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pages.indices.forEach { i ->
                val w by animateDpAsState(if (i == page) 22.dp else 8.dp, label = "dot")
                Box(
                    Modifier.height(8.dp).width(w)
                        .background(
                            if (i == page) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(4.dp),
                        ),
                )
            }
        }
        Box(
            Modifier.fillMaxWidth().height(56.dp)
                .appleSpringPress {
                    if (page < pages.lastIndex) page++
                    else if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else finishOnboarding(name)
                }
                .background(MaterialTheme.colorScheme.onBackground, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (page < pages.lastIndex) "Continuer" else "Commencer",
                color = MaterialTheme.colorScheme.background, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
            )
        }
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
