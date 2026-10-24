package com.ahmwajih.prayerdream

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Full-screen "time for prayer" card shown over everything while the adhan plays.
class AdhanScreen(private val ctx: Context, private val prayer: String) : FrameLayout(ctx) {
    var onClose: (() -> Unit)? = null
    private val bg = BackgroundView(ctx)

    private val arabic = mapOf(
        "Fajr" to "الفجر",
        "Dhuhr" to "الظهر",
        "Asr" to "العصر",
        "Maghrib" to "المغرب",
        "Isha" to "العشاء",
    )

    private fun tv(size: Float, color: Int, bold: Boolean = false) = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(color)
        gravity = Gravity.CENTER
        setShadowLayer(10f, 0f, 2f, 0xAA000000.toInt())
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    init {
        setBackgroundColor(Color.BLACK)
        addView(bg, LayoutParams(-1, -1))

        val cfg = Prefs.load(ctx)
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(Ui.dp(ctx, 64), Ui.dp(ctx, 40), Ui.dp(ctx, 64), Ui.dp(ctx, 40))
            background = Ui.glass(ctx, 0x88000000.toInt(), 0x44FFFFFF, 28)
        }
        val name = arabic[prayer]
        card.addView(tv(64f, Color.WHITE, true).apply { text = if (name != null) "حان وقت صلاة $name" else "الأذان" })
        card.addView(
            tv(34f, Color.WHITE).apply { text = if (name != null) "It's time for $prayer" else "Adhan" },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = Ui.dp(ctx, 12) },
        )
        val time = SimpleDateFormat(if (cfg.h24) "HH:mm" else "h:mm a", Locale.getDefault()).format(Date())
        card.addView(
            tv(28f, cfg.accentColor, true).apply { text = "$time  \u00B7  ${cfg.city}" },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = Ui.dp(ctx, 16) },
        )
        card.addView(
            tv(14f, 0xBBFFFFFF.toInt()).apply { text = "Press any button to close" },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = Ui.dp(ctx, 24) },
        )
        addView(card, LayoutParams(-2, -2, Gravity.CENTER))

        isFocusable = true
        isFocusableInTouchMode = true
        setOnKeyListener { _, _, e ->
            if (e.action == KeyEvent.ACTION_UP) onClose?.invoke()
            true
        }
    }

    fun start() = bg.start()
    fun stop() = bg.stop()
}
