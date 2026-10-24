package com.ahmwajih.prayerdream

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.graphics.PixelFormat
import android.view.WindowManager
import kotlin.concurrent.thread

class AdhanService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var overlay: AdhanScreen? = null
    private var wake: PowerManager.WakeLock? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener { }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == Adhan.ACTION_STOP) {
            finish(reschedule = false)
            return START_NOT_STICKY
        }
        val prayer = intent?.getStringExtra("prayer").orEmpty()
        Adhan.goForeground(this, 11, Adhan.notification(this, "Adhan", if (prayer.isEmpty()) "Playing adhan" else "Time for $prayer"))
        stopPlayer()
        wakeScreen()
        showOverlay(prayer)
        thread {
            val cfg = Prefs.load(this)
            val file = Adhan.ensureCached(this, cfg.adhanUrl)
            handler.post { start(cfg, file?.absolutePath ?: cfg.adhanUrl) }
        }
        return START_NOT_STICKY
    }

    private fun start(cfg: Prefs.Cfg, source: String) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        @Suppress("DEPRECATION")
        am.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        val v = cfg.adhanVolume / 100f
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setDataSource(source)
                setVolume(v, v)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { finish(reschedule = true) }
                setOnErrorListener { _, _, _ -> finish(reschedule = true); true }
                prepareAsync()
            }
        } catch (e: Exception) {
            finish(reschedule = true)
        }
    }

    private fun stopPlayer() {
        try { player?.release() } catch (e: Exception) { }
        player = null
    }

    private fun wakeScreen() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wl = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "prayerdream:adhan",
        )
        wl.acquire(5 * 60_000L)
        wake = wl
    }

    // Needs the "draw over other apps" permission; silently skipped when it is not granted.
    private fun showOverlay(prayer: String) {
        if (overlay != null || !Settings.canDrawOverlays(this)) return
        try {
            val view = AdhanScreen(this, prayer)
            val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            @Suppress("DEPRECATION")
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                PixelFormat.TRANSLUCENT,
            )
            view.onClose = { removeOverlay() }
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(view, lp)
            view.start()
            view.requestFocus()
            overlay = view
        } catch (e: Exception) { }
    }

    private fun removeOverlay() {
        val v = overlay ?: return
        overlay = null
        v.stop()
        try { (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) } catch (e: Exception) { }
    }

    private fun finish(reschedule: Boolean) {
        stopPlayer()
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        @Suppress("DEPRECATION")
        am.abandonAudioFocus(focusListener)
        if (reschedule) {
            thread {
                Adhan.schedule(this)
                handler.postDelayed({ stopSelf() }, if (overlay != null) 6000L else 0L)
            }
        } else {
            stopSelf()
        }
    }

    override fun onDestroy() {
        stopPlayer()
        removeOverlay()
        try { if (wake?.isHeld == true) wake?.release() } catch (e: Exception) { }
        super.onDestroy()
    }
}
