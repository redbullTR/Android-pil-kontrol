package com.huseyin.pil

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat

object Notif {
    const val CH_MONITOR = "izleme"
    const val CH_ALERT = "uyari"

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CH_MONITOR, "Arka plan izleme", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Pil uyarıları", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun openAppIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun alert(ctx: Context, id: Int, title: String, text: String) {
        if (!canPost(ctx)) return
        createChannels(ctx)
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent(ctx))
            .build()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, n)
    }
}

/** Şarj eşiği ve sıcaklık uyarıları. Her uyarı bir şarj boyunca bir kez verilir. */
object Alerts {
    const val HOT_C = 42f
    const val COOL_C = 38f

    private var chargeAlerted = false
    private var tempAlerted = false

    @Synchronized
    fun check(ctx: Context, s: Snap, prefs: SharedPreferences) {
        val th = prefs.getInt("alertPct", 0)
        if (!s.isPlugged) {
            chargeAlerted = false
        } else if (th in 1..100 && !chargeAlerted && s.pct >= th) {
            chargeAlerted = true
            val title = if (th >= 100) "Şarj tamamlandı" else "Pil %$th oldu"
            Notif.alert(ctx, 101, title, "Şarjı çıkarmak pil ömrü için iyi olur.")
        }

        if (prefs.getBoolean("alertTemp", false)) {
            if (s.tempC >= HOT_C && !tempAlerted) {
                tempAlerted = true
                Notif.alert(
                    ctx, 102, "Pil sıcaklığı yüksek",
                    String.format("%.1f °C. Şarjı ya da kullanımı azaltmayı dene.", s.tempC)
                )
            } else if (s.tempC < COOL_C) {
                tempAlerted = false
            }
        }
    }
}
