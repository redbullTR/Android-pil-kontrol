package com.huseyin.pil

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/** Android'e bağımlı olmayan, test edilebilir hesaplamalar. */
object Logic {
    private val TR = Locale("tr", "TR")

    /** Ham akım değeri (µA ya da mA gelebilir) -> işaretli mA. Şarjdayken +, değilken -. */
    fun normalizeCurrentMa(raw: Long, plugged: Boolean): Double? {
        if (raw == Long.MIN_VALUE || raw == 0L) return null
        val a = if (abs(raw) >= 20000) abs(raw) / 1000.0 else abs(raw).toDouble()
        return if (plugged) a else -a
    }

    fun estFullMah(counterMah: Double?, pct: Float): Double? =
        if (counterMah != null && pct >= 5f) counterMah / (pct / 100.0) else null

    fun healthPct(estFull: Double?, design: Double): Double? =
        if (estFull != null && design > 0) (estFull / design * 100).coerceAtMost(100.0) else null

    fun powerW(voltageMv: Int, ma: Double?): Double? =
        if (ma != null && voltageMv > 0) voltageMv / 1000.0 * abs(ma) / 1000.0 else null

    fun fmtDuration(ms: Long): String {
        val totalMin = (ms / 60000).coerceAtLeast(0)
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "$h sa $m dk" else "$m dk"
    }

    /** Şarj oturumlarını Excel'in Türkçe ayarlarında açılacak biçimde CSV yapar. */
    fun csv(list: List<Session>, tz: TimeZone = TimeZone.getDefault()): String {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = tz }
        val sb = StringBuilder()
        sb.append("Başlangıç;Bitiş;Süre (dk);Başlangıç %;Bitiş %;Ort. güç (W);Maks. güç (W);Maks. sıcaklık (°C);Maks. akım (mA)\n")
        for (s in list) {
            sb.append(df.format(Date(s.start))).append(';')
            sb.append(df.format(Date(s.end))).append(';')
            sb.append(
                String.format(
                    TR, "%d;%.0f;%.0f;%.1f;%.1f;%.1f;%.0f",
                    (s.end - s.start) / 60000, s.startPct, s.endPct,
                    s.avgW, s.maxW, s.maxTemp, s.maxMa
                )
            )
            sb.append('\n')
        }
        return sb.toString()
    }
}
