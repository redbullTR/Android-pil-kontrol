package com.huseyin.pil

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Uygulama kapalıyken de şarj kaydı ve uyarılar için hafif arka plan izleme. */
class MonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastText = ""

    private val ticker = object : Runnable {
        override fun run() {
            doTick()
            handler.postDelayed(this, 15_000L)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            doTick()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notif.createChannels(this)
        val n = buildNotification("Pil izleniyor")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, receiver, f, ContextCompat.RECEIVER_EXPORTED)
        handler.post(ticker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    private fun doTick() {
        try {
            val s = BatteryReader.read(this) ?: return
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            Tracker.feed(this, s, pm.isInteractive)
            PilWidget.update(this, s)
            val text = summary(s)
            if (text != lastText) {
                lastText = text
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(1, buildNotification(text))
            }
        } catch (e: Exception) {
            // izleme sessizce devam etsin
        }
    }

    private fun summary(s: Snap): String {
        val sb = StringBuilder(String.format("%%%.0f", s.pct))
        if (s.isPlugged && s.powerW != null) {
            sb.append(String.format(" · %.1f W", s.powerW))
        } else if (s.ma != null) {
            sb.append(String.format(" · %.0f mA", s.ma))
        }
        sb.append(String.format(" · %.1f °C", s.tempC))
        return sb.toString()
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, Notif.CH_MONITOR)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Pil")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(Notif.openAppIntent(this))
            .build()
}
