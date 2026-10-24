package com.ahmwajih.prayerdream

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import java.util.Calendar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BackgroundView(private val ctx: Context) : FrameLayout(ctx) {
    private val handler = Handler(Looper.getMainLooper())
    private var exec: ExecutorService? = null
    private val a = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f }
    private val b = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f }
    private val credit = TextView(ctx).apply {
        setTextColor(0x99FFFFFF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        setPadding(Ui.dp(ctx, 16), 0, Ui.dp(ctx, 16), Ui.dp(ctx, 6))
    }
    private var showingA = false
    private var photos: List<Backgrounds.Photo> = emptyList()
    private var index = 0
    private var running = false
    private var intervalMs = 60_000L
    private var mode = "both"

    init {
        val sky = View(ctx)
        sky.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, skyColors())
        addView(sky, LayoutParams(-1, -1))
        addView(a, LayoutParams(-1, -1))
        addView(b, LayoutParams(-1, -1))
        // Darkening keeps white text readable on bright photos.
        val scrim = View(ctx)
        scrim.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xAA000000.toInt(), 0x55000000, 0x66000000, 0xCC000000.toInt()),
        )
        addView(scrim, LayoutParams(-1, -1))
        addView(credit, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END))
    }

    // Time-of-day gradient shown while photos load or when offline.
    private fun skyColors(): IntArray = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 4..6 -> intArrayOf(0xFF0B1B3A.toInt(), 0xFF5B3A6E.toInt(), 0xFFF29E6B.toInt())
        in 7..15 -> intArrayOf(0xFF1E6FB8.toInt(), 0xFF5BA4E0.toInt(), 0xFFBFE3F5.toInt())
        in 16..18 -> intArrayOf(0xFF1F3A66.toInt(), 0xFFD98A3D.toInt(), 0xFFF4C26B.toInt())
        in 19..20 -> intArrayOf(0xFF140E33.toInt(), 0xFF5A2A6B.toInt(), 0xFFE0705A.toInt())
        else -> intArrayOf(0xFF02030A.toInt(), 0xFF0A1330.toInt(), 0xFF14284F.toInt())
    }

    fun start() {
        if (running) return
        running = true
        val cfg = Prefs.load(ctx)
        mode = cfg.bgMode
        intervalMs = cfg.bgSeconds * 1000L
        if (mode == "gradient") return
        exec = Executors.newSingleThreadExecutor()
        next()
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        exec?.shutdownNow()
        exec = null
    }

    private fun next() {
        val ex = exec ?: return
        ex.execute {
            try {
                if (photos.isEmpty()) photos = Backgrounds.list(ctx, mode).shuffled()
                if (photos.isEmpty()) {
                    handler.post { if (running) handler.postDelayed({ next() }, 60_000L) }
                    return@execute
                }
                val photo = photos[index % photos.size]
                index++
                val bmp = Backgrounds.bitmap(ctx, photo)
                handler.post {
                    if (!running) return@post
                    if (bmp != null) show(bmp, photo)
                    handler.postDelayed({ next() }, if (bmp != null) intervalMs else 5_000L)
                }
            } catch (e: Exception) {
                handler.post { if (running) handler.postDelayed({ next() }, 30_000L) }
            }
        }
    }

    private fun show(bmp: android.graphics.Bitmap, photo: Backgrounds.Photo) {
        val incoming = if (showingA) b else a
        val outgoing = if (showingA) a else b
        showingA = !showingA
        incoming.setImageBitmap(bmp)
        incoming.scaleX = 1f
        incoming.scaleY = 1f
        incoming.animate().alpha(1f).setDuration(1500).start()
        // Slow zoom for a subtle motion effect.
        android.animation.ObjectAnimator.ofFloat(incoming, View.SCALE_X, 1f, 1.08f).setDuration(intervalMs + 3000).start()
        android.animation.ObjectAnimator.ofFloat(incoming, View.SCALE_Y, 1f, 1.08f).setDuration(intervalMs + 3000).start()
        outgoing.animate().alpha(0f).setDuration(1500).withEndAction { outgoing.setImageDrawable(null) }.start()
        credit.text = "Photo: ${photo.credit}"
    }
}
