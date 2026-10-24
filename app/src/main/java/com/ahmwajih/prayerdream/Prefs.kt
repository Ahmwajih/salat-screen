package com.ahmwajih.prayerdream

import android.content.Context

object Prefs {
    val BG_MODES = linkedMapOf(
        "both" to "Mosques + nature photos",
        "mosques" to "Mosques only",
        "nature" to "Nature only",
        "gradient" to "Gradient only (no internet)",
    )

    val ADHAN_PRAYERS = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")

    val ADHAN_PRESETS = linkedMapOf(
        "Azan 1 (IslamCan)" to "https://www.islamcan.com/audio/adhan/azan1.mp3",
        "Azan 2 (IslamCan)" to "https://www.islamcan.com/audio/adhan/azan2.mp3",
    )

    val LEAGUES = linkedMapOf(
        "4480" to "UEFA Champions League",
        "4481" to "UEFA Europa League",
        "4328" to "Premier League",
        "4335" to "La Liga",
        "4331" to "Bundesliga",
        "4332" to "Serie A",
        "4334" to "Ligue 1",
    )

    val METHODS = linkedMapOf(
        3 to "Muslim World League",
        2 to "ISNA (North America)",
        4 to "Umm al-Qura (Makkah)",
        5 to "Egyptian General Authority",
        1 to "Karachi",
        12 to "France (UOIF)",
        13 to "Diyanet (Turkey)",
        21 to "Morocco",
        19 to "Algeria",
        18 to "Tunisia",
        17 to "JAKIM (Malaysia)",
        15 to "Moonsighting Committee",
    )

    val ACCENTS = linkedMapOf(
        "Green" to 0xFF2ECC71.toInt(),
        "Gold" to 0xFFF1C40F.toInt(),
        "Blue" to 0xFF3498DB.toInt(),
        "White" to 0xFFFFFFFF.toInt(),
    )

    data class Cfg(
        val city: String,
        val country: String,
        val method: Int,
        val h24: Boolean,
        val seconds: Boolean,
        val showMatches: Boolean,
        val leagues: Set<String>,
        val favorites: List<String>,
        val maxMatches: Int,
        val accent: String,
        val apiKey: String,
        val bright: Boolean,
        val prioritize: Boolean,
        val adhanOn: Boolean,
        val adhanPrayers: Set<String>,
        val adhanUrl: String,
        val adhanVolume: Int,
        val bgMode: String,
        val bgSeconds: Int,
    ) {
        val accentColor: Int get() = ACCENTS[accent] ?: ACCENTS.getValue("Green")
    }

    private fun sp(c: Context) = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    fun load(c: Context): Cfg {
        val p = sp(c)
        return Cfg(
            city = p.getString("city", "Berlin") ?: "Berlin",
            country = p.getString("country", "Germany") ?: "Germany",
            method = p.getInt("method", 3),
            h24 = p.getBoolean("h24", true),
            seconds = p.getBoolean("seconds", false),
            showMatches = p.getBoolean("showMatches", true),
            leagues = (p.getString("leagues", LEAGUES.keys.joinToString(",")) ?: "")
                .split(",").filter { it.isNotBlank() }.toSet(),
            favorites = (p.getString("favorites", "") ?: "")
                .split(",").map { it.trim() }.filter { it.isNotEmpty() },
            maxMatches = p.getInt("maxMatches", 4),
            accent = p.getString("accent", "Green") ?: "Green",
            apiKey = p.getString("apiKey", "123") ?: "123",
            bright = p.getBoolean("bright", false),
            prioritize = p.getBoolean("prioritize", true),
            adhanOn = p.getBoolean("adhanOn", true),
            adhanPrayers = (p.getString("adhanPrayers", ADHAN_PRAYERS.joinToString(",")) ?: "")
                .split(",").filter { it.isNotBlank() }.toSet(),
            adhanUrl = p.getString("adhanUrl", ADHAN_PRESETS.values.first()) ?: ADHAN_PRESETS.values.first(),
            adhanVolume = p.getInt("adhanVolume", 80),
            bgMode = p.getString("bgMode", "both") ?: "both",
            bgSeconds = p.getInt("bgSeconds", 60),
        )
    }

    fun save(c: Context, cfg: Cfg) {
        sp(c).edit()
            .putString("city", cfg.city)
            .putString("country", cfg.country)
            .putInt("method", cfg.method)
            .putBoolean("h24", cfg.h24)
            .putBoolean("seconds", cfg.seconds)
            .putBoolean("showMatches", cfg.showMatches)
            .putString("leagues", cfg.leagues.joinToString(","))
            .putString("favorites", cfg.favorites.joinToString(","))
            .putInt("maxMatches", cfg.maxMatches)
            .putString("accent", cfg.accent)
            .putString("apiKey", cfg.apiKey)
            .putBoolean("bright", cfg.bright)
            .putBoolean("prioritize", cfg.prioritize)
            .putBoolean("adhanOn", cfg.adhanOn)
            .putString("adhanPrayers", cfg.adhanPrayers.joinToString(","))
            .putString("adhanUrl", cfg.adhanUrl)
            .putInt("adhanVolume", cfg.adhanVolume)
            .putString("bgMode", cfg.bgMode)
            .putInt("bgSeconds", cfg.bgSeconds)
            .apply()
    }
}
