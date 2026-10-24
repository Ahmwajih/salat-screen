package com.ahmwajih.prayerdream

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.IBinder

class PlaybackService : Service() {
    companion object {
        const val ACTION_PLAY = "quran.PLAY"
        const val ACTION_TOGGLE = "quran.TOGGLE"
        const val ACTION_NEXT = "quran.NEXT"
        const val ACTION_PREV = "quran.PREV"
        const val ACTION_STOP = "quran.STOP"

        @Volatile var state = "idle"
        @Volatile var currentSurah = 0
        @Volatile var reciterName = ""

        fun play(ctx: Context, r: Reciter, index: Int) {
            Adhan.startService(
                ctx,
                Intent(ctx, PlaybackService::class.java).setAction(ACTION_PLAY)
                    .putExtra("server", r.server)
                    .putExtra("surahs", r.surahs.toIntArray())
                    .putExtra("index", index)
                    .putExtra("name", r.name),
            )
        }

        fun command(ctx: Context, action: String) {
            if (state == "idle" && action != ACTION_STOP) return
            ctx.startService(Intent(ctx, PlaybackService::class.java).setAction(action))
        }
    }

    private var player: MediaPlayer? = null
    private var server = ""
    private var surahs = IntArray(0)
    private var pos = 0
    private var resumeOnGain = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        val p = player ?: return@OnAudioFocusChangeListener
        try {
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS -> {
                    if (p.isPlaying) { p.pause(); state = "paused"; resumeOnGain = change != AudioManager.AUDIOFOCUS_LOSS }
                }
                AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnGain) { p.start(); state = "playing"; resumeOnGain = false }
            }
        } catch (e: IllegalStateException) { }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                server = intent.getStringExtra("server").orEmpty()
                surahs = intent.getIntArrayExtra("surahs") ?: IntArray(0)
                reciterName = intent.getStringExtra("name").orEmpty()
                Adhan.goForeground(this, 12, Adhan.notification(this, "Quran", reciterName))
                playAt(intent.getIntExtra("index", 0))
            }
            ACTION_TOGGLE -> try {
                player?.let { if (it.isPlaying) { it.pause(); state = "paused" } else { it.start(); state = "playing" } }
            } catch (e: IllegalStateException) { }
            ACTION_NEXT -> playAt(pos + 1)
            ACTION_PREV -> playAt(pos - 1)
            ACTION_STOP -> stopAll()
        }
        return START_NOT_STICKY
    }

    private fun playAt(i: Int) {
        if (i < 0 || i >= surahs.size) { stopAll(); return }
        pos = i
        currentSurah = surahs[i]
        release()
        state = "loading"
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        @Suppress("DEPRECATION")
        am.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setDataSource("$server${String.format("%03d", currentSurah)}.mp3")
                setOnPreparedListener { it.start(); state = "playing" }
                setOnCompletionListener { playAt(pos + 1) }
                setOnErrorListener { _, _, _ -> state = "error"; true }
                prepareAsync()
            }
        } catch (e: Exception) {
            state = "error"
        }
    }

    private fun release() {
        try { player?.release() } catch (e: Exception) { }
        player = null
    }

    private fun stopAll() {
        release()
        state = "idle"
        currentSurah = 0
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        @Suppress("DEPRECATION")
        am.abandonAudioFocus(focusListener)
        stopSelf()
    }

    override fun onDestroy() {
        release()
        state = "idle"
        super.onDestroy()
    }
}
