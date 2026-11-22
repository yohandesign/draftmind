package com.yohan.draftmind

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs

class OverlayService : Service() {

    enum class Mode(val label: String, val color: Int, val max: Int) {
        ENEMY("Enemy", Color.parseColor("#E5484D"), 5),
        ALLY("My team", Color.parseColor("#3E8EF7"), 5),
        BAN("Ban", Color.parseColor("#8B8D98"), 10)
    }

    private lateinit var wm: WindowManager
    private lateinit var heroes: List<Hero>
    private lateinit var engine: DraftEngine

    private val enemies = mutableListOf<Hero>()
    private val allies = mutableListOf<Hero>()
    private val bans = mutableListOf<Hero>()
    private var mode = Mode.ENEMY
    private var myLane: String? = null
    private var laneFilter: String? = null
    private var query = ""

    private lateinit var bubble: TextView
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var panel: LinearLayout? = null
    private lateinit var panelParams: WindowManager.LayoutParams

    private lateinit var enemyRow: LinearLayout
    private lateinit var allyRow: LinearLayout
    private lateinit var banRow: LinearLayout
    private lateinit var suggestBox: LinearLayout
    private lateinit var grid: GridLayout
    private lateinit var search: EditText
    private lateinit var laneBtn: TextView
    private val modeButtons = mutableMapOf<Mode, TextView>()
    private val filterButtons = mutableMapOf<String?, TextView>()

