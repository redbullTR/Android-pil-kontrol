package com.huseyin.pil

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build

/** Bir andaki tüm pil değerleri. */
data class Snap(
    val ts: Long,
    val pct: Float,
    val status: Int,
    val plugged: Int,
    val health: Int,
    val voltageMv: Int,
    val tempC: Float,
    val tech: String,
    val cycles: Int,
    val ma: Double?,
    val counterMah: Double?,
    val estFullMah: Double?,
    val soh: Int?,
    val chargeMs: Long,
    val powerW: Double?
) {
    val isPlugged: Boolean get() = plugged != 0
    val isCharging: Boolean get() = status == BatteryManager.BATTERY_STATUS_CHARGING
}

object BatteryReader {

    private fun longProp(bm: BatteryManager, id: Int): Long = try {
        bm.getLongProperty(id)
    } catch (e: Exception) {
        Long.MIN_VALUE
    }

    private fun intProp(bm: BatteryManager, id: Int): Int = try {
        bm.getIntProperty(id)
    } catch (e: Exception) {
        Int.MIN_VALUE
    }

    fun read(ctx: Context): Snap? {
        val app = ctx.applicationContext
        val i = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val bm = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct = if (level >= 0 && scale > 0) level * 100f / scale else 0f
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val health = i.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val voltage = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        val tech = i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "—"
        val cycles = if (Build.VERSION.SDK_INT >= 34) i.getIntExtra("android.os.extra.CYCLE_COUNT", -1) else -1

        val isPlugged = plugged != 0
        val ma = Logic.normalizeCurrentMa(longProp(bm, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW), isPlugged)

        val rawCounter = intProp(bm, BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val counter: Double? = if (rawCounter != Int.MIN_VALUE && rawCounter > 0) rawCounter / 1000.0 else null
        val est = Logic.estFullMah(counter, pct)

        var soh: Int? = null
        if (Build.VERSION.SDK_INT >= 34) {
            // BATTERY_PROPERTY_STATE_OF_HEALTH: bazı telefonlar BATTERY_STATS izni ister
            val v = intProp(bm, 10)
            if (v in 1..100) soh = v
        }

        var chargeMs = -1L
        if (Build.VERSION.SDK_INT >= 28 && status == BatteryManager.BATTERY_STATUS_CHARGING) {
            chargeMs = try {
                bm.computeChargeTimeRemaining()
            } catch (e: Exception) {
                -1L
            }
        }

        return Snap(
            System.currentTimeMillis(), pct, status, plugged, health, voltage, temp, tech, cycles,
            ma, counter, est, soh, chargeMs, Logic.powerW(voltage, ma)
        )
    }
}
