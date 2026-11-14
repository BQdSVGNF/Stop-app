package com.autoclicker.multi

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
import android.view.WindowManager.LayoutParams.MATCH_PARENT
import android.view.WindowManager.LayoutParams.WRAP_CONTENT
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.hypot

/**
 * Service d'accessibilité : affiche le panneau flottant + les cibles,
 * et exécute les clics / glissements avec dispatchGesture().
 */
class AutoClickService : AccessibilityService() {

    companion object {
        const val MODE_SINGLE = 0
        const val MODE_MULTI = 1

        var instance: AutoClickService? = null
            private set

        private val BLUE = Color.parseColor("#2F7BFF")
        private val GREEN = Color.parseColor("#2E9E44")
        private val ORANGE = Color.parseColor("#FFA21F")
        private val RED = Color.parseColor("#E5401C")
        private val GRAY = Color.parseColor("#9AA0A6")
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    var activeMode = -1
        private set
    var isRunning = false
        private set

    private var cfg = ModeCfg()
    private val items = mutableListOf<Item>()
    private var panel: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var playBtn: TextView? = null
    private var lineView: LineView? = null
    private var dialogView: View? = null

    // état d'exécution
    private var runToken = 0
    private var stepIndex = 0
    private var repeatLeft = 0
    private var cyclesDone = 0
    private var startAt = 0L

    private val markerFlags = FLAG_NOT_FOCUSABLE or FLAG_LAYOUT_NO_LIMITS or FLAG_LAYOUT_IN_SCREEN
    private val themed by lazy { ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Light_Dialog) }

    // ------------------------------------------------------------------ cycle de vie

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        closeMode(); instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        closeMode(); instance = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ API pour MainActivity

    fun startMode(mode: Int) {
        closeMode()
        activeMode = mode
        cfg = Store.modeCfg(this, mode)

        lineView = LineView().also {
            val p = baseParams(MATCH_PARENT, MATCH_PARENT,
                FLAG_NOT_TOUCHABLE or FLAG_NOT_FOCUSABLE or FLAG_LAYOUT_IN_SCREEN or FLAG_LAYOUT_NO_LIMITS)
            wm.addView(it, p)
        }

        val saved = Store.targets(this, mode)
        if (mode == MODE_SINGLE) {
            addItem(saved.firstOrNull { it.type == TargetCfg.CLICK } ?: newTarget(TargetCfg.CLICK))
        } else {
            saved.forEach { addItem(it) }
        }
        buildPanel()
        if (mode == MODE_MULTI && items.isEmpty()) toast("Appuyez sur ＋ pour ajouter une cible")
    }

    fun closeMode() {
        if (activeMode < 0) return
        stopRun()
        closeDialog()
        saveTargets()
        items.forEach { it.markers().forEach(::safeRemove) }
        items.clear()
        safeRemove(panel); safeRemove(lineView)
        panel = null; lineView = null; playBtn = null; panelParams = null
        activeMode = -1
    }

    /** Recharge les réglages après modification depuis l'application. */
    fun reloadCfg() {
        if (activeMode < 0) return
        cfg = Store.modeCfg(this, activeMode)
        if (activeMode == MODE_SINGLE) applySingleDefaults()
    }

    // ------------------------------------------------------------------ cibles

    private fun newTarget(type: Int): TargetCfg {
        val dm = resources.displayMetrics
        val n = items.size
        val cx = dm.widthPixels / 2 + ((n % 5) - 2) * dp(45)
        val cy = dm.heightPixels / 2 + ((n / 5) % 4 - 1) * dp(70)
        val swipe = type == TargetCfg.SWIPE
        return TargetCfg(
            type = type,
            x = cx, y = if (swipe) cy + dp(130) else cy,
            x2 = cx, y2 = cy - dp(130),
            delay = cfg.defDelay, delayUnit = cfg.defDelayUnit,
            duration = if (swipe) 300.0 else cfg.defDuration,
            durationUnit = if (swipe) Units.MS else cfg.defDurationUnit
        )
    }

    private fun addItem(t: TargetCfg) {
        val item = Item(t)
        items.add(item)
        item.markers().forEach { safeAdd(it, it.params) }
        // garder le panneau au premier plan
        panel?.let { p -> safeRemove(p); safeAdd(p, panelParams!!) }
        renumber()
    }

    private fun removeItem(item: Item?) {
        if (item == null) { toast("Aucune cible"); return }
        item.markers().forEach(::safeRemove)
        items.remove(item)
        renumber()
        saveTargets()
    }

    private fun renumber() {
        items.forEachIndexed { i, it -> it.number = i + 1; it.start.invalidate() }
        lineView?.invalidate()
    }

