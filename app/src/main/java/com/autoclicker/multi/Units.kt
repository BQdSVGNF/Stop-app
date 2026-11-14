package com.autoclicker.multi

import android.text.InputType
import android.text.method.DigitsKeyListener
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToLong

/** Unités de temps + lecture des nombres décimaux (virgule OU point acceptés). */
object Units {
    const val MS = 0
    const val SEC = 1
    const val MIN = 2
    val NAMES = arrayOf("ms", "s", "min")

    /** Convertit une valeur (ex : 1,5) dans une unité en millisecondes. */
    fun toMs(value: Double, unit: Int): Long {
        val ms = when (unit) {
            SEC -> value * 1_000.0
            MIN -> value * 60_000.0
            else -> value
        }
        return if (ms.isNaN() || ms < 0) 0L else ms.roundToLong()
    }

    /**
     * "1,5" -> 1.5 · "0.25" -> 0.25 · "300" -> 300.0 · ",5" -> 0.5
     * Retourne null si la saisie est invalide.
     */
    fun parse(text: String): Double? {
        val t = text.replace(Regex("\\s"), "").replace(',', '.')
        if (t.isEmpty() || t == "." || t.count { it == '.' } > 1) return null
        val v = t.toDoubleOrNull() ?: return null
        return if (v.isNaN() || v.isInfinite() || v < 0) null else v
    }

    private val fmt = DecimalFormat("0.######", DecimalFormatSymbols(Locale.FRANCE)).apply {
        isGroupingUsed = false
    }

    /** 1.5 -> "1,5" */
    fun fmt(v: Double): String = fmt.format(v)

    fun describe(v: Double, unit: Int) = "${fmt(v)} ${NAMES[unit]}"
}

/** Clavier numérique qui accepte les chiffres, la virgule et le point. */
class DecimalKeyListener : DigitsKeyListener(false, false) {
    override fun getAcceptedChars(): CharArray = CHARS
    override fun getInputType(): Int =
        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL

    companion object {
        private val CHARS = "0123456789.,".toCharArray()
    }
}
