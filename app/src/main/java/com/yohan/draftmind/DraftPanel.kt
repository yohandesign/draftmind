package com.yohan.draftmind

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * The draft UI: mode buttons, picks, suggestions and the hero picker.
 * Used both by the floating overlay (landscape, side-by-side) and by the
 * full-screen second-screen activity (portrait, stacked).
 */
class DraftPanel(
    private val ctx: Context,
    private val heroes: List<Hero>,
    private val engine: DraftEngine,
    private val stacked: Boolean,
    private val textScale: Float = 1f,
    private val onHide: (() -> Unit)? = null,
    private val onHeaderTouch: View.OnTouchListener? = null,
    private val onSearchFocus: ((Boolean) -> Unit)? = null
) {
    enum class Mode(val label: String, val color: Int, val max: Int) {
        ENEMY("Enemy", Color.parseColor("#E5484D"), 5),
        ALLY("My team", Color.parseColor("#3E8EF7"), 5),
        BAN("Ban", Color.parseColor("#8B8D98"), 10)
    }

    private val enemies = mutableListOf<Hero>()
    private val allies = mutableListOf<Hero>()
    private val bans = mutableListOf<Hero>()
    private var mode = Mode.ENEMY
    private var myLane: String? = null
    private var laneFilter: String? = null
    private var query = ""

    private lateinit var enemyRow: LinearLayout
    private lateinit var allyRow: LinearLayout
    private lateinit var banRow: LinearLayout
    private lateinit var suggestBox: LinearLayout
    private lateinit var grid: GridLayout
    lateinit var search: EditText
        private set
    private lateinit var laneBtn: TextView
    private val modeButtons = mutableMapOf<Mode, TextView>()
    private val filterButtons = mutableMapOf<String?, TextView>()

    private val bg = Color.parseColor("#F2151821")
    private val card = Color.parseColor("#262A36")
    private val textMain = Color.parseColor("#F1F2F6")
    private val textDim = Color.parseColor("#9BA1B0")
    private val accent = Color.parseColor("#F5B83D")

    val root: LinearLayout = build()

    // ---------------------------------------------------------------- helpers

    private val Int.dp: Int get() = (this * ctx.resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radiusDp: Int = 10, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp.dp.toFloat()
        if (stroke != null) setStroke(1.dp, stroke)
    }

    private fun label(text: String, size: Float = 12f, color: Int = textMain, bold: Boolean = false) =
        TextView(ctx).apply {
            this.text = text
            textSize = size * textScale
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun pill(text: String, color: Int, onClick: () -> Unit) = label(text, 12f).apply {
        background = rounded(color, 14)
        val v = (5 * textScale).toInt()
        setPadding(10.dp, v.dp, 10.dp, v.dp)
        gravity = Gravity.CENTER
        setOnClickListener { onClick() }
    }

    private fun lp(w: Int = ViewGroup.LayoutParams.WRAP_CONTENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
                   weight: Float = 0f, end: Int = 0, top: Int = 0) =
        LinearLayout.LayoutParams(w, h, weight).apply { marginEnd = end.dp; topMargin = top.dp }

    // ---------------------------------------------------------------- layout

    @SuppressLint("ClickableViewAccessibility")
    private fun build(): LinearLayout {
        val root = LinearLayout(ctx).apply {
            orientation = if (stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            background = if (stacked) null else rounded(bg, 14, Color.parseColor("#3A3F4E"))
            if (stacked) setBackgroundColor(Color.parseColor("#151821"))
            setPadding(10.dp, 8.dp, 10.dp, 8.dp)
        }

        // ---------- picks + suggestions
        val left = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(label("DraftMind", 15f, accent, bold = true), lp(0, weight = 1f))
        header.addView(pill("Reset", card) { enemies.clear(); allies.clear(); bans.clear(); refresh() }, lp(end = 6))
        if (onHide != null) header.addView(pill("Hide", card) { onHide.invoke() })
        if (onHeaderTouch != null) header.setOnTouchListener(onHeaderTouch)
        left.addView(header, lp(ViewGroup.LayoutParams.MATCH_PARENT))

        left.addView(label("Pick a mode, then tap heroes", 10f, textDim), lp(top = 2))

        val modes = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        Mode.values().forEach { md ->
            val b = pill(md.label, card) { mode = md; refresh() }
            modeButtons[md] = b
            modes.addView(b, lp(end = 6))
        }
        modes.addView(View(ctx), lp(0, weight = 1f))
        laneBtn = pill("My lane: Any", card) {
            val opts = listOf<String?>(null) + HeroRepo.LANES
            myLane = opts[(opts.indexOf(myLane) + 1) % opts.size]
            refresh()
        }
        if (stacked) modes.addView(laneBtn)
        left.addView(modes, lp(ViewGroup.LayoutParams.MATCH_PARENT, top = 6))
        if (!stacked) left.addView(laneBtn, lp(top = 6))

        enemyRow = chipRow(); allyRow = chipRow(); banRow = chipRow()
        left.addView(sectionRow("Enemy", Mode.ENEMY.color, enemyRow), lp(ViewGroup.LayoutParams.MATCH_PARENT, top = 8))
        left.addView(sectionRow("Team", Mode.ALLY.color, allyRow), lp(ViewGroup.LayoutParams.MATCH_PARENT, top = 4))
        left.addView(sectionRow("Bans", Mode.BAN.color, banRow), lp(ViewGroup.LayoutParams.MATCH_PARENT, top = 4))

        suggestBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val sScroll = ScrollView(ctx).apply { addView(suggestBox) }
        left.addView(sScroll, lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, weight = 1f, top = 6))

        // ---------- hero picker
        val right = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        search = EditText(ctx).apply {
            hint = "Search hero"
            setHintTextColor(textDim)
            setTextColor(textMain)
            textSize = 13f * textScale
            isSingleLine = true
            background = rounded(card, 10)
            setPadding(10.dp, 6.dp, 10.dp, 6.dp)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { query = s?.toString().orEmpty(); buildGrid() }
            })
            setOnTouchListener { _, e -> if (e.action == MotionEvent.ACTION_DOWN) onSearchFocus?.invoke(true); false }
        }
        right.addView(search, lp(ViewGroup.LayoutParams.MATCH_PARENT))

        val filters = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        (listOf<String?>(null) + HeroRepo.LANES).forEach { ln ->
            val b = pill(ln ?: "All", card) { laneFilter = ln; refresh() }
            filterButtons[ln] = b
            filters.addView(b, lp(end = 4))
        }
        right.addView(HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; addView(filters) },
            lp(ViewGroup.LayoutParams.MATCH_PARENT, top = 6))

        grid = GridLayout(ctx).apply { columnCount = 4 }
        right.addView(ScrollView(ctx).apply { addView(grid) },
            lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, weight = 1f, top = 6))

        if (stacked) {
            root.addView(left, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            root.addView(right, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.1f).apply { topMargin = 8.dp })
        } else {
            root.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = 10.dp })
            root.addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.15f))
        }
        return root
    }

    private fun chipRow() = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }

    private fun sectionRow(title: String, color: Int, row: LinearLayout): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label(title, 11f, color, bold = true), LinearLayout.LayoutParams((44 * textScale).toInt().dp, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(HorizontalScrollView(ctx).apply {
                isHorizontalScrollBarEnabled = false
                addView(row)
            })
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
        onSearchFocus?.invoke(false)
        refresh()
    }

    fun refresh() {
        modeButtons.forEach { (m, b) ->
            b.background = rounded(if (m == mode) m.color else card, 14)
            b.text = "${m.label} ${listFor(m).size}/${m.max}"
        }
        filterButtons.forEach { (ln, b) ->
            b.background = rounded(if (ln == laneFilter) accent else card, 14)
            b.setTextColor(if (ln == laneFilter) Color.BLACK else textMain)
        }
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
            row.addView(pill("${h.name} ✕", color) { list.remove(h); refresh() }.apply { textSize = 11f * textScale },
                lp(end = 4))
        }
    }

    private fun buildSuggestions() {
        suggestBox.removeAllViews()
        if (mode == Mode.BAN) {
            suggestBox.addView(label("Suggested bans", 12f, Mode.BAN.color, bold = true))
            engine.suggestBans(allies, enemies, bans).forEachIndexed { i, s ->
                suggestBox.addView(suggestionCard(i, s), lp(ViewGroup.LayoutParams.MATCH_PARENT, top = 4))
            }
            suggestBox.addView(label("Switch to Enemy / My team to see pick suggestions.", 10f, textDim), lp(top = 6))
            return
        }
        suggestBox.addView(label("Suggested picks", 12f, accent, bold = true))
        val sugg = engine.suggest(allies, enemies, bans, myLane)
        val enemyLeft = 5 - enemies.size
        if (enemies.isEmpty() && allies.isEmpty()) {
            suggestBox.addView(label("Strongest heroes right now — good picks or bans:", 10.5f, textDim))
        } else {
            suggestBox.addView(label(
                if (enemyLeft > 0) "Enemy has $enemyLeft pick(s) left — favouring safe picks" else "Enemy is done — pure counter-pick",
                10f, textDim))
            if (sugg.isEmpty()) suggestBox.addView(label("No strong pick for this lane — change My lane.", 11f, textDim))
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
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(card, 10)
            setPadding(8.dp, 5.dp, 8.dp, 5.dp)
        }
        val title = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
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
                    val v = (6 * textScale).toInt()
                    setPadding(2.dp, v.dp, 2.dp, v.dp)
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
}
