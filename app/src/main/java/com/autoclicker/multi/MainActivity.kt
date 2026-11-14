package com.autoclicker.multi

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var singleBtn: Button
    private lateinit var multiBtn: Button
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(24))
        }

        status = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { askEnableService() }
        }
        root.addView(status)

        // ---- MODE CIBLE UNIQUE
        card(root, "MODE CIBLE UNIQUE").apply {
            row(this, "⚙", "Paramètres") { modeSettings(AutoClickService.MODE_SINGLE) }
            singleBtn = toggle(this) { onToggle(AutoClickService.MODE_SINGLE) }
        }

        // ---- MODE CIBLES MULTIPLES
        card(root, "MODE CIBLES MULTIPLES").apply {
            row(this, "⚙", "Paramètres") { modeSettings(AutoClickService.MODE_MULTI) }
            row(this, "📖", "Instructions") { instructions() }
            row(this, "🔧", "Gérer les configurations") { manageConfigs() }
            multiBtn = toggle(this) { onToggle(AutoClickService.MODE_MULTI) }
        }

        // ---- AUTRES
        card(root, "AUTRES").apply {
            row(this, "⚙", "Paramètres généraux") { generalSettings() }
            row(this, "❓", "Dépannage") { troubleshooting() }
        }

        root.addView(TextView(this).apply {
            text = "Ver 1.0"; textSize = 16f; gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val svc = AutoClickService.instance
        if (svc == null) {
            status.text = "⚠ Service d'accessibilité désactivé – appuyez ici pour l'activer"
            status.setTextColor(Color.parseColor("#C62828"))
            status.visibility = android.view.View.VISIBLE
        } else {
            status.visibility = android.view.View.GONE
        }
        setToggle(singleBtn, svc?.activeMode == AutoClickService.MODE_SINGLE)
        setToggle(multiBtn, svc?.activeMode == AutoClickService.MODE_MULTI)
    }

    private fun onToggle(mode: Int) {
        val svc = AutoClickService.instance
        if (svc == null) { askEnableService(); return }
        if (svc.activeMode == mode) {
            svc.closeMode()
        } else {
            svc.startMode(mode)
            moveTaskToBack(true)   // revenir à l'appli à automatiser
        }
        refresh()
    }

    // ------------------------------------------------------------------ dialogues

    private fun askEnableService() {
        AlertDialog.Builder(this)
            .setTitle("Activer le service")
            .setMessage(
                "Pour cliquer à votre place, Auto Clicker utilise le service d'accessibilité d'Android.\n\n" +
                "1. Ouvrez Accessibilité → Applications installées (ou Services installés)\n" +
                "2. Choisissez « Auto Clicker » et activez-le.\n\n" +
                "Android 13+ : si l'option est grisée, allez dans Infos de l'appli → ⋮ → " +
                "« Autoriser les paramètres restreints », puis réessayez."
            )
            .setPositiveButton("Ouvrir les réglages") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNeutralButton("Infos de l'appli") { _, _ -> openAppInfo() }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun modeSettings(mode: Int) {
        val cfg = Store.modeCfg(this, mode)
        val single = mode == AutoClickService.MODE_SINGLE
        val form = SettingsForm(this, cfg, single)
        val d = AlertDialog.Builder(this)
            .setTitle(if (single) "Paramètres – cible unique" else "Paramètres – cibles multiples")
            .setView(ScrollView(this).apply {
                setPadding(dp(22), 0, dp(22), 0)
                addView(form.view)
            })
            .setPositiveButton("OK", null)
            .setNegativeButton("Annuler", null)
            .create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (form.apply()) {
                    Store.saveModeCfg(this, mode, cfg)
                    if (single) {
                        // appliquer aussi à la cible unique enregistrée
                        val t = Store.targets(this, mode)
                        t.firstOrNull()?.apply {
                            delay = cfg.defDelay; delayUnit = cfg.defDelayUnit
                            duration = cfg.defDuration; durationUnit = cfg.defDurationUnit
                        }
                        Store.saveTargets(this, mode, t)
                    }
                    AutoClickService.instance?.takeIf { it.activeMode == mode }?.reloadCfg()
                    d.dismiss()
                }
            }
        }
        d.show()
    }

    private fun instructions() {
        AlertDialog.Builder(this)
            .setTitle("Instructions")
            .setMessage(
                "PANNEAU FLOTTANT\n" +
                "▶ / ■  Démarrer / arrêter\n" +
                "＋  Ajouter une cible de clic\n" +
                "↗  Ajouter un glissement (cercle numéroté → point bleu)\n" +
                "－  Supprimer la dernière cible\n" +
                "💾  Enregistrer la configuration\n" +
                "⚙  Paramètres (délais par défaut, arrêt automatique)\n" +
                "✥  Déplacer le panneau (glisser)\n" +
                "✕  Fermer le mode\n\n" +
                "CIBLES\n" +
                "• Glissez une cible pour la placer.\n" +
                "• Touchez une cible pour la personnaliser : délai avant l'action suivante, " +
                "durée de l'appui, nombre de répétitions.\n" +
                "• Les cibles s'exécutent dans l'ordre 1, 2, 3… puis le cycle recommence.\n\n" +
                "DÉLAIS AVEC DÉCIMALES\n" +
                "Tapez la valeur avec une virgule ou un point, puis touchez le bouton d'unité " +
                "pour choisir ms → s → min.\n" +
                "Exemples : 0,5 s · 1,25 min · 8 ms · 300 min · 2.75 s"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun manageConfigs() {
        val names = Store.configNames(this)
        val b = AlertDialog.Builder(this).setTitle("Configurations enregistrées")
        if (names.isEmpty()) {
            b.setMessage("Aucune configuration.\n\nEn mode cibles multiples, placez vos cibles puis touchez 💾 dans le panneau flottant.")
        } else {
            b.setItems(names.toTypedArray()) { _, i -> configActions(names[i]) }
        }
        b.setNegativeButton("Fermer", null).show()
    }

    private fun configActions(name: String) {
        val count = Store.loadConfig(this, name).size
        AlertDialog.Builder(this)
            .setTitle("« $name » ($count cible${if (count > 1) "s" else ""})")
            .setItems(arrayOf("Charger", "Renommer", "Supprimer")) { _, which ->
                when (which) {
                    0 -> loadConfig(name)
                    1 -> renameConfig(name)
                    2 -> AlertDialog.Builder(this)
                        .setMessage("Supprimer « $name » ?")
                        .setPositiveButton("Supprimer") { _, _ -> Store.deleteConfig(this, name); toast("Supprimée") }
                        .setNegativeButton("Annuler", null).show()
                }
            }
            .setNegativeButton("Retour") { _, _ -> manageConfigs() }
            .show()
    }

    private fun loadConfig(name: String) {
        Store.saveTargets(this, AutoClickService.MODE_MULTI, Store.loadConfig(this, name))
        val svc = AutoClickService.instance
        if (svc?.activeMode == AutoClickService.MODE_MULTI) {
            svc.startMode(AutoClickService.MODE_MULTI)
            toast("« $name » chargée")
        } else {
            toast("« $name » chargée – activez le mode cibles multiples")
        }
        refresh()
    }

    private fun renameConfig(name: String) {
        val edit = EditText(this).apply { setText(name); setSingleLine(); setSelectAllOnFocus(true) }
        AlertDialog.Builder(this)
            .setTitle("Renommer")
            .setView(LinearLayout(this).apply { setPadding(dp(22), dp(8), dp(22), 0); addView(edit, LinearLayout.LayoutParams(-1, -2)) })
            .setPositiveButton("OK") { _, _ ->
                val n = edit.text.toString().trim()
                if (n.isNotEmpty() && n != name) {
                    Store.saveConfig(this, n, Store.loadConfig(this, name))
                    Store.deleteConfig(this, name)
                }
            }
            .setNegativeButton("Annuler", null).show()
    }

    private fun generalSettings() {
        AlertDialog.Builder(this)
            .setTitle("Paramètres généraux")
            .setItems(arrayOf("Réglages d'accessibilité", "Infos de l'application", "Désactiver tous les modes")) { _, w ->
                when (w) {
                    0 -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    1 -> openAppInfo()
                    2 -> { AutoClickService.instance?.closeMode(); refresh() }
                }
            }
            .setNegativeButton("Fermer", null).show()
    }

    private fun troubleshooting() {
        AlertDialog.Builder(this)
            .setTitle("Dépannage")
            .setMessage(
                "• Le panneau n'apparaît pas : vérifiez que le service « Auto Clicker » est activé dans Accessibilité.\n\n" +
                "• Option grisée (Android 13+) : Infos de l'appli → ⋮ → Autoriser les paramètres restreints.\n\n" +
                "• Le service se désactive tout seul : désactivez l'optimisation de batterie pour Auto Clicker, " +
                "et sur Samsung retirez-la des « applis en veille ».\n\n" +
                "• Les clics ne fonctionnent pas : désactivez puis réactivez le service. Certaines applis " +
                "(banque, jeux protégés) bloquent les gestes automatiques.\n\n" +
                "• Durée d'un appui ou glissement : 60 s maximum (limite d'Android). Les délais entre actions, eux, n'ont pas de limite."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun openAppInfo() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    // ------------------------------------------------------------------ construction de l'interface

    private fun card(parent: LinearLayout, title: String): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(10).toFloat() }
            elevation = dp(3).toFloat()
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        c.addView(TextView(this).apply {
            text = title; textSize = 18f
            setTextColor(Color.BLACK)
            setTypeface(typeface, Typeface.NORMAL)
        })
        parent.addView(c, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(4), dp(10), dp(4), 0) })
        return c
    }

    private fun row(parent: LinearLayout, icon: String, label: String, onClick: () -> Unit) {
        val tv = TextView(this).apply {
            text = "$icon   $label"
            textSize = 17f
            setTextColor(Color.parseColor("#8A8A8A"))
            setPadding(dp(4), dp(14), dp(4), dp(14))
            val tvv = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tvv, true)
            setBackgroundResource(tvv.resourceId)
            setOnClickListener { onClick() }
        }
        parent.addView(tv)
    }

    private fun toggle(parent: LinearLayout, onClick: () -> Unit): Button {
        val b = Button(this).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            stateListAnimator = null
            setOnClickListener { onClick() }
        }
        parent.addView(b, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(10) })
        return b
    }

    private fun setToggle(b: Button, on: Boolean) {
        b.text = if (on) "ACTIVÉ" else "DÉSACTIVÉ"
        b.background = GradientDrawable().apply {
            setColor(Color.parseColor(if (on) "#EC9FBD" else "#A0A0A0"))
            cornerRadius = dp(6).toFloat()
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