    private val bg = Color.parseColor("#F2151821")
    private val card = Color.parseColor("#262A36")
    private val textMain = Color.parseColor("#F1F2F6")
    private val textDim = Color.parseColor("#9BA1B0")
    private val accent = Color.parseColor("#F5B83D")

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        heroes = HeroRepo.load(this)
        engine = DraftEngine(heroes)
        goForeground()
        createBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { wm.removeView(bubble) }
        panel?.let { p -> runCatching { wm.removeView(p) } }
        super.onDestroy()
    }

    // ---------------------------------------------------------------- foreground

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "DraftMind overlay", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("DraftMind is running")
            .setContentText("Tap the floating DM bubble during hero pick")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    // ---------------------------------------------------------------- helpers

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radiusDp: Int = 10, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp.dp.toFloat()
        if (stroke != null) setStroke(1.dp, stroke)
    }

    private fun label(text: String, size: Float = 12f, color: Int = textMain, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun pill(text: String, color: Int, onClick: () -> Unit) = label(text, 12f).apply {
        background = rounded(color, 14)
        setPadding(10.dp, 5.dp, 10.dp, 5.dp)
        gravity = Gravity.CENTER
        setOnClickListener { onClick() }
    }

    private fun lp(w: Int = ViewGroup.LayoutParams.WRAP_CONTENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
                   weight: Float = 0f, end: Int = 0, top: Int = 0) =
        LinearLayout.LayoutParams(w, h, weight).apply { marginEnd = end.dp; topMargin = top.dp }

    private fun overlayType() = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    // ---------------------------------------------------------------- bubble

    @SuppressLint("ClickableViewAccessibility")
    private fun createBubble() {
        bubble = label("DM", 15f, Color.BLACK, bold = true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accent)
                setStroke(2.dp, Color.WHITE)
            }
            elevation = 6.dp.toFloat()
        }
        bubbleParams = WindowManager.LayoutParams(
            52.dp, 52.dp, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 12.dp; y = 120.dp
        }
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        bubble.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = bubbleParams.x; startY = bubbleParams.y; moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (abs(dx) > 10 || abs(dy) > 10) moved = true
                    bubbleParams.x = startX + dx.toInt(); bubbleParams.y = startY + dy.toInt()
                    wm.updateViewLayout(bubble, bubbleParams)
                }
                MotionEvent.ACTION_UP -> if (!moved) openPanel()
            }
            true
        }
        wm.addView(bubble, bubbleParams)
    }

    // ---------------------------------------------------------------- panel

    private fun openPanel() {
        runCatching { wm.removeView(bubble) }
        val p = panel ?: buildPanel().also { panel = it }
        val m = resources.displayMetrics
        panelParams.width = minOf(m.widthPixels - 16.dp, 640.dp)
        panelParams.height = minOf(m.heightPixels - 16.dp, 520.dp)
        wm.addView(p, panelParams)
        refresh()
    }

    private fun closePanel() {
        setFocusable(false)
        panel?.let { p -> runCatching { wm.removeView(p) } }
        runCatching { wm.addView(bubble, bubbleParams) }
    }

    private fun setFocusable(focus: Boolean) {
        val p = panel ?: return
        val f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        panelParams.flags = if (focus) panelParams.flags and f.inv() else panelParams.flags or f
        runCatching { wm.updateViewLayout(p, panelParams) }
        if (!focus) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(p.windowToken, 0)
            search.clearFocus()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildPanel(): LinearLayout {
        panelParams = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 8.dp; y = 8.dp
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(bg, 14, Color.parseColor("#3A3F4E"))
            setPadding(10.dp, 8.dp, 10.dp, 8.dp)
        }

        // ---------- left column: picks + suggestions
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(label("DraftMind", 15f, accent, bold = true), lp(0, weight = 1f))
        header.addView(pill("Reset", card) { enemies.clear(); allies.clear(); bans.clear(); refresh() }, lp(end = 6))
        header.addView(pill("Hide", card) { closePanel() })
        makeDraggable(header)
        left.addView(header, lp(ViewGroup.LayoutParams.MATCH_PARENT))

        left.addView(label("Tap a mode, then tap heroes on the right", 10f, textDim), lp(top = 2))

        val modes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Mode.values().forEach { md ->
            val b = pill(md.label, card) { mode = md; refresh() }
            modeButtons[md] = b
            modes.addView(b, lp(end = 6))
        }
        left.addView(modes, lp(top = 6))

        laneBtn = pill("My lane: Any", card) {
            val opts = listOf<String?>(null) + HeroRepo.LANES
            myLane = opts[(opts.indexOf(myLane) + 1) % opts.size]
            refresh()
        }
        left.addView(laneBtn, lp(top = 6))

        enemyRow = chipRow(); allyRow = chipRow(); banRow = chipRow()
        left.addView(sectionRow("Enemy", Mode.ENEMY.color, enemyRow), lp(ViewGroup.LayoutParams.MATCH_PARENT, top = 8))
        left.addView(sectionRow("Team", Mode.ALLY.color, allyRow), lp(ViewGroup.LayoutParams.MATCH_PARENT, top = 4))
        left.addView(sectionRow("Bans", Mode.BAN.color, banRow), lp(ViewGroup.LayoutParams.MATCH_PARENT, top = 4))

        left.addView(label("Suggested picks", 12f, accent, bold = true), lp(top = 8))
        suggestBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sScroll = ScrollView(this).apply { addView(suggestBox) }
        left.addView(sScroll, lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, weight = 1f, top = 2))

        // ---------- right column: hero picker
        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        search = EditText(this).apply {
            hint = "Search hero"
            setHintTextColor(textDim)
            setTextColor(textMain)
            textSize = 13f
            isSingleLine = true
            background = rounded(card, 10)
            setPadding(10.dp, 6.dp, 10.dp, 6.dp)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { query = s?.toString().orEmpty(); buildGrid() }
            })
            setOnTouchListener { _, e -> if (e.action == MotionEvent.ACTION_DOWN) setFocusable(true); false }
        }
        right.addView(search, lp(ViewGroup.LayoutParams.MATCH_PARENT))

        val filters = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        (listOf<String?>(null) + HeroRepo.LANES).forEach { ln ->
            val b = pill(ln ?: "All", card) { laneFilter = ln; refresh() }
            filterButtons[ln] = b
            filters.addView(b, lp(end = 4))
        }
        right.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(filters) },
            lp(ViewGroup.LayoutParams.MATCH_PARENT, top = 6))

        grid = GridLayout(this).apply { columnCount = 4 }
        right.addView(ScrollView(this).apply { addView(grid) },
            lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, weight = 1f, top = 6))

        root.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = 10.dp })
        root.addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.15f))
        return root
    }

    private fun chipRow() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

    private fun sectionRow(title: String, color: Int, row: LinearLayout): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label(title, 11f, color, bold = true), LinearLayout.LayoutParams(44.dp, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(HorizontalScrollView(this@OverlayService).apply {
                isHorizontalScrollBarEnabled = false
                addView(row)
            })
        }

    @SuppressLint("ClickableViewAccessibility")
    private fun makeDraggable(handle: View) {
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        handle.setOnTouchListener { _, e ->
            val p = panel ?: return@setOnTouchListener false
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = panelParams.x; startY = panelParams.y }
                MotionEvent.ACTION_MOVE -> {
                    panelParams.x = startX + (e.rawX - downX).toInt()
                    panelParams.y = startY + (e.rawY - downY).toInt()
                    wm.updateViewLayout(p, panelParams)
                }
            }
            true
        }
    }

    // ---------------------------------------------------------------- state -> UI

    private fun listFor(m: Mode) = when (m) { Mode.ENEMY -> enemies; Mode.ALLY -> allies; Mode.BAN -> bans }

    private fun takenNames() = (enemies + allies + bans).map { it.name }.toSet()

    private fun onHeroTapped(h: Hero) {
        if (h.name in takenNames()) return
        val list = listFor(mode)
        if (list.size >= mode.max) return
        list.add(h)
        if (search.text.isNotEmpty()) search.setText("")
        setFocusable(false)
        refresh()
    }

    private fun refresh() {
        if (panel == null) return
        modeButtons.forEach { (m, b) ->
            b.background = rounded(if (m == mode) m.color else card, 14)
            b.text = "${m.label} ${listFor(m).size}/${m.max}"
        }
        filterButtons.forEach { (ln, b) -> b.background = rounded(if (ln == laneFilter) accent else card, 14)
            b.setTextColor(if (ln == laneFilter) Color.BLACK else textMain) }
        laneBtn.text = "My lane: ${myLane ?: "Any"}"
        laneBtn.background = rounded(if (myLane != null) Color.parseColor("#2F6B4F") else card, 14)

        fillChips(enemyRow, enemies, Mode.ENEMY.color)
        fillChips(allyRow, allies, Mode.ALLY.color)
        fillChips(banRow, bans, Mode.BAN.color)
        buildSuggestions()
        buildGrid()
    }

    private fun fillChips(row: LinearLayout, list: MutableList<Hero>, color: Int) {
        row.removeAllViews()
        if (list.isEmpty()) { row.addView(label("—", 11f, textDim)); return }
        list.toList().forEach { h ->
            row.addView(pill("${h.name} ✕", color) { list.remove(h); refresh() }.apply { textSize = 11f },
                lp(end = 4))
        }
    }

    private fun buildSuggestions() {
        suggestBox.removeAllViews()
        if (mode == Mode.BAN) {
            suggestBox.addView(label("Suggested bans", 12f, Mode.BAN.color, bold = true))
            engine.suggestBans(allies, enemies, bans).forEachIndexed { i, s -> suggestBox.addView(suggestionCard(i, s), lp(ViewGroup.LayoutParams.MATCH_PARENT, top = 4)) }
            suggestBox.addView(label("Switch to Enemy / My team to see pick suggestions.", 10f, textDim), lp(top = 6))
            return
        }
        val sugg = engine.suggest(allies, enemies, bans, myLane)
        val enemyLeft = 5 - enemies.size
        if (allies.isNotEmpty() || enemies.isNotEmpty()) {
            suggestBox.addView(label(
                if (enemyLeft > 0) "Enemy has $enemyLeft pick(s) left — favouring safe picks" else "Enemy is done — pure counter-pick",
                10f, textDim))
        }
        if (enemies.isEmpty() && allies.isEmpty()) {
            suggestBox.addView(label("Strongest heroes right now — good picks or bans:", 10.5f, textDim))
        } else if (sugg.isEmpty()) {
            suggestBox.addView(label("No strong pick yet for this lane — add more picks or change My lane.", 11f, textDim))
        }
        sugg.forEachIndexed { i, s -> suggestBox.addView(suggestionCard(i, s), lp(ViewGroup.LayoutParams.MATCH_PARENT, top = 4)) }
        val avoid = engine.avoid(allies, enemies, bans, myLane)
        if (avoid.isNotEmpty()) {
            suggestBox.addView(label("Avoid", 12f, Mode.ENEMY.color, bold = true), lp(top = 8))
            avoid.forEach { (h, by) ->
                suggestBox.addView(label("${h.name} — countered by ${by.joinToString(", ") { it.name }}", 10.5f, textDim))
            }
        }
    }

    private fun suggestionCard(i: Int, s: Suggestion): View {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(card, 10)
                setPadding(8.dp, 5.dp, 8.dp, 5.dp)
            }
            val title = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            title.addView(label("${i + 1}. ${s.hero.name}", 13f, textMain, bold = true), lp(0, weight = 1f))
            title.addView(label("%.1f".format(s.score), 11f, accent, bold = true))
            box.addView(title, lp(ViewGroup.LayoutParams.MATCH_PARENT))
            box.addView(label(s.hero.lanes.joinToString("/") + " · " + s.reasons.take(3).joinToString(" · "), 10.5f, textDim))
            return box
    }

    private fun buildGrid() {
        if (!::grid.isInitialized) return
        grid.removeAllViews()
        val taken = takenNames()
        val q = query.trim().lowercase()
        heroes.filter { (laneFilter == null || laneFilter in it.lanes) && (q.isEmpty() || it.name.lowercase().contains(q)) }
            .forEach { h ->
                val isTaken = h.name in taken
                val cell = label(h.name, 10.5f, if (isTaken) textDim else textMain).apply {
                    gravity = Gravity.CENTER
                    maxLines = 2
                    setPadding(2.dp, 6.dp, 2.dp, 6.dp)
                    background = rounded(if (isTaken) Color.parseColor("#1C1F28") else laneColor(h.lanes.first()), 8)
                    alpha = if (isTaken) 0.45f else 1f
                    setOnClickListener { onHeroTapped(h) }
                }
                val glp = GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)
                ).apply { width = 0; setMargins(2.dp, 2.dp, 2.dp, 2.dp) }
                grid.addView(cell, glp)
            }
    }

    private fun laneColor(lane: String) = when (lane) {
        "Gold" -> Color.parseColor("#6B5420")
        "Exp" -> Color.parseColor("#5A2E2E")
        "Mid" -> Color.parseColor("#3B2F66")
        "Jungle" -> Color.parseColor("#24503A")
        else -> Color.parseColor("#24445E") // Roam
    }

    companion object {
        const val CHANNEL = "draftmind"
        const val NOTIF_ID = 7
        const val ACTION_STOP = "com.yohan.draftmind.STOP"
    }
}
