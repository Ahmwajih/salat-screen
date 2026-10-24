package com.ahmwajih.prayerdream

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DisplayView(private val ctx: Context, private val showMatchColumn: Boolean = true) : FrameLayout(ctx) {
    private val handler = Handler(Looper.getMainLooper())
    private var exec: ExecutorService? = null
    private var cfg = Prefs.load(ctx)
    private var today: Api.Day? = null
    private var tomorrow: Api.Day? = null
    private var matches: List<Api.Match> = emptyList()
    private var lastRefresh = 0L
    private var refreshing = false
    private var failed = false
    private var running = false
    private var ticks = 0
    private var appliedNext: String? = "?"
    private val rnd = Random()

    private val s = if (showMatchColumn) 1f else 0.7f
    private val bg = BackgroundView(ctx)
    private val content = LinearLayout(ctx)
    private val city = tv(20f, Color.WHITE)
    private val dateTv = tv(20f, Color.WHITE, true)
    private val hijri = tv(16f, 0xCCFFFFFF.toInt())
    private val clock = tv(110f, Color.WHITE)
    private val nextLabel = tv(22f, Color.WHITE, true)
    private val countdown = tv(52f, Color.WHITE, true)
    private val status = tv(16f, 0xFFFF8A80.toInt())
    private val matchCard = LinearLayout(ctx)
    private val matchHeader = tv(16f, Color.WHITE, true)
    private val matchBox = LinearLayout(ctx)
    private val cards = LinkedHashMap<String, Triple<LinearLayout, TextView, TextView>>()

    private fun dp(v: Int) = Ui.dp(ctx, v)
    private fun dps(v: Int) = Ui.dp(ctx, (v * s).toInt())

    private fun tv(size: Float, color: Int, bold: Boolean = false) = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size * s)
        setTextColor(color)
        includeFontPadding = false
        setShadowLayer(8f, 0f, 2f, 0xAA000000.toInt())
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    init {
        setBackgroundColor(Color.BLACK)
        addView(bg, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dps(40), dps(28), dps(40), dps(28))
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val top = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(city, LinearLayout.LayoutParams(0, -2, 1f))
        val dates = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
        dateTv.gravity = Gravity.END
        hijri.gravity = Gravity.END
        dates.addView(dateTv)
        dates.addView(hijri, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
        top.addView(dates)
        content.addView(top, LinearLayout.LayoutParams(-1, -2))

        val mid = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val left = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        clock.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        left.addView(clock)
        nextLabel.setPadding(dps(18), dps(8), dps(18), dps(8))
        left.addView(nextLabel, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dps(10) })
        left.addView(countdown, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dps(10) })
        left.addView(status, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dps(8) })
        mid.addView(left, LinearLayout.LayoutParams(0, -2, 1.3f))

        matchCard.orientation = LinearLayout.VERTICAL
        matchCard.setPadding(dp(22), dp(18), dp(22), dp(18))
        matchCard.background = Ui.glass(ctx, 0x77000000, 0x33FFFFFF, 20)
        matchHeader.text = "UPCOMING MATCHES"
        matchCard.addView(matchHeader)
        matchBox.orientation = LinearLayout.VERTICAL
        matchCard.addView(matchBox)
        mid.addView(matchCard, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(32) })
        content.addView(mid, LinearLayout.LayoutParams(-1, 0, 1f))

        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        for (name in Api.PRAYERS) {
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dps(14), dp(4), dps(14))
            }
            val n = tv(16f, 0xFFCCCCCC.toInt())
            val t = tv(28f, Color.WHITE, true)
            n.text = name
            card.addView(n)
            card.addView(t, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })
            row.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(5), 0, dp(5), 0) })
            cards[name] = Triple(card, n, t)
        }
        content.addView(row, LinearLayout.LayoutParams(-1, -2))
        applyConfig()
    }

    fun start() {
        if (running) return
        running = true
        bg.start()
        exec = Executors.newSingleThreadExecutor()
        refresh()
        handler.post(tick)
    }

    fun stop() {
        running = false
        bg.stop()
        handler.removeCallbacksAndMessages(null)
        exec?.shutdownNow()
        exec = null
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.currentTimeMillis()
            render(now)
            val d = today
            val stale = now - lastRefresh > 10 * 60 * 1000L ||
                (d != null && d.date != Api.todayStr(d.tz) && now - lastRefresh > 60 * 1000L)
            if (!refreshing && stale) refresh()
            if (++ticks % 60 == 0) {
                // Small drift protects the panel from burn-in.
                content.translationX = (rnd.nextInt(dp(24)) - dp(12)).toFloat()
                content.translationY = (rnd.nextInt(dp(16)) - dp(8)).toFloat()
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun refresh() {
        refreshing = true
        lastRefresh = System.currentTimeMillis()
        val ex = exec ?: return
        ex.execute {
            try {
                val c = Prefs.load(ctx)
                val t = Api.prayerDay(ctx, c, 0)
                val tm = if (t != null) Api.prayerDay(ctx, c, 1) else null
                val m = if (c.showMatches) Api.matches(ctx, c) else emptyList()
                handler.post {
                    cfg = c
                    today = t
                    tomorrow = tm
                    matches = m
                    failed = t == null
                    refreshing = false
                    appliedNext = "?"
                    applyConfig()
                    renderMatches()
                    render(System.currentTimeMillis())
                }
            } catch (e: Exception) {
                handler.post { refreshing = false }
            }
        }
    }

    private fun applyConfig() {
        matchCard.visibility = if (cfg.showMatches && showMatchColumn) View.VISIBLE else View.GONE
        nextLabel.background = Ui.glass(ctx, Ui.withAlpha(cfg.accentColor, 0x55), cfg.accentColor, 30)
        countdown.setTextColor(cfg.accentColor)
    }

    private fun fmtHm(hhmm: String): String {
        if (cfg.h24) return hhmm
        val h = hhmm.substring(0, 2).toInt()
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "$h12:${hhmm.substring(3, 5)} ${if (h < 12) "AM" else "PM"}"
    }

    private fun clockFormat() = when {
        cfg.h24 && cfg.seconds -> "HH:mm:ss"
        cfg.h24 -> "HH:mm"
        cfg.seconds -> "h:mm:ss a"
        else -> "h:mm a"
    }

    private fun countdownText(diff: Long): String {
        val h = diff / 3600000
        val m = (diff / 60000) % 60
        val sec = (diff / 1000) % 60
        return when {
            cfg.seconds -> String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "$m min"
            else -> "<1 min"
        }
    }

    private fun styleCard(name: String, highlighted: Boolean) {
        val (card, n, t) = cards.getValue(name)
        if (highlighted) {
            card.background = Ui.glass(ctx, Ui.withAlpha(cfg.accentColor, 0xE6), cfg.accentColor, 16)
            n.setTextColor(Color.BLACK)
            t.setTextColor(Color.BLACK)
        } else {
            card.background = Ui.glass(ctx, 0x66000000, 0x33FFFFFF, 16)
            n.setTextColor(if (name == "Sunrise") 0xFF9E9E9E.toInt() else 0xFFCCCCCC.toInt())
            t.setTextColor(if (name == "Sunrise") 0xFFBDBDBD.toInt() else Color.WHITE)
        }
    }

    private fun render(now: Long) {
        clock.text = SimpleDateFormat(clockFormat(), Locale.getDefault()).format(Date(now))
        dateTv.text = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date(now))
        city.text = "${cfg.city}, ${cfg.country}"

        val d = today
        hijri.text = d?.hijri ?: ""
        if (d == null) {
            nextLabel.text = if (failed) "Prayer times unavailable" else "Loading prayer times\u2026"
            countdown.text = ""
            status.text = if (failed) "Check the city/country spelling and the internet connection." else ""
            cards.values.forEach { it.third.text = "" }
            return
        }
        status.text = ""

        var nextName: String? = null
        var nextAt = 0L
        for (n in Prefs.ADHAN_PRAYERS) {
            val at = Api.epoch(d, d.times.getValue(n))
            if (at > now) { nextName = n; nextAt = at; break }
        }
        if (nextName == null) {
            val tm = tomorrow
            if (tm != null) { nextName = "Fajr"; nextAt = Api.epoch(tm, tm.times.getValue("Fajr")) }
        }
        if (nextName != null) {
            val shownTime = d.times[nextName]?.let { fmtHm(it) } ?: ""
            nextLabel.text = "NEXT PRAYER  \u00B7  $nextName  $shownTime"
            countdown.text = "in ${countdownText(nextAt - now)}"
        } else {
            nextLabel.text = ""
            countdown.text = ""
        }

        for ((name, parts) in cards) parts.third.text = fmtHm(d.times.getValue(name))
        if (appliedNext != nextName) {
            appliedNext = nextName
            for (name in cards.keys) styleCard(name, name == nextName)
        }
    }

    private fun renderMatches() {
        matchBox.removeAllViews()
        if (!cfg.showMatches) return
        val shown = Api.select(matches, cfg, System.currentTimeMillis())
        if (shown.isEmpty()) {
            matchBox.addView(tv(18f, 0xFFBDBDBD.toInt()).apply { text = "No upcoming matches found" })
            return
        }
        val fmt = SimpleDateFormat(if (cfg.h24) "EEE d MMM  HH:mm" else "EEE d MMM  h:mm a", Locale.getDefault())
        for (m in shown) {
            val fav = Api.isFav(m, cfg)
            val title = tv(22f, if (fav) 0xFFFFD54F.toInt() else Color.WHITE, true)
            title.text = (if (fav) "\u2605 " else "") + "${m.home} vs ${m.away}"
            val sub = tv(14f, 0xFFD0D0D0.toInt())
            sub.text = "${fmt.format(Date(m.whenUtc))}  \u00B7  ${m.league}"
            matchBox.addView(title, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(14) })
            matchBox.addView(sub, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
        }
    }
}