    private fun saveTargets() {
        if (activeMode < 0) return
        items.forEach { it.syncCfg() }
        Store.saveTargets(this, activeMode, items.map { it.cfg })
    }

    private fun applySingleDefaults() {
        items.firstOrNull()?.cfg?.apply {
            delay = cfg.defDelay; delayUnit = cfg.defDelayUnit
            duration = cfg.defDuration; durationUnit = cfg.defDurationUnit
        }
        saveTargets()
    }

    // ------------------------------------------------------------------ panneau flottant

    @SuppressLint("ClickableViewAccessibility")
    private fun buildPanel() {
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE202124"))
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        fun btn(label: String, color: Int, action: () -> Unit) = TextView(this).apply {
            text = label
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(color)
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { action() }
        }.also { ll.addView(it, LinearLayout.LayoutParams(dp(52), dp(52))) }

        playBtn = btn("▶", BLUE) { if (isRunning) stopRun() else startRun() }
        if (activeMode == MODE_MULTI) {
            btn("＋", GREEN) { guard { addItem(newTarget(TargetCfg.CLICK)); saveTargets() } }
            btn("↗", ORANGE) { guard { addItem(newTarget(TargetCfg.SWIPE)); saveTargets() } }
            btn("－", RED) { guard { removeItem(items.lastOrNull()) } }
            btn("💾", Color.WHITE) { guard { openSaveConfig() } }
        }
        btn("⚙", GRAY) { guard { openSettings() } }
        btn("✥", GRAY) {}.setOnTouchListener(PanelDrag())
        btn("✕", GRAY) { closeMode() }

        val p = baseParams(WRAP_CONTENT, WRAP_CONTENT, markerFlags).apply { x = dp(6); y = dp(160) }
        panel = ll; panelParams = p
        safeAdd(ll, p)
    }

