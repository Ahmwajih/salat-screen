package com.ahmwajih.prayerdream

import android.service.dreams.DreamService

class PrayerDreamService : DreamService() {
    private var view: DisplayView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = Prefs.load(this).bright
        view = DisplayView(this)
        setContentView(view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        view?.start()
    }

    override fun onDreamingStopped() {
        view?.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        view?.stop()
        super.onDetachedFromWindow()
    }
}
