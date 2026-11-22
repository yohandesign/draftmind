package com.yohan.draftmind

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp, 32.dp, 24.dp, 24.dp)
            setBackgroundColor(Color.parseColor("#151821"))
        }
        col.addView(TextView(this).apply {
            text = "DraftMind"
            textSize = 28f
            setTextColor(Color.parseColor("#F5B83D"))
            setTypeface(typeface, Typeface.BOLD)
        })
        col.addView(TextView(this).apply {
            text = "Hero-pick helper for Moba Legends: 5v5 / Mobile Legends. A floating DM bubble sits over the game; " +
                "during the draft, tap it, add enemy picks, your team's picks and bans, and it suggests " +
                "the best counter picks for your team."
            textSize = 14f
            setTextColor(Color.parseColor("#C9CDD8"))
            setPadding(0, 8.dp, 0, 16.dp)
        })
        status = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 12.dp)
        }
        col.addView(status)

        col.addView(button("1. Allow \"Display over other apps\"") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        col.addView(button("2. Start floating bubble") { startOverlay() })
        col.addView(button("3. Open Moba Legends / Mobile Legends") { openGame() })
        col.addView(button("Stop bubble") {
            startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_STOP))
        })
        col.addView(TextView(this).apply {
            text = "Tips\n• Pick a mode (Enemy / My team / Ban), then tap heroes.\n" +
                "• Set \"My lane\" to only see picks for your role.\n" +
                "• Tap a picked hero chip to remove it. Drag the title bar to move the panel.\n" +
                "• Counter data is a starter set and will be improved over time."
            textSize = 13f
            setTextColor(Color.parseColor("#9BA1B0"))
            setPadding(0, 20.dp, 0, 0)
        })
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.parseColor("#151821")); addView(col) })

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        status.text = if (Settings.canDrawOverlays(this)) "✅ Overlay permission granted" else "⚠️ Overlay permission needed (step 1)"
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow \"Display over other apps\" first", Toast.LENGTH_LONG).show()
            return
        }
        startForegroundService(Intent(this, OverlayService::class.java))
        Toast.makeText(this, "Bubble started — now open the game", Toast.LENGTH_SHORT).show()
    }

    /** Finds Moba Legends: 5v5 (India) or Mobile Legends: Bang Bang, whatever is installed. */
    private fun openGame() {
        packageManager.getLaunchIntentForPackage("com.mobile.legends")?.let { startActivity(it); return }
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val match = packageManager.queryIntentActivities(launcher, 0).firstOrNull { ri ->
            val label = ri.loadLabel(packageManager).toString().lowercase()
            ("legends" in label && ("moba" in label || "mobile" in label)) || ri.activityInfo.packageName.contains("legends")
        }
        if (match != null) {
            packageManager.getLaunchIntentForPackage(match.activityInfo.packageName)?.let { startActivity(it); return }
        }
        Toast.makeText(this, "Game not found - just open it yourself, the bubble works over any app", Toast.LENGTH_LONG).show()
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 15f
        setTextColor(Color.BLACK)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply { setColor(Color.parseColor("#F5B83D")); cornerRadius = 12.dp.toFloat() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 52.dp).apply { topMargin = 10.dp }
        setOnClickListener { onClick() }
    }
}
