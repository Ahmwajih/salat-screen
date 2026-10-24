package com.ahmwajih.prayerdream

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {
    private lateinit var col: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String) {
        col.addView(TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(0xFF9E9E9E.toInt())
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
    }

    private fun edit(value: String): EditText {
        val e = EditText(this).apply {
            setText(value)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT
        }
        col.addView(e, LinearLayout.LayoutParams(-1, -2))
        return e
    }

    private fun check(text: String, on: Boolean): CheckBox {
        val c = CheckBox(this).apply {
            this.text = text
            isChecked = on
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        }
        col.addView(c, LinearLayout.LayoutParams(-1, -2))
        return c
    }

    private fun spinner(items: List<String>, selected: Int): Spinner {
        val s = Spinner(this)
        s.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        s.setSelection(selected.coerceAtLeast(0))
        col.addView(s, LinearLayout.LayoutParams(-1, dp(52)))
        return s
    }

    private fun button(text: String, onClick: () -> Unit) {
        col.addView(Button(this).apply {
            this.text = text
            setOnClickListener { onClick() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cfg = Prefs.load(this)

        val scroll = ScrollView(this).apply { setBackgroundColor(Color.BLACK) }
        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(64), dp(32), dp(64), dp(48))
        }
        scroll.addView(col)
        setContentView(scroll)

        col.addView(TextView(this).apply {
            text = "Prayer Screensaver \u2013 Settings"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTextColor(Color.WHITE)
        })

        label("City")
        val city = edit(cfg.city)
        label("Country")
        val country = edit(cfg.country)

        label("Calculation method")
        val methodIds = Prefs.METHODS.keys.toList()
        val method = spinner(Prefs.METHODS.values.toList(), methodIds.indexOf(cfg.method))

        label("Display")
        val h24 = check("24-hour clock", cfg.h24)
        val seconds = check("Show seconds", cfg.seconds)
        val bright = check("Bright screen while screensaver runs", cfg.bright)

        label("Accent color")
        val accentNames = Prefs.ACCENTS.keys.toList()
        val accent = spinner(accentNames, accentNames.indexOf(cfg.accent))

        label("Background photos")
        val bgModeKeys = Prefs.BG_MODES.keys.toList()
        val bgMode = spinner(Prefs.BG_MODES.values.toList(), bgModeKeys.indexOf(cfg.bgMode))
        label("Change photo every")
        val bgIntervals = listOf(30, 60, 120, 300)
        val bgSeconds = spinner(bgIntervals.map { "$it seconds" }, bgIntervals.indexOf(cfg.bgSeconds))

        label("Football matches")
        val showMatches = check("Show upcoming matches", cfg.showMatches)
        val prioritize = check("Most important first (favorites, then top leagues)", cfg.prioritize)
        val leagueChecks = Prefs.LEAGUES.map { (id, name) -> check(name, id in cfg.leagues) }

        label("Favorite teams (comma separated, e.g. Real Madrid, Bayern)")
        val favorites = edit(cfg.favorites.joinToString(", "))

        label("Number of matches to show")
        val maxMatches = spinner((1..6).map { it.toString() }, cfg.maxMatches - 1)

        label("TheSportsDB API key (123 = free test key)")
        val apiKey = edit(cfg.apiKey)

        label("Adhan")
        val adhanOn = check("Play adhan at prayer times", cfg.adhanOn)
        val adhanChecks = Prefs.ADHAN_PRAYERS.map { check(it, it in cfg.adhanPrayers) }

        label("Adhan sound")
        val presetNames = Prefs.ADHAN_PRESETS.keys.toList() + "Custom URL"
        val presetUrls = Prefs.ADHAN_PRESETS.values.toList()
        val currentPreset = presetUrls.indexOf(cfg.adhanUrl).let { if (it < 0) presetNames.size - 1 else it }
        val preset = spinner(presetNames, currentPreset)
        val adhanUrl = edit(cfg.adhanUrl)
        preset.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos < presetUrls.size) adhanUrl.setText(presetUrls[pos])
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        label("Adhan volume")
        val volumes = (1..10).map { it * 10 }
        val volume = spinner(volumes.map { "$it%" }, volumes.indexOf(cfg.adhanVolume))

        button("Save") {
            val leagues = Prefs.LEAGUES.keys.filterIndexed { i, _ -> leagueChecks[i].isChecked }.toSet()
            Prefs.save(
                this,
                Prefs.Cfg(
                    city = city.text.toString().trim().ifEmpty { "Berlin" },
                    country = country.text.toString().trim().ifEmpty { "Germany" },
                    method = methodIds[method.selectedItemPosition],
                    h24 = h24.isChecked,
                    seconds = seconds.isChecked,
                    showMatches = showMatches.isChecked,
                    leagues = leagues,
                    favorites = favorites.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    maxMatches = maxMatches.selectedItemPosition + 1,
                    accent = accentNames[accent.selectedItemPosition],
                    apiKey = apiKey.text.toString().trim().ifEmpty { "123" },
                    bright = bright.isChecked,
                    prioritize = prioritize.isChecked,
                    adhanOn = adhanOn.isChecked,
                    adhanPrayers = Prefs.ADHAN_PRAYERS.filterIndexed { i, _ -> adhanChecks[i].isChecked }.toSet(),
                    adhanUrl = adhanUrl.text.toString().trim().ifEmpty { presetUrls.first() },
                    adhanVolume = volumes[volume.selectedItemPosition],
                    bgMode = bgModeKeys[bgMode.selectedItemPosition],
                    bgSeconds = bgIntervals[bgSeconds.selectedItemPosition],
                ),
            )
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            kotlin.concurrent.thread { Adhan.schedule(this) }
        }
        button("Preview (press any key to exit)") {
            startActivity(Intent(this, PreviewActivity::class.java))
        }
        button("Choose screensaver in system settings") {
            try {
                startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "Open Settings > Device Preferences > Screen saver on the box.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
        scroll.visibility = View.VISIBLE
    }
}
