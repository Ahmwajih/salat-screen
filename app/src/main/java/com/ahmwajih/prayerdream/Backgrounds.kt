package com.ahmwajih.prayerdream

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Photos come from Wikimedia Commons featured pictures; only CC / public-domain licences are kept.
object Backgrounds {
    class Photo(val url: String, val credit: String)

    private const val TTL_MS = 7 * 86400 * 1000L
    private const val MAX_CACHED_FILES = 25
    private const val API = "https://commons.wikimedia.org/w/api.php?action=query&prop=imageinfo" +
        "&iiprop=url|size|extmetadata&iiurlwidth=1920&format=json"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun sp(c: Context) = c.getSharedPreferences("cache", Context.MODE_PRIVATE)

    private fun mosqueUrl() = "$API&generator=search&gsrnamespace=6&gsrlimit=50&gsrsearch=" +
        enc("mosque OR masjid OR minaret OR mihrab incategory:\"Featured pictures on Wikimedia Commons\"")

    private fun categoryUrl(cat: String) = "$API&generator=categorymembers&gcmtype=file&gcmlimit=50&gcmtitle=" + enc(cat)

    private fun parse(body: String): List<Photo> {
        val out = ArrayList<Photo>()
        try {
            val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages") ?: return out
            for (key in pages.keys()) {
                val info = pages.getJSONObject(key).optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                val w = info.optInt("width")
                val h = info.optInt("height")
                if (h == 0 || w.toFloat() / h < 1.3f) continue
                val meta = info.optJSONObject("extmetadata") ?: continue
                val licence = meta.optJSONObject("LicenseShortName")?.optString("value").orEmpty()
                val free = licence.startsWith("CC") || licence.contains("public domain", ignoreCase = true)
                if (!free) continue
                val artist = meta.optJSONObject("Artist")?.optString("value").orEmpty()
                    .replace(Regex("<[^>]*>"), "").trim().take(60)
                val url = info.optString("thumburl")
                if (url.isEmpty()) continue
                out.add(Photo(url, "${artist.ifEmpty { "Unknown" }} / Wikimedia Commons \u00B7 $licence"))
            }
        } catch (e: Exception) { }
        return out
    }

    // Blocking: photo list for a mode ("mosques", "nature", "both"), cached for a week.
    fun list(ctx: Context, mode: String): List<Photo> {
        val p = sp(ctx)
        val key = "bg|$mode"
        val stale = p.getString(key, null)
        if (stale != null && System.currentTimeMillis() - p.getLong("$key|t", 0L) < TTL_MS) return fromJson(stale)

        val urls = ArrayList<String>()
        if (mode == "mosques" || mode == "both") urls.add(mosqueUrl())
        if (mode == "nature" || mode == "both") {
            listOf("Featured pictures of landscapes", "Featured pictures of sunsets", "Featured pictures of mountains")
                .forEach { urls.add(categoryUrl("Category:$it")) }
        }
        val photos = urls.flatMap { u -> Api.http(u)?.let { parse(it) } ?: emptyList() }
        if (photos.isEmpty()) return if (stale != null) fromJson(stale) else emptyList()
        val arr = JSONArray()
        photos.forEach { arr.put(JSONObject().put("u", it.url).put("c", it.credit)) }
        p.edit().putString(key, arr.toString()).putLong("$key|t", System.currentTimeMillis()).apply()
        return photos
    }

    private fun fromJson(raw: String): List<Photo> = try {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { Photo(arr.getJSONObject(it).getString("u"), arr.getJSONObject(it).getString("c")) }
    } catch (e: Exception) {
        emptyList()
    }

    // Blocking: downloads (once) and decodes the photo, scaled down to at most ~1920 px wide.
    fun bitmap(ctx: Context, photo: Photo): Bitmap? {
        val dir = File(ctx.cacheDir, "bg").apply { mkdirs() }
        val f = File(dir, "${photo.url.hashCode()}.jpg")
        if (!f.exists() || f.length() < 10_000) {
            try {
                val conn = URL(photo.url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", "PrayerDream/1.0")
                if (conn.responseCode != 200) return null
                val tmp = File(dir, "dl.part")
                conn.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                tmp.renameTo(f)
            } catch (e: Exception) {
                return null
            }
            dir.listFiles { x -> x.extension == "jpg" }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_CACHED_FILES)
                ?.forEach { it.delete() }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1920) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
