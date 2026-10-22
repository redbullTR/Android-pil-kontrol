package com.huseyin.pil

import android.graphics.Color

/** Açık ve koyu tema renkleri. */
class Pal(val dark: Boolean) {
    val bg: Int = if (dark) 0xFF0F1115.toInt() else 0xFFF1F3F6.toInt()
    val card: Int = if (dark) 0xFF1A1E26.toInt() else 0xFFFFFFFF.toInt()
    val text: Int = if (dark) Color.WHITE else 0xFF111318.toInt()
    val sub: Int = if (dark) 0xFF9AA3B2.toInt() else 0xFF5B6472.toInt()
    val muted: Int = if (dark) 0xFF6B7380.toInt() else 0xFF8A93A3.toInt()
    val accent: Int = if (dark) 0xFF3DDC84.toInt() else 0xFF129C55.toInt()
    val warn: Int = 0xFFFF9F43.toInt()
    val blue: Int = if (dark) 0xFF5AA9FF.toInt() else 0xFF1F6FD6.toInt()
    val red: Int = if (dark) 0xFFFF6B6B.toInt() else 0xFFD63B3B.toInt()
}

/** Canlı grafikler için son ölçümleri tutan halka tampon. */
class Series(private val cap: Int) {
    private val buf = FloatArray(cap)
    private var n = 0
    private var start = 0

    @Synchronized
    fun add(v: Float) {
        if (n < cap) {
            buf[(start + n) % cap] = v
            n++
        } else {
            buf[start] = v
            start = (start + 1) % cap
        }
    }

    @Synchronized
    fun toArray(): FloatArray {
        val out = FloatArray(n)
        for (i in 0 until n) out[i] = buf[(start + i) % cap]
        return out
    }
}

/** Etkinlik yeniden oluşturulsa bile korunan canlı durum. */
object Live {
    val ma = Series(600)
    val w = Series(600)
    val temp = Series(600)
    val pct = Series(600)
    var tab = 0
    var lastPlugged: Boolean? = null
    var sessionStartPct = 0f
    var sessionStartTime = 0L
}
