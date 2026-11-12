package com.tempo.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.tempo.app.notif.Scheduler
import org.json.JSONArray
import java.io.File

/** Stockage des tâches dans un fichier JSON sur le téléphone. */
object TaskStore {
    val tasks = mutableStateListOf<Task>()
    private var file: File? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        if (file != null) return
        appContext = context.applicationContext
        file = File(context.filesDir, "tasks.json")
        load()
    }

    private fun load() {
        val f = file ?: return
        if (!f.exists()) return
        runCatching {
            val array = JSONArray(f.readText())
            tasks.clear()
            for (i in 0 until array.length()) tasks.add(Task.fromJson(array.getJSONObject(i)))
        }
    }

    private fun persist() {
        val f = file ?: return
        val array = JSONArray()
        tasks.forEach { array.put(it.toJson()) }
        runCatching { f.writeText(array.toString()) }
    }

    private fun save() {
        persist()
        appContext?.let { Scheduler.rescheduleAll(it) }
    }

    /**
     * Les tâches en pause dont l'heure de reprise est passée redeviennent actives
     * toutes seules (la note « où j'en étais » est gardée).
     */
    fun resumeDue(now: Long = System.currentTimeMillis()): Boolean {
        var changed = false
        for (i in tasks.indices) {
            val t = tasks[i]
            val r = t.resumeAt
            if (t.isPaused && r != null && r <= now) {
                tasks[i] = t.resumed()
                changed = true
            }
        }
        if (changed) persist()
        return changed
    }

    fun upsert(task: Task) {
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index >= 0) tasks[index] = task else tasks.add(task)
        save()
    }

    fun update(id: String, change: (Task) -> Task) {
        val index = tasks.indexOfFirst { it.id == id }
        if (index >= 0) {
            tasks[index] = change(tasks[index])
            save()
        }
    }

    fun delete(id: String) {
        tasks.removeAll { it.id == id }
        save()
    }
}

/** Réglages de l'app, observables par l'interface. */
object AppSettings {
    private lateinit var prefs: SharedPreferences

    private val _onboarded = mutableStateOf(false)
    private val _userName = mutableStateOf("")
    private val _encEnabled = mutableStateOf(true)
    private val _encHour = mutableStateOf(8)
    private val _encMinute = mutableStateOf(0)
    private val _style = mutableStateOf("LIQUID_GLASS")
    private val _palette = mutableStateOf("INDIGO")
    private val _appearance = mutableStateOf("system")
    private val _motion3d = mutableStateOf(true)

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("tempo", Context.MODE_PRIVATE)
        _onboarded.value = prefs.getBoolean("onboarded", false)
        _userName.value = prefs.getString("userName", "") ?: ""
        _encEnabled.value = prefs.getBoolean("encEnabled", true)
        _encHour.value = prefs.getInt("encHour", 8)
        _encMinute.value = prefs.getInt("encMinute", 0)
        _style.value = prefs.getString("style", "LIQUID_GLASS") ?: "LIQUID_GLASS"
        _palette.value = prefs.getString("palette", "INDIGO") ?: "INDIGO"
        _appearance.value = prefs.getString("appearance", "system") ?: "system"
        _motion3d.value = prefs.getBoolean("motion3d", true)
    }

    var onboarded: Boolean
        get() = _onboarded.value
        set(v) { _onboarded.value = v; prefs.edit().putBoolean("onboarded", v).apply() }
    var userName: String
        get() = _userName.value
        set(v) { _userName.value = v; prefs.edit().putString("userName", v).apply() }
    var encouragementEnabled: Boolean
        get() = _encEnabled.value
        set(v) { _encEnabled.value = v; prefs.edit().putBoolean("encEnabled", v).apply() }
    var encouragementHour: Int
        get() = _encHour.value
        set(v) { _encHour.value = v; prefs.edit().putInt("encHour", v).apply() }
    var encouragementMinute: Int
        get() = _encMinute.value
        set(v) { _encMinute.value = v; prefs.edit().putInt("encMinute", v).apply() }
    var style: String
        get() = _style.value
        set(v) { _style.value = v; prefs.edit().putString("style", v).apply() }
    var palette: String
        get() = _palette.value
        set(v) { _palette.value = v; prefs.edit().putString("palette", v).apply() }
    var appearance: String
        get() = _appearance.value
        set(v) { _appearance.value = v; prefs.edit().putString("appearance", v).apply() }
    var motion3d: Boolean
        get() = _motion3d.value
        set(v) { _motion3d.value = v; prefs.edit().putBoolean("motion3d", v).apply() }
}
