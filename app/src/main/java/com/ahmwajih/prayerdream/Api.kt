package com.ahmwajih.prayerdream

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object Api {
    val PRAYERS = listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")
    private const val EVENTS_TTL_MS = 3 * 3600 * 1000L

    class Day(val times: Map<String, String>, val tz: String, val date: String, val hijri: String)

    class Match(
        val home: String,
        val away: String,
        val league: String,
        val leagueId: String,
        val whenUtc: Long,
    )

    private fun sp(c: Context) = c.getSharedPreferences("cache", Context.MODE_PRIVATE)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun http(url: String): String? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", "PrayerDream/1.0")
        if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
    } catch (e: Exception) {
        null
    }

    private fun parseDay(body: String): Day? = try {
        val data = JSONObject(body).getJSONObject("data")
        val t = data.getJSONObject("timings")
        val times = PRAYERS.associateWith { t.getString(it).substring(0, 5) }
        val h = data.getJSONObject("date").getJSONObject("hijri")
        val hijri = "${h.getString("day").trimStart('0')} ${h.getJSONObject("month").getString("en")} ${h.getString("year")} AH"
        Day(
            times,
            data.getJSONObject("meta").optString("timezone"),
            data.getJSONObject("date").getJSONObject("gregorian").getString("date"),
            hijri,
        )
    } catch (e: Exception) {
        null
    }

    fun todayStr(tz: String, offsetDays: Int = 0): String {
        val zone = if (tz.isEmpty()) TimeZone.getDefault() else TimeZone.getTimeZone(tz)
        val cal = Calendar.getInstance(zone)
        cal.add(Calendar.DAY_OF_YEAR, offsetDays)
        return SimpleDateFormat("dd-MM-yyyy", Locale.US).apply { timeZone = zone }.format(cal.time)
    }

    fun epoch(day: Day, hhmm: String): Long {
        val zone = if (day.tz.isEmpty()) TimeZone.getDefault() else TimeZone.getTimeZone(day.tz)
        val f = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US).apply { timeZone = zone }
        return f.parse("${day.date} $hhmm")!!.time
    }

    fun prayerDay(ctx: Context, cfg: Prefs.Cfg, offsetDays: Int): Day? {
        val base = "${cfg.city.lowercase()}|${cfg.country.lowercase()}|${cfg.method}"
        val p = sp(ctx)
        repeat(2) {
            val tz = p.getString("tz|$base", "") ?: ""
            val date = todayStr(tz, offsetDays)
            val key = "pt|$base|$date"
            p.getString(key, null)?.let { cached -> parseDay(cached)?.let { return it } }

            val url = "https://api.aladhan.com/v1/timingsByCity/$date" +
                "?city=${enc(cfg.city)}&country=${enc(cfg.country)}&method=${cfg.method}"
            val body = http(url) ?: return null
            val day = parseDay(body) ?: return null
            p.edit().putString(key, body).putString("tz|$base", day.tz).apply()
            // First fetch ran with the device timezone; redo once with the city's.
            if (tz.isNotEmpty() || day.tz.isEmpty()) return day
        }
        return null
    }

    private fun parseEvents(body: String, leagueId: String): List<Match> {
        val out = ArrayList<Match>()
        val arr = JSONObject(body).optJSONArray("events") ?: return out
        val utc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            val ts = e.optString("strTimestamp").ifEmpty { "${e.optString("dateEvent")}T${e.optString("strTime")}" }
            val whenUtc = try { utc.parse(ts)!!.time } catch (ex: Exception) { continue }
            out.add(Match(e.optString("strHomeTeam"), e.optString("strAwayTeam"), e.optString("strLeague"), leagueId, whenUtc))
        }
        return out
    }

    fun matches(ctx: Context, cfg: Prefs.Cfg): List<Match> {
        val p = sp(ctx)
        val all = ArrayList<Match>()
        for (id in cfg.leagues) {
            val key = "ev|${cfg.apiKey}|$id"
            val stamp = p.getLong("$key|t", 0L)
            var body = p.getString(key, null)
            if (body == null || System.currentTimeMillis() - stamp > EVENTS_TTL_MS) {
                val fresh = http("https://www.thesportsdb.com/api/v1/json/${enc(cfg.apiKey)}/eventsnextleague.php?id=$id")
                if (fresh != null && fresh.trimStart().startsWith("{")) {
                    p.edit().putString(key, fresh).putLong("$key|t", System.currentTimeMillis()).apply()
                    body = fresh
                }
            }
            if (body != null) try { all.addAll(parseEvents(body, id)) } catch (e: Exception) { }
        }
        return all
    }

    fun isFav(m: Match, cfg: Prefs.Cfg): Boolean = cfg.favorites.any {
        m.home.contains(it, ignoreCase = true) || m.away.contains(it, ignoreCase = true)
    }

    fun select(list: List<Match>, cfg: Prefs.Cfg, now: Long): List<Match> {
        val upcoming = list.filter { it.whenUtc > now - 2 * 3600 * 1000L }
        val picked = if (cfg.prioritize) {
            val near = upcoming.filter { it.whenUtc <= now + 7 * 86400 * 1000L }.ifEmpty { upcoming }
            val rank = Prefs.LEAGUES.keys.toList()
            near.sortedWith(
                compareBy<Match>({ if (isFav(it, cfg)) 0 else 1 }, { rank.indexOf(it.leagueId) }, { it.whenUtc }),
            )
        } else {
            upcoming.sortedBy { it.whenUtc }
        }
        return picked.take(cfg.maxMatches).sortedBy { it.whenUtc }
    }
}
