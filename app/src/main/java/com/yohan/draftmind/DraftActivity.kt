package com.yohan.draftmind

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager

/** Full-screen draft helper: use this phone as a second screen next to the device you play on (e.g. an iPhone). */
class DraftActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val heroes = HeroRepo.load(this)
        val panel = DraftPanel(this, heroes, DraftEngine(heroes), stacked = true, textScale = 1.15f)
        setContentView(panel.root)
        panel.refresh()
    }
}
