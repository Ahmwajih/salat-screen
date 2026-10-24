package com.ahmwajih.prayerdream

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private var display: DisplayView? = null

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val accent = Prefs.load(this).accentColor
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.BLACK)
        }
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(28), dp(24), dp(12), dp(24))
            setBackgroundColor(0xFF0B0F14.toInt())
        }
        root.addView(menu, LinearLayout.LayoutParams(dp(300), -1))

        fun add(text: String, onClick: () -> Unit): View {
            val b = Ui.menuButton(this, text, accent, onClick)
            menu.addView(b, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            return b
        }
        val first = add("Quran") { startActivity(Intent(this, QuranActivity::class.java)) }
        add("Start screensaver now") { startActivity(Intent(this, PreviewActivity::class.java)) }
        add("Use as system screensaver") { enableSystemScreensaver() }
        add("Test adhan") {
            if (!Settings.canDrawOverlays(this)) {
                AlertDialog.Builder(this)
                    .setTitle("Show adhan on screen")
                    .setMessage(
                        "To show the prayer screen over other apps, run once on the laptop:\n\n" +
                            "adb shell appops set $packageName SYSTEM_ALERT_WINDOW allow",
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }
            Adhan.startService(this, Intent(this, AdhanService::class.java).setAction(Adhan.ACTION_PLAY).putExtra("prayer", "test"))
        }
        add("Stop adhan / Quran") {
            startService(Intent(this, AdhanService::class.java).setAction(Adhan.ACTION_STOP))
            PlaybackService.command(this, PlaybackService.ACTION_STOP)
        }
        add("Settings") { startActivity(Intent(this, SettingsActivity::class.java)) }
        first.requestFocus()

        val view = DisplayView(this, showMatchColumn = false)
        display = view
        root.addView(view, LinearLayout.LayoutParams(0, -1, 1f))
        setContentView(root)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        thread { Adhan.schedule(this) }
    }

    private fun enableSystemScreensaver() {
        val component = "$packageName/${PrayerDreamService::class.java.name}"
        try {
            Settings.Secure.putString(contentResolver, "screensaver_components", component)
            Settings.Secure.putInt(contentResolver, "screensaver_enabled", 1)
            Toast.makeText(this, "Screensaver enabled", Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            AlertDialog.Builder(this)
                .setTitle("One-time setup")
                .setMessage(
                    "Android needs a one-time permission. On the laptop run:\n\n" +
                        "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS\n\n" +
                        "then press this button again.",
                )
                .setPositiveButton("OK", null)
                .show()
        }
    }

    override fun onStart() {
        super.onStart()
        display?.start()
    }

    override fun onStop() {
        display?.stop()
        super.onStop()
    }
}
