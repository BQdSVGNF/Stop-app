package com.autoclicker.multi

import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

/**
 * Une ligne "libellé + champ numérique + bouton d'unité (ms / s / min)".
 * Le champ accepte les décimales : 1,5 · 0.25 · 300
 */
class NumberRow(
    ctx: Context,
    label: String,
    value: Double,
    initialUnit: Int,
    withUnit: Boolean = true,
    private val integer: Boolean = false
) {
    var unit = initialUnit
        private set
    val edit = EditText(ctx)
    val view = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, ctx.dp(10), 0, 0)
    }

    init {
        view.addView(TextView(ctx).apply {
            text = label
            textSize = 14f
            setTextColor(Color.parseColor("#555555"))
        })
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        edit.setSingleLine()
        edit.setSelectAllOnFocus(true)
        if (integer) {
            edit.inputType = InputType.TYPE_CLASS_NUMBER
            edit.setText(value.toLong().toString())
        } else {
            edit.keyListener = DecimalKeyListener()   // chiffres + virgule + point
            edit.setText(Units.fmt(value))
        }
        row.addView(edit, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (withUnit) {
            val btn = Button(ctx)
            btn.isAllCaps = false
            btn.text = Units.NAMES[unit]
            btn.setOnClickListener {
                unit = (unit + 1) % Units.NAMES.size        // ms -> s -> min -> ms
                btn.text = Units.NAMES[unit]
            }
            row.addView(btn, LinearLayout.LayoutParams(ctx.dp(84), LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        view.addView(row)
    }

    fun readDouble(): Double? = Units.parse(edit.text.toString()).also {
        if (it == null) edit.error = "Nombre invalide (ex : 1,5 ou 300)"
    }

    fun readInt(min: Int = 1): Int? = edit.text.toString().trim().toIntOrNull()?.takeIf { it >= min }.also {
        if (it == null) edit.error = "Nombre entier ≥ $min"
    }

    fun setEnabled(b: Boolean) {
        view.alpha = if (b) 1f else 0.4f
        edit.isEnabled = b
        (edit.parent as? LinearLayout)?.let { r -> for (i in 0 until r.childCount) r.getChildAt(i).isEnabled = b }
    }
}

fun sectionTitle(ctx: Context, s: String) = TextView(ctx).apply {
    text = s
    textSize = 15f
    setTypeface(typeface, android.graphics.Typeface.BOLD)
    setTextColor(Color.parseColor("#00796B"))
    setPadding(0, ctx.dp(16), 0, 0)
}

fun hint(ctx: Context, s: String) = TextView(ctx).apply {
    text = s
    textSize = 12.5f
    setTextColor(Color.parseColor("#888888"))
    setPadding(0, ctx.dp(10), 0, 0)
}

/** Formulaire des paramètres d'un mode (utilisé dans l'appli ET dans le panneau flottant). */
class SettingsForm(ctx: Context, private val cfg: ModeCfg, single: Boolean) {
    val view = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    private val delay = NumberRow(
        ctx, if (single) "Intervalle entre deux clics" else "Délai par défaut entre deux actions",
        cfg.defDelay, cfg.defDelayUnit
    )
    private val dur = NumberRow(ctx, "Durée d'un appui", cfg.defDuration, cfg.defDurationUnit)
    private val rNever = RadioButton(ctx).apply { id = View.generateViewId(); text = "Infini (jusqu'à STOP)" }
    private val rCycles = RadioButton(ctx).apply { id = View.generateViewId(); text = if (single) "Après un nombre de clics" else "Après un nombre de cycles" }
    private val rTime = RadioButton(ctx).apply { id = View.generateViewId(); text = "Après une durée" }
    private val group = RadioGroup(ctx)
    private val cycles = NumberRow(ctx, if (single) "Nombre de clics" else "Nombre de cycles", cfg.stopCycles.toDouble(), 0, withUnit = false, integer = true)
    private val time = NumberRow(ctx, "Durée totale", cfg.stopTime, cfg.stopTimeUnit)

    init {
        view.addView(sectionTitle(ctx, if (single) "Cible unique" else "Nouvelles cibles"))
        view.addView(delay.view)
        view.addView(dur.view)
        if (single) view.addView(hint(ctx, "Ces valeurs s'appliquent aussi à la cible actuelle."))
        view.addView(sectionTitle(ctx, "Arrêt automatique"))
        group.addView(rNever); group.addView(rCycles); group.addView(rTime)
        view.addView(group)
        view.addView(cycles.view)
        view.addView(time.view)
        view.addView(hint(ctx, "Décimales acceptées avec virgule ou point : 0,5 s · 1,25 min · 8 ms · 300 min"))
        group.check(when (cfg.stopMode) {
            ModeCfg.STOP_CYCLES -> rCycles.id
            ModeCfg.STOP_TIME -> rTime.id
            else -> rNever.id
        })
        refresh()
        group.setOnCheckedChangeListener { _, _ -> refresh() }
    }

    private fun refresh() {
        cycles.setEnabled(group.checkedRadioButtonId == rCycles.id)
        time.setEnabled(group.checkedRadioButtonId == rTime.id)
    }

    /** Valide et applique dans cfg. Retourne false si une valeur est invalide. */
    fun apply(): Boolean {
        val d = delay.readDouble() ?: return false
        val du = dur.readDouble() ?: return false
        val mode = when (group.checkedRadioButtonId) {
            rCycles.id -> ModeCfg.STOP_CYCLES
            rTime.id -> ModeCfg.STOP_TIME
            else -> ModeCfg.STOP_NEVER
        }
        if (mode == ModeCfg.STOP_CYCLES) cfg.stopCycles = cycles.readInt(1) ?: return false
        if (mode == ModeCfg.STOP_TIME) {
            val t = time.readDouble() ?: return false
            if (t <= 0) { time.edit.error = "Doit être > 0"; return false }
            cfg.stopTime = t; cfg.stopTimeUnit = time.unit
        }
        cfg.stopMode = mode
        cfg.defDelay = d; cfg.defDelayUnit = delay.unit
        cfg.defDuration = du; cfg.defDurationUnit = dur.unit
        return true
    }
}

/** ScrollView avec hauteur max (pour les fenêtres flottantes). */
class MaxHeightScrollView(ctx: Context, private val maxH: Int) : ScrollView(ctx) {
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST))
    }
}
