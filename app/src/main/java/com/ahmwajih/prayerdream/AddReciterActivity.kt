package com.ahmwajih.prayerdream

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import kotlin.concurrent.thread

class AddReciterActivity : Activity() {
    private var all: List<Reciter> = emptyList()
    private var filtered: List<Reciter> = emptyList()
    private lateinit var list: ListView
    private lateinit var info: TextView
    private val handler = Handler(Looper.getMainLooper())

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(48), dp(24), dp(48), dp(24))
        }
        val search = EditText(this).apply {
            hint = "Search reciter (e.g. Sudais, Husary, Minshawi)"
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF757575.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setSingleLine()
        }
        root.addView(search)
        info = TextView(this).apply {
            text = "Loading reciters\u2026"
            setTextColor(0xFF9E9E9E.toInt())
        }
        root.addView(info)
        list = ListView(this).apply {
            divider = null
            selector = Ui.glass(this@AddReciterActivity, Ui.withAlpha(Prefs.load(this@AddReciterActivity).accentColor, 0x66), Prefs.load(this@AddReciterActivity).accentColor, 12)
            background = Ui.glass(this@AddReciterActivity, 0x66000000, 0x33FFFFFF, 18)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = applyFilter(s.toString())
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        list.setOnItemClickListener { _, _, position, _ ->
            val r = filtered[position]
            val saved = Reciters.list(this)
            if (saved.none { it.server == r.server }) saved.add(r)
            Reciters.save(this, saved)
            Reciters.setSelected(this, saved.indexOfFirst { it.server == r.server })
            finish()
        }

        thread {
            val r = Reciters.fetchAll()
            handler.post {
                all = r
                info.text = if (r.isEmpty()) "Could not load reciters. Check the internet connection." else "${r.size} recitations. Select one to add."
                applyFilter("")
            }
        }
    }

    private fun applyFilter(q: String) {
        filtered = all.filter { it.name.contains(q.trim(), ignoreCase = true) }.take(200)
        list.adapter = Ui.ListAdapter(this, filtered.map { "${it.name} (${it.surahs.size} surahs)" })
    }
}
