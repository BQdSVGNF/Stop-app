package com.tempo.app.notif

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tempo.app.MainActivity
import com.tempo.app.R
import com.tempo.app.data.AppSettings
import com.tempo.app.data.Quotes
import com.tempo.app.data.Task
import com.tempo.app.data.TaskStore
import com.tempo.app.data.toMillis
import java.time.LocalDateTime
import java.time.LocalTime

/** Affiche les notifications. */
object Notifier {
    const val CHANNEL_ID = "rappels"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Rappels et encouragements", NotificationManager.IMPORTANCE_HIGH)
        channel.description = "Rappels de tâches, reprises et phrase du jour"
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, id: Int, title: String, body: String) {
        if (!canNotify(context)) return
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }
}

/**
 * Programme les rappels avec AlarmManager.
 * On ne programme que la PROCHAINE occurrence de chaque rappel ;
 * quand elle sonne, on reprogramme la suivante.
 */
object Scheduler {
    private const val ACTION = "com.tempo.app.ALARM"

    private data class Fire(val code: Int, val time: Long, val title: String, val body: String)

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    fun rescheduleAll(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences("alarms", Context.MODE_PRIVATE)

        // 1. Annuler les anciennes alarmes
        prefs.getStringSet("codes", emptySet())?.forEach { code ->
            pendingIntent(context, code.toInt(), null, null, create = false)?.let { am.cancel(it) }
        }

        // 2. Calculer les prochaines
        val now = LocalDateTime.now()
        val fires = mutableListOf<Fire>()
        TaskStore.tasks.forEach { task -> nextFire(task, now)?.let { fires.add(it) } }
        nextEncouragement(now)?.let { fires.add(it) }

        // 3. Programmer
        fires.forEach { fire ->
            val pi = pendingIntent(context, fire.code, fire.title, fire.body, create = true) ?: return@forEach
            if (canScheduleExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fire.time, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fire.time, pi)
            }
        }
        prefs.edit().putStringSet("codes", fires.map { it.code.toString() }.toSet()).apply()
    }

    private fun nextFire(task: Task, now: LocalDateTime): Fire? {
        val code = task.id.hashCode()
        if (task.isPaused) {
            val resume = task.resumeAt ?: return null
            if (resume <= System.currentTimeMillis()) return null
            val body = if (task.pauseNote.isBlank())
                "Tu l'avais mise en pause. C'est le bon moment pour t'y remettre !"
            else "Tu t'étais arrêté à : ${task.pauseNote}"
            return Fire(code, resume, "▶️ Reprends : ${task.title}", body)
        }
        if (!task.reminderEnabled) return null
        val time = LocalTime.of(task.reminderHour, task.reminderMinute)
        val title = "${task.emoji} ${task.title}"
        val body = task.notes.ifBlank { "C'est le moment ! Tu vas gérer 💪" }

        if (task.isRepeating) {
            for (offset in 0..7L) {
                val date = now.toLocalDate().plusDays(offset)
                if (date.dayOfWeek.value !in task.repeatDays) continue
                val at = LocalDateTime.of(date, time)
                if (at.isAfter(now)) return Fire(code, toMillis(at), title, body)
            }
            return null
        }
        if (task.completedAt != null) return null
        var at = LocalDateTime.of(task.due ?: now.toLocalDate(), time)
        if (task.due == null && !at.isAfter(now)) at = at.plusDays(1)
        return if (at.isAfter(now)) Fire(code, toMillis(at), title, body) else null
    }

    private fun nextEncouragement(now: LocalDateTime): Fire? {
        if (!AppSettings.encouragementEnabled) return null
        var at = LocalDateTime.of(now.toLocalDate(), LocalTime.of(AppSettings.encouragementHour, AppSettings.encouragementMinute))
        if (!at.isAfter(now)) at = at.plusDays(1)
        return Fire(777_001, toMillis(at), "Ton élan du jour ☀️", Quotes.forDate(at.toLocalDate()))
    }

    private fun pendingIntent(context: Context, code: Int, title: String?, body: String?, create: Boolean): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION
            data = Uri.parse("tempo://alarm/$code")
            putExtra("code", code)
            title?.let { putExtra("title", it) }
            body?.let { putExtra("body", it) }
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, code, intent, flags)
    }
}

/** Déclenché par une alarme : affiche la notification puis programme la suivante. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TaskStore.init(context)
        AppSettings.init(context)
        Notifier.createChannel(context)
        val title = intent.getStringExtra("title") ?: return
        val body = intent.getStringExtra("body") ?: ""
        Notifier.show(context, intent.getIntExtra("code", 0), title, body)
        Scheduler.rescheduleAll(context)
    }
}

/** Après un redémarrage du téléphone, les alarmes sont effacées : on les reprogramme. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TaskStore.init(context)
        AppSettings.init(context)
        Notifier.createChannel(context)
        Scheduler.rescheduleAll(context)
    }
}
