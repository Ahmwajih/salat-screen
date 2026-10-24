package com.ahmwajih.prayerdream

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager

class PreviewActivity : Activity() {
    private var view: DisplayView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = DisplayView(this)
        setContentView(view)
    }

    override fun onStart() {
        super.onStart()
        view?.start()
    }

    override fun onStop() {
        view?.stop()
        super.onStop()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        finish()
        return true
    }
}
