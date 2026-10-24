package com.ahmwajih.prayerdream

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import kotlin.concurrent.thread

class QuranActivity : Activity() {
    private lateinit var bg: BackgroundView
    private lateinit var spinner: Spinner
    private lateinit var list: ListView
    private lateinit var status: TextView
    private var reciters = mutableListOf<Reciter>()
    private var names: Map<Int, String> = emptyMap()
    private var shown: List<Int> = emptyList()
    private val handler = Handler(Looper.getMainLooper())

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val accent = Prefs.load(this).accentColor
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        bg = BackgroundView(this)
        root.addView(bg, FrameLayout.LayoutParams(-1, -1))

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(28), dp(48), dp(28))
        }
        root.addView(col, FrameLayout.LayoutParams(-1, -1))

        val title = TextView(this).apply {
            text = "Quran"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
            setShadowLayer(8f, 0f, 2f, 0xAA000000.toInt())
        }
        col.addView(title)

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        spinner = Spinner(this)
        spinner.background = Ui.glass(this, 0x77000000, 0x33FFFFFF, 14)
        top.addView(spinner, LinearLayout.LayoutParams(0, dp(52), 1f))
        top.addView(
            Ui.menuButton(this, "Add reciter", accent) { startActivity(Intent(this, AddReciterActivity::class.java)) },
            LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) },
        )
        top.addView(Ui.menuButton(this, "Remove", accent) { removeCurrent() }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) })
        col.addView(top, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        status = TextView(this).apply {
            setTextColor(0xFFE0E0E0.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setShadowLayer(8f, 0f, 2f, 0xAA000000.toInt())
        }
        col.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10); bottomMargin = dp(10) })

        list = ListView(this).apply {
            divider = null
            selector = Ui.glass(this@QuranActivity, Ui.withAlpha(accent, 0x66), accent, 12)
            background = Ui.glass(this@QuranActivity, 0x66000000, 0x33FFFFFF, 18)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        col.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun control(text: String, action: String) {
            val b = Ui.menuButton(this, text, accent) { PlaybackService.command(this, action) }
            b.gravity = Gravity.CENTER
            controls.addView(b, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        }
        control("Previous", PlaybackService.ACTION_PREV)
        control("Pause / Resume", PlaybackService.ACTION_TOGGLE)
        control("Next", PlaybackService.ACTION_NEXT)
        control("Stop", PlaybackService.ACTION_STOP)
        col.addView(controls, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        setContentView(root)

        list.setOnItemClickListener { _, _, position, _ ->
            val r = current() ?: return@setOnItemClickListener
            PlaybackService.play(this, r, position)
        }
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                Reciters.setSelected(this@QuranActivity, pos)
                refreshList()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun current(): Reciter? = reciters.getOrNull(spinner.selectedItemPosition)

    private fun loadReciters() {
        reciters = Reciters.list(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, reciters.map { it.name })
        spinner.setSelection(Reciters.selected(this).coerceIn(0, reciters.size - 1))
    }

    private fun refreshList() {
        val r = current() ?: return
        shown = r.surahs
        list.adapter = Ui.ListAdapter(this, shown.map { "$it.  ${names[it] ?: "Surah $it"}" })
    }

    private fun removeCurrent() {
        if (reciters.size <= 1) return
        reciters.removeAt(spinner.selectedItemPosition)
        Reciters.save(this, reciters)
        Reciters.setSelected(this, 0)
        loadReciters()
    }

    private val poll = object : Runnable {
        override fun run() {
            val s = PlaybackService.currentSurah
            status.text = when (PlaybackService.state) {
                "idle" -> "Choose a surah to play"
                else -> "${PlaybackService.state}: $s. ${names[s] ?: "Surah $s"} \u2013 ${PlaybackService.reciterName}"
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onResume() {
        super.onResume()
        bg.start()
        loadReciters()
        thread {
            val n = Reciters.surahNames(this)
            handler.post { names = n; refreshList() }
        }
        handler.post(poll)
    }

    override fun onPause() {
        bg.stop()
        handler.removeCallbacksAndMessages(null)
        super.onPause()
    }
}
