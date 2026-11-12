package com.tempo.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

/**
 * Une tâche ou une routine.
 * - repeatDays vide  → tâche ponctuelle
 * - repeatDays rempli → routine (1 = lundi … 7 = dimanche)
 */
data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val notes: String = "",
    val emoji: String = "✨",
    val color: String = "indigo",
    val createdAt: Long = System.currentTimeMillis(),
    val dueDate: String? = null,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val repeatDays: List<Int> = emptyList(),
    val completedAt: Long? = null,
    val completedDays: List<String> = emptyList(),
    val pausedAt: Long? = null,
    val pauseNote: String = "",
    val resumeAt: Long? = null,
    val isLater: Boolean = false,
) {
    val isRepeating: Boolean get() = repeatDays.isNotEmpty()
    val due: LocalDate? get() = dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun isDone(date: LocalDate = LocalDate.now()): Boolean =
        if (isRepeating) date.toString() in completedDays else completedAt != null

    fun toggled(date: LocalDate = LocalDate.now()): Task {
        if (isRepeating) {
            val key = date.toString()
            return copy(completedDays = if (key in completedDays) completedDays - key else completedDays + key)
        }
        return if (completedAt == null) {
            copy(completedAt = System.currentTimeMillis(), pausedAt = null, resumeAt = null, pauseNote = "")
        } else {
            copy(completedAt = null)
        }
    }

    // Pause
    val isPaused: Boolean get() = pausedAt != null && !isRepeating && completedAt == null

    fun paused(note: String, resumeAt: Long?): Task =
        copy(pausedAt = System.currentTimeMillis(), pauseNote = note, resumeAt = resumeAt, isLater = false)

    /** Reprise : la note « où j'en étais » reste visible jusqu'à ce que la tâche soit faite. */
    fun resumed(): Task = copy(pausedAt = null, resumeAt = null)

    fun belongsToToday(): Boolean {
        val today = LocalDate.now()
        if (isLater && completedAt == null) return false
        if (isPaused) return false
        if (isRepeating) return today.dayOfWeek.value in repeatDays
        completedAt?.let { return toLocalDate(it) == today }
        val d = due ?: return true
        return !d.isAfter(today)
    }

    val isOverdue: Boolean
        get() {
            if (isRepeating || completedAt != null) return false
            val d = due ?: return false
            return d.isBefore(LocalDate.now())
        }

    val sortMinutes: Int get() = if (reminderEnabled) reminderHour * 60 + reminderMinute else 24 * 60

    val reminderText: String get() = "%02d:%02d".format(reminderHour, reminderMinute)

    fun repeatSummary(): String {
        val days = repeatDays.toSet()
        return when {
            days.size == 7 -> "Chaque jour"
            days == setOf(1, 2, 3, 4, 5) -> "En semaine"
            days == setOf(6, 7) -> "Le week-end"
            else -> Weekdays.shortNames.filterKeys { it in days }.values.joinToString(", ")
        }
    }

    fun dueText(): String? {
        if (isRepeating) return null
        val d = due ?: return null
        val today = LocalDate.now()
        return when (d) {
            today -> "Aujourd'hui"
            today.plusDays(1) -> "Demain"
            today.minusDays(1) -> "Hier"
            else -> d.format(DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH))
        }
    }

    fun pausedSinceText(): String {
        val p = pausedAt ?: return ""
        val days = ChronoUnit.DAYS.between(toLocalDate(p), LocalDate.now())
        return when {
            days < 1 -> "En pause depuis aujourd'hui"
            days == 1L -> "En pause depuis hier"
            else -> "En pause depuis $days jours"
        }
    }

    fun resumeText(): String? {
        val r = resumeAt ?: return null
        if (r <= System.currentTimeMillis()) return null
        val dt = toLocalDateTime(r)
        val time = dt.format(DateTimeFormatter.ofPattern("HH:mm"))
        val today = LocalDate.now()
        return when (dt.toLocalDate()) {
            today -> "Reprise aujourd'hui à $time"
            today.plusDays(1) -> "Reprise demain à $time"
            else -> "Reprise " + dt.format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.FRENCH)) + " à $time"
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("title", title); put("notes", notes); put("emoji", emoji); put("color", color)
        put("createdAt", createdAt); put("dueDate", dueDate ?: JSONObject.NULL)
        put("reminderEnabled", reminderEnabled); put("reminderHour", reminderHour); put("reminderMinute", reminderMinute)
        put("repeatDays", JSONArray(repeatDays)); put("completedAt", completedAt ?: JSONObject.NULL)
        put("completedDays", JSONArray(completedDays)); put("pausedAt", pausedAt ?: JSONObject.NULL)
        put("pauseNote", pauseNote); put("resumeAt", resumeAt ?: JSONObject.NULL); put("isLater", isLater)
    }

    companion object {
        fun fromJson(o: JSONObject): Task {
            fun optLong(key: String): Long? = if (o.isNull(key) || !o.has(key)) null else o.getLong(key)
            fun optString(key: String): String? = if (o.isNull(key) || !o.has(key)) null else o.getString(key)
            val days = o.optJSONArray("repeatDays")?.let { a -> List(a.length()) { a.getInt(it) } } ?: emptyList()
            val done = o.optJSONArray("completedDays")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
            return Task(
                id = o.getString("id"),
                title = o.optString("title"),
                notes = o.optString("notes"),
                emoji = o.optString("emoji", "✨"),
                color = o.optString("color", "indigo"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                dueDate = optString("dueDate"),
                reminderEnabled = o.optBoolean("reminderEnabled"),
                reminderHour = o.optInt("reminderHour", 9),
                reminderMinute = o.optInt("reminderMinute", 0),
                repeatDays = days,
                completedAt = optLong("completedAt"),
                completedDays = done,
                pausedAt = optLong("pausedAt"),
                pauseNote = o.optString("pauseNote"),
                resumeAt = optLong("resumeAt"),
                isLater = o.optBoolean("isLater"),
            )
        }
    }
}

object Weekdays {
    /** Lundi → dimanche */
    val ordered = listOf(1, 2, 3, 4, 5, 6, 7)
    val letters = mapOf(1 to "L", 2 to "M", 3 to "M", 4 to "J", 5 to "V", 6 to "S", 7 to "D")
    val shortNames = linkedMapOf(1 to "Lun", 2 to "Mar", 3 to "Mer", 4 to "Jeu", 5 to "Ven", 6 to "Sam", 7 to "Dim")
}

object Stats {
    fun streak(tasks: List<Task>): Int {
        val days = mutableSetOf<String>()
        tasks.forEach { t ->
            days.addAll(t.completedDays)
            t.completedAt?.let { days.add(toLocalDate(it).toString()) }
        }
        var day = LocalDate.now()
        if (day.toString() !in days) day = day.minusDays(1)
        var count = 0
        while (day.toString() in days) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    fun totalCompleted(tasks: List<Task>): Int =
        tasks.sumOf { it.completedDays.size + if (it.completedAt != null) 1 else 0 }
}

fun toLocalDateTime(millis: Long): LocalDateTime =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()

fun toLocalDate(millis: Long): LocalDate = toLocalDateTime(millis).toLocalDate()

fun toMillis(dateTime: LocalDateTime): Long =
    dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
