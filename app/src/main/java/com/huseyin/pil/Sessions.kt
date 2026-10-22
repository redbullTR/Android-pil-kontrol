package com.huseyin.pil

import org.json.JSONObject

/** Bir şarj oturumunun özeti. */
data class Session(
    val start: Long,
    val end: Long,
    val startPct: Float,
    val endPct: Float,
    val avgW: Double,
    val maxW: Double,
    val maxTemp: Float,
    val maxMa: Double
)

/**
 * Şarj oturumlarını izler: fiş takılınca başlar, çıkarılınca biter.
 * Çok kısa (1 dakikadan az) oturumlar kaydedilmez.
 */
class SessionTracker {
    companion object {
        const val MIN_MS = 60_000L
        const val MAX_GAP_MS = 60_000L
    }

    private var active = false
    private var start = 0L
    private var lastTs = 0L
    private var startPct = 0f
    private var lastPct = 0f
    private var wSum = 0.0
    private var wTime = 0L
    private var maxW = 0.0
    private var maxTemp = 0f
    private var maxMa = 0.0

    fun isActive() = active

    /** Oturum bittiyse özetini döndürür, yoksa null. */
    fun feed(ts: Long, pct: Float, plugged: Boolean, powerW: Double?, tempC: Float, ma: Double?): Session? {
        if (plugged) {
            if (!active) {
                active = true
                start = ts
                startPct = pct
                wSum = 0.0
                wTime = 0L
                maxW = powerW ?: 0.0
                maxTemp = tempC
                maxMa = ma ?: 0.0
            } else {
                val dt = (ts - lastTs).coerceIn(0L, MAX_GAP_MS)
                if (powerW != null) {
                    wSum += powerW * dt
                    wTime += dt
                    if (powerW > maxW) maxW = powerW
                }
                if (tempC > maxTemp) maxTemp = tempC
                if (ma != null && ma > maxMa) maxMa = ma
            }
            lastTs = ts
            lastPct = pct
            return null
        }
        if (!active) return null
        active = false
        val dur = lastTs - start
        if (dur < MIN_MS) return null
        val avg = if (wTime > 0) wSum / wTime else 0.0
        return Session(start, lastTs, startPct, lastPct, avg, maxW, maxTemp, maxMa)
    }
}

/**
 * Şarjda değilken ekran açık/kapalı tüketimini (mAh) ölçer.
 * Şarj sayacındaki düşüşü ekran durumuna göre iki kovaya ekler.
 */
class ConsumptionTracker {
    var onMah = 0.0
    var onMs = 0L
    var offMah = 0.0
    var offMs = 0L

    private var lastTs = 0L
    private var lastCounter: Double? = null
    private var lastPlugged = true

    fun feed(ts: Long, counterMah: Double?, plugged: Boolean, screenOn: Boolean) {
        val prevTs = lastTs
        val prev = lastCounter
        val prevPlugged = lastPlugged
        lastTs = ts
        lastCounter = counterMah
        lastPlugged = plugged
        if (plugged || prevPlugged || counterMah == null || prev == null || prevTs == 0L) return
        val dt = ts - prevTs
        if (dt <= 0 || dt > 120_000) return
        val drop = prev - counterMah
        if (drop < 0) return
        if (screenOn) {
            onMah += drop
            onMs += dt
        } else {
            offMah += drop
            offMs += dt
        }
    }

    /** Ekran açıkken saatlik mAh tüketimi (en az 1 dk ölçüm gerekir). */
    fun onRate(): Double? = if (onMs >= 60_000) onMah / (onMs / 3_600_000.0) else null

    fun offRate(): Double? = if (offMs >= 60_000) offMah / (offMs / 3_600_000.0) else null

    fun reset() {
        onMah = 0.0
        onMs = 0L
        offMah = 0.0
        offMs = 0L
    }
}

object SessionJson {
    fun toJson(s: Session): JSONObject = JSONObject()
        .put("s", s.start)
        .put("e", s.end)
        .put("sp", s.startPct.toDouble())
        .put("ep", s.endPct.toDouble())
        .put("aw", s.avgW)
        .put("mw", s.maxW)
        .put("mt", s.maxTemp.toDouble())
        .put("mm", s.maxMa)

    fun fromJson(o: JSONObject): Session = Session(
        o.getLong("s"),
        o.getLong("e"),
        o.getDouble("sp").toFloat(),
        o.getDouble("ep").toFloat(),
        o.getDouble("aw"),
        o.getDouble("mw"),
        o.getDouble("mt").toFloat(),
        o.getDouble("mm")
    )
}
