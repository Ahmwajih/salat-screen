package com.ahmwajih.prayerdream

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Adhan.ACTION_PLAY -> Adhan.startService(
                context,
                Intent(context, AdhanService::class.java)
                    .setAction(Adhan.ACTION_PLAY)
                    .putExtra("prayer", intent.getStringExtra("prayer") ?: ""),
            )
            Adhan.ACTION_SCHEDULE -> {
                val r = goAsync()
                thread { try { Adhan.schedule(context) } finally { r.finish() } }
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val r = goAsync()
        thread { try { Adhan.schedule(context) } finally { r.finish() } }
    }
}
