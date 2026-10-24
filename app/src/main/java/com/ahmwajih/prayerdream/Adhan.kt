package com.ahmwajih.prayerdream

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object Adhan {
    const val ACTION_PLAY = "com.ahmwajih.prayerdream.PLAY_ADHAN"
    const val ACTION_SCHEDULE = "com.ahmwajih.prayerdream.SCHEDULE"
    const val ACTION_STOP = "com.ahmwajih.prayerdream.STOP"
    private const val CHANNEL = "audio"

    fun notification(ctx: Context, title: String, text: String): Notification {
        val b = if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Adhan & Quran", NotificationManager.IMPORTANCE_LOW))
            Notification.Builder(ctx, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(ctx)
        }
        return b.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title).setContentText(text).build()
    }

    fun goForeground(svc: Service, id: Int, n: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            svc.startForeground(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            svc.startForeground(id, n)
        }
    }

    fun startService(ctx: Context, i: Intent) {
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
    }

    private fun cacheFile(ctx: Context, url: String) = File(ctx.filesDir, "adhan_${url.hashCode()}.mp3")

    // Blocking: call from a background thread.
    fun ensureCached(ctx: Context, url: String): File? {
        val f = cacheFile(ctx, url)
        if (f.exists() && f.length() > 10_000) return f
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 30000
            if (conn.responseCode != 200) return null
            val tmp = File(ctx.filesDir, "adhan_tmp.part")
            conn.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            if (tmp.length() > 10_000 && tmp.renameTo(f)) f else null
        } catch (e: Exception) {
            null
        }
    }

    private fun pending(ctx: Context, action: String, code: Int, prayer: String? = null): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(action)
        if (prayer != null) i.putExtra("prayer", prayer)
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    // Blocking: call from a background thread. Sets one alarm for the next enabled prayer.
    fun schedule(ctx: Context) {
        val cfg = Prefs.load(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val retry = pending(ctx, ACTION_SCHEDULE, 2)
        val play = pending(ctx, ACTION_PLAY, 1)
        if (!cfg.adhanOn) {
            am.cancel(play)
            am.cancel(retry)
            return
        }

        val now = System.currentTimeMillis()
        // Self-heal: re-run scheduling every 6 hours even if no adhan fires.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, now + 6 * 3600 * 1000L, retry)

        val days = listOfNotNull(Api.prayerDay(ctx, cfg, 0), Api.prayerDay(ctx, cfg, 1))
        var best: Pair<Long, String>? = null
        for (d in days) {
            for (name in Prefs.ADHAN_PRAYERS) {
                if (name !in cfg.adhanPrayers) continue
                val at = Api.epoch(d, d.times.getValue(name))
                if (at > now + 1000 && (best == null || at < best.first)) best = at to name
            }
        }
        if (best == null) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, now + 30 * 60 * 1000L, retry)
            return
        }

        val pi = pending(ctx, ACTION_PLAY, 1, best.second)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, best.first, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, best.first, pi)
        }
        ensureCached(ctx, cfg.adhanUrl)
    }
}
