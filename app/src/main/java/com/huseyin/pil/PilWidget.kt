package com.huseyin.pil

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.widget.RemoteViews

class PilWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val s = BatteryReader.read(context) ?: return
        update(context, s)
    }

    companion object {
        fun update(ctx: Context, s: Snap) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, PilWidget::class.java))
            if (ids.isEmpty()) return

            val state = when (s.status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Şarj oluyor"
                BatteryManager.BATTERY_STATUS_FULL -> "Dolu"
                else -> if (s.isPlugged) "Takılı" else "Şarjda değil"
            }
            val line1 = StringBuilder(state)
            if (s.isPlugged && s.powerW != null) {
                line1.append(String.format(" · %.1f W", s.powerW))
            } else if (s.ma != null) {
                line1.append(String.format(" · %.0f mA", s.ma))
            }
            val line2 = String.format("%.3f V · %.1f °C", s.voltageMv / 1000.0, s.tempC)

            val rv = RemoteViews(ctx.packageName, R.layout.widget_pil)
            rv.setTextViewText(R.id.w_pct, String.format("%%%.0f", s.pct))
            rv.setTextViewText(R.id.w_line1, line1.toString())
            rv.setTextViewText(R.id.w_line2, line2)
            val pi = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(R.id.w_root, pi)
            mgr.updateAppWidget(ids, rv)
        }

        fun updateNow(ctx: Context) {
            BatteryReader.read(ctx)?.let { update(ctx, it) }
        }
    }
}
