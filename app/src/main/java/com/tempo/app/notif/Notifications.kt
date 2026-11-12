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
        val channel = NotificationChannel(CHANNEL_ID, "Rappels et encouragements", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Rappels de tâches, reprises et phrase du jour"
            enableVibration(true)
            enableLights(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * Vrai seulement si les notifications peuvent VRAIMENT s'afficher :
     * autorisation accordée, notifications de l'app activées et canal non coupé.
     */
    fun canNotify(context: Context): Boolean {
        val permission = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!permission) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun show(context: Context, id: Int, title: String, body: String, taskId: String? = null) {
        if (!canNotify(context)) return
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF5E5CE6.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(open)
        // Bouton « C'est fait » directement dans la notification
        if (taskId != null) builder.addAction(0, "✓ C'est fait", Scheduler.doneIntent(context, id, taskId))
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {
        }
    }
}

/**
 * Programme les rappels avec AlarmManager.
 * On ne programme que la PROCHAINE occurrence de chaque rappel ;
 * quand elle sonne, on reprogramme la suivante.
 * Une alarme de maintenance quotidienne (silencieuse) reprogramme tout,
 * pour que l'app reste autonome même si on ne l'ouvre jamais.
 */
object Scheduler {
    private const val ACTION_FIRE = "com.tempo.app.ALARM"
    const val ACTION_DONE = "com.tempo.app.DONE"
    private const val CODE_ENCOURAGEMENT = 777_001
    private const val CODE_MAINTENANCE = 777_002
    private const val CODE_TEST = 777_003

    private data class Fire(
        val code: Int,
        val time: Long,
        val title: String?,
        val body: String?,
        val taskId: String? = null,
        val exact: Boolean = true,
    )

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    fun rescheduleAll(context: Context) {
        TaskStore.init(context)
        AppSettings.init(context)
        // Les pauses arrivées à échéance redeviennent actives toutes seules
        TaskStore.resumeDue()

        val am = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences("alarms", Context.MODE_PRIVATE)

        // 1. Annuler les anciennes alarmes
        prefs.getStringSet("codes", emptySet())?.forEach { raw ->
            raw.toIntOrNull()?.let { code ->
                pendingIntent(context, code, null, null, null, create = false)?.let { am.cancel(it) }
            }
        }

        // 2. Calculer les prochaines
        val now = LocalDateTime.now()
        val fires = mutableListOf<Fire>()
        TaskStore.tasks.toList().forEach { task -> nextFire(task, now)?.let { fires.add(it) } }
        nextEncouragement(now)?.let { fires.add(it) }
        fires.add(maintenance(now))

        // 3. Programmer
        fires.forEach { schedule(context, am, it) }
        prefs.edit().putStringSet("codes", fires.map { it.code.toString() }.toSet()).apply()
    }

    /** Envoie une notification tout de suite, et une autre dans 1 minute (app fermée possible). */
    fun sendTest(context: Context): Boolean {
        Notifier.createChannel(context)
        Notifier.show(context, CODE_TEST + 1, "🔔 Test réussi", "Les notifications de Tempo s'affichent bien.")
        val am = context.getSystemService(AlarmManager::class.java)
        schedule(
            context, am,
            Fire(
                CODE_TEST, System.currentTimeMillis() + 60_000,
                "⏰ Rappel test", "Si tu lis ceci, les rappels programmés marchent, même app fermée. 🎉",
            ),
        )
        return Notifier.canNotify(context)
    }

    fun doneIntent(context: Context, code: Int, taskId: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_DONE
            data = Uri.parse("tempo://done/$code")
            putExtra("code", code)
            putExtra("taskId", taskId)
        }
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun schedule(context: Context, am: AlarmManager, fire: Fire) {
        val pi = pendingIntent(context, fire.code, fire.title, fire.body, fire.taskId, create = true) ?: return
        try {
            if (fire.exact && canScheduleExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fire.time, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fire.time, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fire.time, pi)
        }
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
        val body = when {
            task.notes.isNotBlank() -> task.notes
            task.pauseNote.isNotBlank() -> "Tu t'étais arrêté à : ${task.pauseNote}"
            else -> "C'est le moment ! Tu vas gérer 💪"
        }

        if (task.isRepeating) {
            for (offset in 0..7L) {
                val date = now.toLocalDate().plusDays(offset)
                if (date.dayOfWeek.value !in task.repeatDays) continue
                if (task.isDone(date)) continue // déjà cochée ce jour-là : pas de rappel inutile
                val at = LocalDateTime.of(date, time)
                if (at.isAfter(now)) return Fire(code, toMillis(at), title, body, task.id)
            }
            return null
        }
        if (task.completedAt != null) return null
        var at = LocalDateTime.of(task.due ?: now.toLocalDate(), time)
        if (task.due == null && !at.isAfter(now)) at = at.plusDays(1)
        return if (at.isAfter(now)) Fire(code, toMillis(at), title, body, task.id) else null
    }

    private fun nextEncouragement(now: LocalDateTime): Fire? {
        if (!AppSettings.encouragementEnabled) return null
        var at = LocalDateTime.of(now.toLocalDate(), LocalTime.of(AppSettings.encouragementHour, AppSettings.encouragementMinute))
        if (!at.isAfter(now)) at = at.plusDays(1)
        return Fire(CODE_ENCOURAGEMENT, toMillis(at), "Ton élan du jour ☀️", Quotes.forDate(at.toLocalDate()))
    }

    /** Réveil silencieux chaque nuit : tout est recalculé, sans action de ta part. */
    private fun maintenance(now: LocalDateTime): Fire {
        var at = LocalDateTime.of(now.toLocalDate(), LocalTime.of(3, 17))
        if (!at.isAfter(now)) at = at.plusDays(1)
        return Fire(CODE_MAINTENANCE, toMillis(at), null, null, exact = false)
    }

    private fun pendingIntent(context: Context, code: Int, title: String?, body: String?, taskId: String?, create: Boolean): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            data = Uri.parse("tempo://alarm/$code")
            putExtra("code", code)
            title?.let { putExtra("title", it) }
            body?.let { putExtra("body", it) }
            taskId?.let { putExtra("taskId", it) }
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, code, intent, flags)
    }
}

/** Déclenché par une alarme (ou le bouton « C'est fait ») : affiche la notification puis programme la suivante. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TaskStore.init(context)
        AppSettings.init(context)
        Notifier.createChannel(context)

        if (intent.action == Scheduler.ACTION_DONE) {
            val id = intent.getStringExtra("taskId")
            if (id != null) TaskStore.update(id) { if (it.isDone()) it else it.toggled() }
            NotificationManagerCompat.from(context).cancel(intent.getIntExtra("code", 0))
            return
        }

        val title = intent.getStringExtra("title")
        if (title != null) {
            Notifier.show(context, intent.getIntExtra("code", 0), title, intent.getStringExtra("body") ?: "", intent.getStringExtra("taskId"))
        }
        Scheduler.rescheduleAll(context)
    }
}

/** Redémarrage, mise à jour de l'app, changement d'heure : les alarmes sont reprogrammées. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TaskStore.init(context)
        AppSettings.init(context)
        Notifier.createChannel(context)
        Scheduler.rescheduleAll(context)
    }
}