    private inner class PanelDrag : View.OnTouchListener {
        private var rx = 0f; private var ry = 0f; private var sx = 0; private var sy = 0
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val p = panelParams ?: return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { rx = e.rawX; ry = e.rawY; sx = p.x; sy = p.y }
                MotionEvent.ACTION_MOVE -> {
                    p.x = sx + (e.rawX - rx).toInt(); p.y = sy + (e.rawY - ry).toInt()
                    safeUpdate(panel, p)
                }
            }
            return true
        }
    }

    private fun guard(action: () -> Unit) {
        if (isRunning) toast("Appuyez d'abord sur ■ pour arrêter") else action()
    }

    // ------------------------------------------------------------------ exécution

    private fun startRun() {
        if (items.isEmpty()) { toast("Ajoutez au moins une cible avec ＋"); return }
        closeDialog()
        saveTargets()
        isRunning = true
        val token = ++runToken
        setMarkersTouchable(false)
        playBtn?.apply { text = "■"; setTextColor(RED) }
        stepIndex = 0; cyclesDone = 0
        repeatLeft = items[0].cfg.repeat.coerceAtLeast(1)
        startAt = SystemClock.elapsedRealtime()
        handler.postDelayed({ step(token) }, 300)
    }

    fun stopRun() {
        if (!isRunning) return
        isRunning = false
        runToken++
        handler.removeCallbacksAndMessages(null)
        setMarkersTouchable(true)
        playBtn?.apply { text = "▶"; setTextColor(BLUE) }
    }

    private fun timeUp() = cfg.stopMode == ModeCfg.STOP_TIME &&
        SystemClock.elapsedRealtime() - startAt >= Units.toMs(cfg.stopTime, cfg.stopTimeUnit)

    private fun step(token: Int) {
        if (token != runToken || !isRunning) return
        if (timeUp()) { stopRun(); toast("Durée atteinte : arrêt"); return }
        val item = items.getOrNull(stepIndex) ?: run { stopRun(); return }

        perform(item) {
            if (token != runToken) return@perform
            val delay = Units.toMs(item.cfg.delay, item.cfg.delayUnit)
            repeatLeft--
            if (repeatLeft <= 0) {
                stepIndex++
                if (stepIndex >= items.size) {
                    stepIndex = 0
                    cyclesDone++
                    if (cfg.stopMode == ModeCfg.STOP_CYCLES && cyclesDone >= cfg.stopCycles) {
                        stopRun(); toast("$cyclesDone ${if (activeMode == MODE_SINGLE) "clics" else "cycles"} effectués")
                        return@perform
                    }
                }
                repeatLeft = items[stepIndex].cfg.repeat.coerceAtLeast(1)
            }
            handler.postDelayed({ step(token) }, delay)
        }
    }

    private fun perform(item: Item, done: () -> Unit) {
        val a = item.start.screenCenter()
        val path = Path().apply { moveTo(a.x, a.y) }
        item.end?.let { val b = it.screenCenter(); path.lineTo(b.x, b.y) }
        val dur = Units.toMs(item.cfg.duration, item.cfg.durationUnit)
            .coerceIn(1L, GestureDescription.getMaxGestureDuration())

        var finished = false
        val once = { if (!finished) { finished = true; handler.post { done() } } }

        val gesture = try {
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, dur))
                .build()
        } catch (e: Exception) {
            once(); return
        }
        val ok = dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) { once() }
            override fun onCancelled(d: GestureDescription?) { once() }
        }, null)
        if (!ok) handler.postDelayed({ once() }, 50)
    }

    /** Pendant l'exécution, les cibles laissent passer les clics. */
    private fun setMarkersTouchable(touchable: Boolean) {
        items.forEach { item ->
            item.markers().forEach { m ->
                m.params.flags = if (touchable) markerFlags else markerFlags or FLAG_NOT_TOUCHABLE
                m.alpha = if (touchable) 1f else 0.55f
                safeUpdate(m, m.params)
            }
        }
    }

    // ------------------------------------------------------------------ fenêtres de réglage

    private fun openTargetEditor(item: Item) {
        if (isRunning) return
        val ctx = themed
        val c = item.cfg
        val single = activeMode == MODE_SINGLE
        val swipe = c.type == TargetCfg.SWIPE
        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        body.addView(hint(ctx, if (swipe) "Glissement : du cercle numéroté vers le point bleu" else "Clic"))
        val delay = NumberRow(ctx,
            if (single) "Intervalle entre deux clics" else "Délai avant l'action suivante",
            c.delay, c.delayUnit)
        val dur = NumberRow(ctx,
            if (swipe) "Durée du glissement" else "Durée de l'appui (appui long possible)",
            c.duration, c.durationUnit)
        body.addView(delay.view)
        body.addView(dur.view)
        val rep = if (!single) NumberRow(ctx, "Répétitions de cette cible", c.repeat.toDouble(), 0,
            withUnit = false, integer = true).also { body.addView(it.view) } else null
        body.addView(hint(ctx,
            "Décimales acceptées avec virgule ou point : 0,5 s · 1,25 min · 8 ms · 300 min.\n" +
            "Durée d'un geste : 60 s maximum (limite Android)."))

        val buttons = mutableListOf<Pair<String, () -> Boolean>>()
        if (!single) buttons += "Supprimer" to { removeItem(item); true }
        buttons += "Annuler" to { true }
        buttons += "OK" to fun(): Boolean {
            val d = delay.readDouble() ?: return false
            val du = dur.readDouble() ?: return false
            var r = 1
            if (rep != null) r = rep.readInt(1) ?: return false
            c.delay = d; c.delayUnit = delay.unit
            c.duration = du; c.durationUnit = dur.unit
            c.repeat = r
            saveTargets()
            return true
        }
        showDialog(if (single) "Cible unique" else "Cible n°${item.number}", body, buttons)
    }

    private fun openSettings() {
        val single = activeMode == MODE_SINGLE
        val form = SettingsForm(themed, cfg, single)
        showDialog(if (single) "Paramètres – cible unique" else "Paramètres – cibles multiples", form.view, listOf(
            "Annuler" to { true },
            "OK" to {
                if (form.apply()) {
                    Store.saveModeCfg(this, activeMode, cfg)
                    if (single) applySingleDefaults()
                    true
                } else false
            }
        ))
    }

    private fun openSaveConfig() {
        if (items.isEmpty()) { toast("Aucune cible à enregistrer"); return }
        val ctx = themed
        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val name = EditText(ctx).apply { hint = "Nom de la configuration"; setSingleLine() }
        body.addView(name)
        val existing = Store.configNames(this)
        if (existing.isNotEmpty()) body.addView(hint(ctx, "Existantes : " + existing.joinToString(", ")))
        showDialog("Enregistrer la configuration", body, listOf(
            "Annuler" to { true },
            "Enregistrer" to {
                val n = name.text.toString().trim()
                if (n.isEmpty()) { name.error = "Entrez un nom"; false } else {
                    saveTargets()
                    Store.saveConfig(this, n, items.map { it.cfg })
                    toast("« $n » enregistrée")
                    true
                }
            }
        ))
    }

    /** Petite boîte de dialogue flottante (fenêtre d'accessibilité focusable pour le clavier). */
    private fun showDialog(title: String, body: View, buttons: List<Pair<String, () -> Boolean>>) {
        closeDialog()
        val ctx = themed
        val dm = resources.displayMetrics
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(18).toFloat() }
            setPadding(dp(20), dp(16), dp(20), dp(6))
        }
        root.addView(TextView(ctx).apply {
            text = title; textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#00796B"))
        })
        root.addView(MaxHeightScrollView(ctx, (dm.heightPixels * 0.55).toInt()).apply { addView(body) })
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        buttons.forEach { (label, action) ->
            row.addView(Button(ctx, null, android.R.attr.borderlessButtonStyle).apply {
                text = label
                setOnClickListener { if (action()) closeDialog() }
            })
        }
        root.addView(row)

        val p = baseParams(minOf(dm.widthPixels - dp(32), dp(440)), WRAP_CONTENT, FLAG_DIM_BEHIND).apply {
            gravity = Gravity.CENTER
            x = 0; y = 0
            dimAmount = 0.45f
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        }
        dialogView = root
        safeAdd(root, p)
    }

    private fun closeDialog() {
        safeRemove(dialogView); dialogView = null
    }

    // ------------------------------------------------------------------ vues

    inner class Item(val cfg: TargetCfg) {
        var number = 1
        val start = Marker(this, false).apply { place(cfg.x, cfg.y) }
        val end: Marker? = if (cfg.type == TargetCfg.SWIPE) Marker(this, true).apply { place(cfg.x2, cfg.y2) } else null
        fun markers() = listOfNotNull(end, start)   // l'arrivée sous le départ
        fun syncCfg() {
            cfg.x = start.cx(); cfg.y = start.cy()
            end?.let { cfg.x2 = it.cx(); cfg.y2 = it.cy() }
        }
    }

    @SuppressLint("ViewConstructor")
    inner class Marker(private val item: Item, private val isEnd: Boolean) : View(this@AutoClickService) {
        private val size = dp(if (isEnd) 34 else 52)
        val params = baseParams(size, size, markerFlags)
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#AA5EB0FF") }
        private val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF2196F3") }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = dp(3).toFloat(); color = Color.parseColor("#FF1565C0")
        }
        private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFFF6D00"); textAlign = Paint.Align.CENTER
            textSize = dp(22).toFloat(); typeface = Typeface.DEFAULT_BOLD
        }
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private var rx = 0f; private var ry = 0f; private var sx = 0; private var sy = 0
        private var dragging = false

        fun place(cx: Int, cy: Int) { params.x = cx - size / 2; params.y = cy - size / 2 }
        fun cx() = params.x + size / 2
        fun cy() = params.y + size / 2

        /** Position réelle à l'écran (utilisée pour le geste). */
        fun screenCenter(): PointF {
            val l = IntArray(2); getLocationOnScreen(l)
            return PointF(
                (l[0] + width / 2f).coerceAtLeast(0f),
                (l[1] + height / 2f).coerceAtLeast(0f)
            )
        }

        override fun onDraw(c: Canvas) {
            val r = size / 2f
            val rr = r - dp(3)
            if (isEnd) {
                c.drawCircle(r, r, rr, solid); c.drawCircle(r, r, rr, ring)
            } else {
                c.drawCircle(r, r, rr, fill); c.drawCircle(r, r, rr, ring)
                val y = r - (txt.descent() + txt.ascent()) / 2
                c.drawText(item.number.toString(), r, y, txt)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { rx = e.rawX; ry = e.rawY; sx = params.x; sy = params.y; dragging = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - rx; val dy = e.rawY - ry
                    if (!dragging && hypot(dx, dy) > slop) dragging = true
                    if (dragging) {
                        params.x = sx + dx.toInt(); params.y = sy + dy.toInt()
                        safeUpdate(this, params)
                        lineView?.invalidate()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) saveTargets() else { performClick(); openTargetEditor(item) }
                }
            }
            return true
        }

        override fun performClick(): Boolean = super.performClick()
    }

    /** Vue plein écran (non touchable) qui dessine les traits des glissements. */
    inner class LineView : View(this@AutoClickService) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#885EB0FF"); strokeWidth = dp(22).toFloat(); strokeCap = Paint.Cap.ROUND
        }
        override fun onDraw(c: Canvas) {
            for (it in items) {
                val e = it.end ?: continue
                c.drawLine(it.start.cx().toFloat(), it.start.cy().toFloat(), e.cx().toFloat(), e.cy().toFloat(), p)
            }
        }
    }

    // ------------------------------------------------------------------ utilitaires

    private fun baseParams(w: Int, h: Int, flags: Int) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun safeAdd(v: View, p: WindowManager.LayoutParams) { try { wm.addView(v, p) } catch (_: Exception) {} }
    private fun safeRemove(v: View?) { if (v != null) try { wm.removeView(v) } catch (_: Exception) {} }
    private fun safeUpdate(v: View?, p: WindowManager.LayoutParams) { if (v != null) try { wm.updateViewLayout(v, p) } catch (_: Exception) {} }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
