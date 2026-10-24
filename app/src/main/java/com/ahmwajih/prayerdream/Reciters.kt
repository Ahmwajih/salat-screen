package com.ahmwajih.prayerdream

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Reciter(val name: String, val server: String, val surahs: List<Int>)

object Reciters {
    // Mishary Rashid Alafasy, Hafs, all 114 surahs (mp3quran.net CDN).
    val DEFAULT = Reciter(
        "Mishary Rashid Alafasy (Hafs)",
        "https://cdn.mp3quran.net/audio/mishary-alafasy/r1/",
        (1..114).toList(),
    )

    private fun sp(c: Context) = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    fun list(ctx: Context): MutableList<Reciter> {
        val raw = sp(ctx).getString("reciters", null) ?: return mutableListOf(DEFAULT)
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf<Reciter>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Reciter(
                        o.getString("name"),
                        o.getString("server"),
                        o.getString("surahs").split(",").mapNotNull { it.toIntOrNull() },
                    ),
                )
            }
            if (out.isEmpty()) mutableListOf(DEFAULT) else out
        } catch (e: Exception) {
            mutableListOf(DEFAULT)
        }
    }

    fun save(ctx: Context, list: List<Reciter>) {
        val arr = JSONArray()
        for (r in list) {
            arr.put(JSONObject().put("name", r.name).put("server", r.server).put("surahs", r.surahs.joinToString(",")))
        }
        sp(ctx).edit().putString("reciters", arr.toString()).apply()
    }

    fun selected(ctx: Context): Int = sp(ctx).getInt("reciterSel", 0)
    fun setSelected(ctx: Context, i: Int) = sp(ctx).edit().putInt("reciterSel", i).apply()

    // Blocking: all reciters/recitations from mp3quran.net, one entry per recitation.
    fun fetchAll(): List<Reciter> {
        val body = Api.http("https://mp3quran.net/api/v3/reciters?language=eng") ?: return emptyList()
        val out = ArrayList<Reciter>()
        try {
            val arr = JSONObject(body).getJSONArray("reciters")
            for (i in 0 until arr.length()) {
                val r = arr.getJSONObject(i)
                val ms = r.getJSONArray("moshaf")
                for (j in 0 until ms.length()) {
                    val m = ms.getJSONObject(j)
                    var server = m.getString("server")
                    if (!server.endsWith("/")) server += "/"
                    val surahs = m.getString("surah_list").split(",").mapNotNull { it.trim().toIntOrNull() }
                    if (surahs.isNotEmpty()) out.add(Reciter("${r.getString("name")} \u2013 ${m.getString("name")}", server, surahs))
                }
            }
        } catch (e: Exception) {
            return emptyList()
        }
        return out
    }

    // Blocking: surah names, cached after the first successful fetch.
    fun surahNames(ctx: Context): Map<Int, String> {
        val p = ctx.getSharedPreferences("cache", Context.MODE_PRIVATE)
        var body = p.getString("suwar", null)
        if (body == null) {
            body = Api.http("https://mp3quran.net/api/v3/suwar?language=eng")
            if (body != null) p.edit().putString("suwar", body).apply()
        }
        val out = HashMap<Int, String>()
        try {
            val arr = JSONObject(body ?: return out).getJSONArray("suwar")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out[o.getInt("id")] = o.getString("name").trim()
            }
        } catch (e: Exception) { }
        return out
    }
}
