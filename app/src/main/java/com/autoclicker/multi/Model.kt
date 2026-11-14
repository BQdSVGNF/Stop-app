package com.autoclicker.multi

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Une cible : un clic (point unique) ou un glissement (point de départ -> point d'arrivée). */
class TargetCfg(
    var type: Int = CLICK,
    var x: Int = 0, var y: Int = 0,
    var x2: Int = 0, var y2: Int = 0,
    var delay: Double = 100.0, var delayUnit: Int = Units.MS,        // délai avant l'action suivante
    var duration: Double = 10.0, var durationUnit: Int = Units.MS,   // durée de l'appui / du glissement
    var repeat: Int = 1                                              // répétitions de cette cible
) {
    fun toJson(): JSONObject = JSONObject()
        .put("type", type).put("x", x).put("y", y).put("x2", x2).put("y2", y2)
        .put("delay", delay).put("delayUnit", delayUnit)
        .put("duration", duration).put("durationUnit", durationUnit)
        .put("repeat", repeat)

    companion object {
        const val CLICK = 0
        const val SWIPE = 1

        fun fromJson(o: JSONObject) = TargetCfg(
            o.optInt("type"), o.optInt("x"), o.optInt("y"), o.optInt("x2"), o.optInt("y2"),
            o.optDouble("delay", 100.0), o.optInt("delayUnit"),
            o.optDouble("duration", 10.0), o.optInt("durationUnit"),
            o.optInt("repeat", 1).coerceAtLeast(1)
        )
    }
}

/** Réglages d'un mode (valeurs par défaut des nouvelles cibles + condition d'arrêt). */
class ModeCfg(
    var defDelay: Double = 100.0, var defDelayUnit: Int = Units.MS,
    var defDuration: Double = 10.0, var defDurationUnit: Int = Units.MS,
    var stopMode: Int = STOP_NEVER,
    var stopCycles: Int = 100,
    var stopTime: Double = 1.0, var stopTimeUnit: Int = Units.MIN
) {
    fun toJson(): JSONObject = JSONObject()
        .put("defDelay", defDelay).put("defDelayUnit", defDelayUnit)
        .put("defDuration", defDuration).put("defDurationUnit", defDurationUnit)
        .put("stopMode", stopMode).put("stopCycles", stopCycles)
        .put("stopTime", stopTime).put("stopTimeUnit", stopTimeUnit)

    companion object {
        const val STOP_NEVER = 0
        const val STOP_CYCLES = 1
        const val STOP_TIME = 2

        fun fromJson(o: JSONObject) = ModeCfg(
            o.optDouble("defDelay", 100.0), o.optInt("defDelayUnit"),
            o.optDouble("defDuration", 10.0), o.optInt("defDurationUnit"),
            o.optInt("stopMode"), o.optInt("stopCycles", 100).coerceAtLeast(1),
            o.optDouble("stopTime", 1.0), o.optInt("stopTimeUnit", Units.MIN)
        )
    }
}

/** Sauvegarde locale (SharedPreferences, JSON). */
object Store {
    private fun sp(c: Context) = c.getSharedPreferences("autoclicker", Context.MODE_PRIVATE)

    fun modeCfg(c: Context, mode: Int): ModeCfg =
        sp(c).getString("cfg_$mode", null)
            ?.let { runCatching { ModeCfg.fromJson(JSONObject(it)) }.getOrNull() }
            ?: if (mode == AutoClickService.MODE_MULTI) ModeCfg(defDelay = 500.0) else ModeCfg()

    fun saveModeCfg(c: Context, mode: Int, cfg: ModeCfg) =
        sp(c).edit().putString("cfg_$mode", cfg.toJson().toString()).apply()

    fun targets(c: Context, mode: Int): MutableList<TargetCfg> =
        parseList(sp(c).getString("targets_$mode", null))

    fun saveTargets(c: Context, mode: Int, list: List<TargetCfg>) =
        sp(c).edit().putString("targets_$mode", toArray(list).toString()).apply()

    // ---- Configurations nommées (mode cibles multiples) ----
    private fun configs(c: Context) =
        runCatching { JSONObject(sp(c).getString("configs", "{}")!!) }.getOrDefault(JSONObject())

    fun configNames(c: Context): List<String> =
        configs(c).keys().asSequence().toList().sortedBy { it.lowercase() }

    fun saveConfig(c: Context, name: String, list: List<TargetCfg>) {
        val all = configs(c).put(name, toArray(list))
        sp(c).edit().putString("configs", all.toString()).apply()
    }

    fun loadConfig(c: Context, name: String): MutableList<TargetCfg> =
        parseList(configs(c).optJSONArray(name)?.toString())

    fun deleteConfig(c: Context, name: String) {
        val all = configs(c); all.remove(name)
        sp(c).edit().putString("configs", all.toString()).apply()
    }

    private fun toArray(list: List<TargetCfg>) = JSONArray().apply { list.forEach { put(it.toJson()) } }

    private fun parseList(s: String?): MutableList<TargetCfg> {
        if (s == null) return mutableListOf()
        return runCatching {
            val a = JSONArray(s)
            MutableList(a.length()) { TargetCfg.fromJson(a.getJSONObject(it)) }
        }.getOrDefault(mutableListOf())
    }
}
