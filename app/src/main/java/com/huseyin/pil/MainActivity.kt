package com.huseyin.pil

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var bm: BatteryManager
    private val handler = Handler(Looper.getMainLooper())
    private val values = LinkedHashMap<String, TextView>()
    private lateinit var bigPercent: TextView
    private lateinit var bigStatus: TextView
    private lateinit var bar: ProgressBar

    private var designCapacity = 0.0
    private var lastPlugged: Boolean? = null
    private var sessionStartPct = 0f
    private var sessionStartTime = 0L

    private val ticker = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val manual = getSharedPreferences("pil", 0).getFloat("design", 0f).toDouble()
        designCapacity = if (manual > 0) manual else readDesignCapacity()
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    // ---------------------------------------------------------------- UI

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(0xFF0F1115.toInt()) }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)

        ViewCompat.setOnApplyWindowInsetsListener(scroll) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            content.setPadding(dp(16), b.top + dp(16), dp(16), b.bottom + dp(16))
            insets
        }

        bigPercent = TextView(this).apply {
            textSize = 64f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        bigStatus = TextView(this).apply {
            textSize = 16f
            setTextColor(0xFF9AA3B2.toInt())
            gravity = Gravity.CENTER
        }
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
        }
        content.addView(bigPercent)
        content.addView(bigStatus)
        content.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12)).apply {
            topMargin = dp(12)
        })

        section(content, "Şarj", listOf(
            "Durum", "Bağlantı", "Tahmini süre", "Akım", "Güç", "Voltaj", "Bu oturumda"
        ))
        section(content, "Pil", listOf(
            "Sıcaklık", "Teknoloji", "Anlık kapasite", "Tahmini tam kapasite"
        ))
        section(content, "Sağlık", listOf(
            "Android sağlık durumu", "Sağlık yüzdesi", "Tasarım kapasitesi", "Döngü sayısı"
        ))

        val note = TextView(this).apply {
            text = "Not: Bazı değerleri (akım, kapasite, döngü sayısı) telefon üreticisi desteklemezse \"—\" görünür. " +
                "Tasarım kapasitesi görünmüyorsa o satıra dokunup etiketteki mAh değerini elle girebilirsin."
            textSize = 12f
            setTextColor(0xFF6B7380.toInt())
            setPadding(dp(4), dp(16), dp(4), 0)
        }
        content.addView(note)
        setContentView(scroll)
    }

    private fun section(parent: LinearLayout, title: String, keys: List<String>) {
        val head = TextView(this).apply {
            text = title.uppercase()
            textSize = 12f
            setTextColor(0xFF6B7380.toInt())
            setPadding(dp(4), dp(20), 0, dp(6))
        }
        parent.addView(head)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFF1A1E26.toInt())
                cornerRadius = dp(16).toFloat()
            }
        }
        for (k in keys) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }
            val key = TextView(this).apply {
                text = k
                textSize = 15f
                setTextColor(0xFF9AA3B2.toInt())
            }
            val value = TextView(this).apply {
                text = "—"
                textSize = 15f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.END
            }
            row.addView(key, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(value, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f))
            if (k == "Tasarım kapasitesi") row.setOnClickListener { askDesignCapacity() }
            values[k] = value
            card.addView(row)
        }
        parent.addView(card)
    }

    private fun set(key: String, text: String) {
        values[key]?.text = text
    }

    private fun askDesignCapacity() {
        val et = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "örn. 5000"
        }
        AlertDialog.Builder(this)
            .setTitle("Tasarım kapasitesi (mAh)")
            .setView(et)
            .setPositiveButton("Kaydet") { _, _ ->
                val n = et.text.toString().toDoubleOrNull()
                if (n != null && n > 500) {
                    designCapacity = n
                    getSharedPreferences("pil", 0).edit().putFloat("design", n.toFloat()).apply()
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    // ---------------------------------------------------------------- Data

    private fun readDesignCapacity(): Double = try {
        val c = Class.forName("com.android.internal.os.PowerProfile")
        val o = c.getConstructor(Context::class.java).newInstance(this)
        c.getMethod("getBatteryCapacity").invoke(o) as Double
    } catch (t: Throwable) {
        0.0
    }

    private fun fmtDuration(ms: Long): String {
        val totalMin = ms / 60000
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "$h sa $m dk" else "$m dk"
    }

    private fun update() {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return

        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct = if (level >= 0 && scale > 0) level * 100f / scale else 0f
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val health = i.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val voltage = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) // mV
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        val tech = i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "—"
        val cycles = if (Build.VERSION.SDK_INT >= 34) i.getIntExtra("android.os.extra.CYCLE_COUNT", -1) else -1

        val isPlugged = plugged != 0
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING

        // Şarj oturumu takibi
        if (lastPlugged != isPlugged) {
            lastPlugged = isPlugged
            sessionStartPct = pct
            sessionStartTime = System.currentTimeMillis()
        }

        // Akım (µA veya mA gelebilir, cihaza göre)
        val rawCur = try {
            bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        } catch (e: Exception) {
            Long.MIN_VALUE
        }
        var mA: Double? = null
        if (rawCur != Long.MIN_VALUE && rawCur != 0L) {
            val a = if (abs(rawCur) >= 20000) abs(rawCur) / 1000.0 else abs(rawCur).toDouble()
            mA = if (isPlugged) a else -a
        }

        // Kapasite
        val rawCounter = try {
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        } catch (e: Exception) {
            Int.MIN_VALUE
        }
        val counterMah: Double? =
            if (rawCounter != Int.MIN_VALUE && rawCounter > 0) rawCounter / 1000.0 else null
        val estFull: Double? =
            if (counterMah != null && pct >= 5f) counterMah / (pct / 100.0) else null

        // Başlık
        bigPercent.text = "%.0f%%".format(pct)
        bar.progress = (pct * 10).toInt()
        bigStatus.text = statusText(status)

        // Şarj kartı
        set("Durum", statusText(status))
        set("Bağlantı", pluggedText(plugged))
        set("Akım", mA?.let { "%+.0f mA".format(it) } ?: "—")
        set("Güç", if (mA != null && voltage > 0) "%.2f W".format(voltage / 1000.0 * abs(mA) / 1000.0) else "—")
        set("Voltaj", if (voltage > 0) "%.3f V".format(voltage / 1000.0) else "—")

        var timeText = "—"
        if (isPlugged && status == BatteryManager.BATTERY_STATUS_FULL) {
            timeText = "Şarj tamamlandı"
        } else if (isCharging) {
            var ms = -1L
            if (Build.VERSION.SDK_INT >= 28) {
                ms = try {
                    bm.computeChargeTimeRemaining()
                } catch (e: Exception) {
                    -1L
                }
            }
            if (ms <= 0 && mA != null && mA > 50 && counterMah != null && estFull != null) {
                ms = ((estFull - counterMah) / mA * 3600000).toLong()
            }
            if (ms > 0) timeText = "Dolmasına ~" + fmtDuration(ms)
        } else if (!isPlugged && mA != null && mA < -20 && counterMah != null) {
            val ms = (counterMah / abs(mA) * 3600000).toLong()
            timeText = "Bitmesine ~" + fmtDuration(ms) + " (anlık akımla)"
        }
        set("Tahmini süre", timeText)

        val elapsed = System.currentTimeMillis() - sessionStartTime
        val diff = pct - sessionStartPct
        set("Bu oturumda", "%+.0f%% / %s".format(diff, fmtDuration(elapsed)))

        // Pil kartı
        set("Sıcaklık", "%.1f °C".format(temp))
        values["Sıcaklık"]?.setTextColor(if (temp >= 40f) 0xFFFF9F43.toInt() else Color.WHITE)
        set("Teknoloji", tech)
        set("Anlık kapasite", counterMah?.let { "%.0f mAh".format(it) } ?: "—")
        set("Tahmini tam kapasite", estFull?.let { "%.0f mAh".format(it) } ?: "—")

        // Sağlık kartı
        set("Android sağlık durumu", healthText(health))
        set("Tasarım kapasitesi", if (designCapacity > 0) "%.0f mAh".format(designCapacity) else "Dokun ve gir")

        var healthPct: Int? = null
        if (Build.VERSION.SDK_INT >= 34) {
            // BATTERY_PROPERTY_STATE_OF_HEALTH: bazı telefonlar BATTERY_STATS izni ister
            val soh = try {
                bm.getIntProperty(10)
            } catch (e: Exception) {
                -1
            }
            if (soh in 1..100) healthPct = soh
        }
        if (healthPct != null) {
            set("Sağlık yüzdesi", "$healthPct%")
        } else if (estFull != null && designCapacity > 0) {
            val p = (estFull / designCapacity * 100).coerceAtMost(100.0)
            set("Sağlık yüzdesi", "~%.0f%% (tahmini)".format(p))
        } else {
            set("Sağlık yüzdesi", "—")
        }
        set("Döngü sayısı", if (cycles >= 0) "$cycles" else "—")
    }

    private fun statusText(s: Int) = when (s) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Şarj oluyor"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Şarjda değil"
        BatteryManager.BATTERY_STATUS_FULL -> "Dolu"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Şarj olmuyor"
        else -> "Bilinmiyor"
    }

    private fun pluggedText(p: Int) = when (p) {
        BatteryManager.BATTERY_PLUGGED_AC -> "Adaptör (AC)"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Kablosuz"
        8 -> "Dock"
        else -> "Takılı değil"
    }

    private fun healthText(h: Int) = when (h) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "İyi"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Aşırı ısınmış"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Bozuk"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Aşırı voltaj"
        BatteryManager.BATTERY_HEALTH_COLD -> "Çok soğuk"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Bilinmeyen hata"
        else -> "Bilinmiyor"
    }
}
