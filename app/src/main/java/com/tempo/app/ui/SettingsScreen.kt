package com.tempo.app.ui

import android.content.Intent
import android.net.Uri
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.content.IntentCompat
import com.tempo.app.data.AppSettings
import com.tempo.app.data.Quotes
import com.tempo.app.data.Stats
import com.tempo.app.data.TaskStore
import com.tempo.app.notif.Notifier
import com.tempo.app.notif.Scheduler
import java.util.Locale

@Composable
fun SettingsScreen() {
    var showTheme by rememberSaveable { mutableStateOf(false) }
    if (showTheme) {
        BackHandler { showTheme = false }
        ThemeScreen(onBack = { showTheme = false })
        return
    }

    val context = LocalContext.current
    val palette = currentPalette()
    val style = currentStyle()
    var showTime by remember { mutableStateOf(false) }

    // Rafraîchir l'état des autorisations quand on revient dans l'app
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val canNotify = remember(refresh) { Notifier.canNotify(context) }
    val exact = remember(refresh) { Scheduler.canScheduleExact(context) }
    val batteryFree = remember(refresh) {
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }
    var testResult by remember { mutableStateOf<Boolean?>(null) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 190.dp)) {
        Text("Réglages", fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
        Spacer(Modifier.height(16.dp))

        // Profil
        Group {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
                val initial = AppSettings.userName.trim().take(1).uppercase(Locale.FRENCH).ifEmpty { "T" }
                Box(Modifier.size(56.dp).background(Brush.linearGradient(palette.colors.take(2)), CircleShape), contentAlignment = Alignment.Center) {
                    Text(initial, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    TextField(
                        value = AppSettings.userName, onValueChange = { AppSettings.userName = it },
                        placeholder = { Text("Ton prénom") }, singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        ),
                        textStyle = MaterialTheme.typography.titleMedium,
                    )
                    Text("Utilisé pour te saluer", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp))
                }
            }
        }

        Label("Apparence")
        Group {
            Row(
                Modifier.fillMaxWidth().clickable { showTheme = true }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(width = 56.dp, height = 40.dp).clip(RoundedCornerShape(10.dp))) {
                    ThemeBackground(style, palette, Modifier.matchParentSize())
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Thème")
                    Text("${style.label} · ${palette.label}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Divider()
            SettingRow(Icons.Rounded.AutoAwesome, "Effet 3D et animations") {
                Switch(AppSettings.motion3d, { AppSettings.motion3d = it })
            }
        }
        Footer("Le décor suit l'inclinaison de ton téléphone. Désactive-le pour économiser un peu de batterie.")

        Label("Statistiques")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(Stats.streak(TaskStore.tasks), "Jours d'affilée", Icons.Rounded.LocalFireDepartment, Orange, Modifier.weight(1f))
            StatTile(Stats.totalCompleted(TaskStore.tasks), "Tâches accomplies", Icons.Rounded.Verified, Color(0xFF34C759), Modifier.weight(1f))
        }

        Label("Motivation")
        Group {
            SettingRow(Icons.Rounded.AutoAwesome, "Encouragement quotidien") {
                Switch(AppSettings.encouragementEnabled, { AppSettings.encouragementEnabled = it; Scheduler.rescheduleAll(context) })
            }
            if (AppSettings.encouragementEnabled) {
                Divider()
                Row(Modifier.fillMaxWidth().clickable { showTime = true }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Heure", Modifier.weight(1f))
                    Text("%02d:%02d".format(AppSettings.encouragementHour, AppSettings.encouragementMinute),
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
        Footer("Aujourd'hui : « ${Quotes.today} »")

        Label("Fiabilité des rappels")
        Group {
            SettingRow(Icons.Rounded.Notifications, "Notifications") {
                if (canNotify) Status("Activées", true) else Action("Activer") {
                    context.openSettings(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }
            }
            if (Build.VERSION.SDK_INT >= 31) {
                Divider()
                SettingRow(Icons.Rounded.AlarmOn, "Rappels à l'heure exacte") {
                    if (exact) Status("Activés", true) else Action("Autoriser") {
                        context.openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    }
                }
            }
            Divider()
            SettingRow(Icons.Rounded.BatteryChargingFull, "Batterie sans restriction") {
                if (batteryFree) Status("Oui", true) else Action("Autoriser") {
                    context.openSettings(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                    )
                }
            }
            if (Build.VERSION.SDK_INT >= 30) {
                Divider()
                SettingRow(Icons.Rounded.Schedule, "Ne jamais mettre en veille") {
                    Action("Régler") {
                        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        val unused = runCatching { IntentCompat.createManageUnusedAppRestrictionsIntent(context, context.packageName) }.getOrNull()
                        context.openSettings(unused ?: details, details)
                    }
                }
            }
            Divider()
            Row(
                Modifier.fillMaxWidth().clickable { testResult = Scheduler.sendTest(context) }.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Tester les rappels", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
        when (testResult) {
            true -> Footer("✅ Une notification vient de partir. Une 2ᵉ arrivera dans 1 minute : tu peux fermer l'app pour vérifier.")
            false -> Footer("⚠️ Les notifications sont bloquées. Appuie sur « Activer » ci-dessus.")
            null -> Footer("Tout est géré par ton téléphone, même sans Internet et app fermée. Tempo se reprogramme tout seul chaque nuit, après un redémarrage et après une mise à jour : aucune action de ta part.")
        }

        Spacer(Modifier.height(24.dp))
        Text("Tempo 1.1 · garde le rythme, un jour à la fois.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }

    if (showTime) {
        TimeDialog(AppSettings.encouragementHour, AppSettings.encouragementMinute, onDismiss = { showTime = false }) { h, m ->
            AppSettings.encouragementHour = h
            AppSettings.encouragementMinute = m
            Scheduler.rescheduleAll(context)
            showTime = false
        }
    }
}

/** Ouvre un écran des réglages Android, avec un plan B si le téléphone ne le propose pas. */
private fun Context.openSettings(intent: Intent, fallback: Intent? = null) {
    runCatching { startActivity(intent) }.onFailure {
        val alt = fallback ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        runCatching { startActivity(alt) }
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column { content() }
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(Locale.FRENCH), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 14.dp, top = 24.dp, bottom = 6.dp))
}

@Composable
private fun Footer(text: String) {
    Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
}

@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(start = 14.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
}

@Composable
private fun SettingRow(icon: ImageVector, label: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun Status(text: String, ok: Boolean) {
    Text(text, color = if (ok) Color(0xFF34C759) else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
}

@Composable
private fun Action(text: String, onClick: () -> Unit) {
    Text(text, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(8.dp))
}

@Composable
private fun StatTile(value: Int, label: String, icon: ImageVector, color: Color, modifier: Modifier) {
    Glass(modifier, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = color)
            Text("$value", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// MARK: - Écran Thème

@Composable
fun ThemeScreen(onBack: () -> Unit) {
    val palette = currentPalette()
    val style = currentStyle()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 190.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
            Text("Thème", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        EncouragementCard()

        Spacer(Modifier.height(26.dp))
        Text("Style", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Le décor de toute l'app, appliqué en direct", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        ThemeStyle.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                pair.forEach { item ->
                    val selected = item == style
                    Box(
                        Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(18.dp))
                            .border(if (selected) 3.dp else 1.dp, if (selected) palette.accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                            .clickable { AppSettings.style = item.name },
                    ) {
                        ThemeBackground(item, palette, Modifier.matchParentSize())
                        Text(
                            item.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111111),
                            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                                .background(Color.White.copy(alpha = 0.8f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                        if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = Color.White,
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Couleurs", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(palette.label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ThemePalette.entries.forEach { item ->
                val selected = item == palette
                Column(
                    Modifier.width(72.dp).clickable { AppSettings.palette = item.name },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier.size(56.dp).border(3.dp, if (selected) item.accent else Color.Transparent, CircleShape).padding(5.dp)
                            .background(Brush.sweepGradient(item.colors + item.colors[0]), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.size(18.dp).background(item.dark, CircleShape)) }
                    Text(item.label, fontSize = 11.sp, maxLines = 1,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(26.dp))
        Text("Apparence", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Clair, sombre ou automatique", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        val modes = listOf("system" to "Auto", "light" to "Clair", "dark" to "Sombre")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            modes.forEachIndexed { i, (key, label) ->
                SegmentedButton(
                    selected = AppSettings.appearance == key,
                    onClick = { AppSettings.appearance = key },
                    shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                ) { Text(label) }
            }
        }
    }
}
