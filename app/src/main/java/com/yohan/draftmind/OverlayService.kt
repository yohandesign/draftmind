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

    private lateinit var wm: WindowManager
    private lateinit var heroes: List<Hero>
    private lateinit var engine: DraftEngine


    private lateinit var bubble: TextView
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var panel: DraftPanel? = null
    private lateinit var panelParams: WindowManager.LayoutParams


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
        panel?.let { p -> runCatching { wm.removeView(p.root) } }
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

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

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
        if (panel == null) buildPanel()
        val m = resources.displayMetrics
        panelParams.width = minOf(m.widthPixels - 16.dp, 640.dp)
        panelParams.height = minOf(m.heightPixels - 16.dp, 520.dp)
        wm.addView(panel!!.root, panelParams)
        panel!!.refresh()
    }

    private fun closePanel() {
        setFocusable(false)
        panel?.let { p -> runCatching { wm.removeView(p.root) } }
        runCatching { wm.addView(bubble, bubbleParams) }
    }

    private fun setFocusable(focus: Boolean) {
        val p = panel ?: return
        val f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        panelParams.flags = if (focus) panelParams.flags and f.inv() else panelParams.flags or f
        runCatching { wm.updateViewLayout(p.root, panelParams) }
        if (!focus) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(p.root.windowToken, 0)
            p.search.clearFocus()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildPanel() {
        panelParams = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 8.dp; y = 8.dp
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        }
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        val drag = View.OnTouchListener { _, e ->
            val p = panel ?: return@OnTouchListener false
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = panelParams.x; startY = panelParams.y }
                MotionEvent.ACTION_MOVE -> {
                    panelParams.x = startX + (e.rawX - downX).toInt()
                    panelParams.y = startY + (e.rawY - downY).toInt()
                    wm.updateViewLayout(p.root, panelParams)
                }
            }
            true
        }
        panel = DraftPanel(this, heroes, engine, stacked = false,
            onHide = { closePanel() }, onHeaderTouch = drag, onSearchFocus = { setFocusable(it) })
    }

    companion object {
        const val CHANNEL = "draftmind"
        const val NOTIF_ID = 7
        const val ACTION_STOP = "com.yohan.draftmind.STOP"
    }
}
