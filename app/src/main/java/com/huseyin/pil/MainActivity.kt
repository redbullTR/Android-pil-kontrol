package com.huseyin.pil

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var pal: Pal
    private lateinit var host: FrameLayout
    private val handler = Handler(Looper.getMainLooper())
    private val tabViews = ArrayList<TextView>()

    // Canlı sekme öğeleri
    private val values = HashMap<String, TextView>()
    private var bigPercent: TextView? = null
    private var bigStatus: TextView? = null
    private var bar: ProgressBar? = null
    private var chMa: ChartView? = null
    private var chW: ChartView? = null
    private var chTemp: ChartView? = null
    private var chPct: ChartView? = null

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val createCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            try {
                val text = "﻿" + Logic.csv(store.loadSessions())
                contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                toast("Dosya kaydedildi")
            } catch (e: Exception) {
                toast("Dosya kaydedilemedi")
            }
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            try {
                tick()
            } catch (e: Exception) {
                // bir okuma başarısız olsa da uygulama çalışmaya devam etsin
            }
            handler.postDelayed(this, 1000L)
        }
    }

    // ------------------------------------------------------------ yaşam döngüsü

    override fun onCreate(savedInstanceState: Bundle?) {
        store = Store(this)
        AppCompatDelegate.setDefaultNightMode(
            when (store.prefs.getInt("theme", 0)) {
                1 -> AppCompatDelegate.MODE_NIGHT_YES
                2 -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
        super.onCreate(savedInstanceState)

        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        pal = Pal(night)

        if (store.prefs.getBoolean("keepOn", true)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        window.setBackgroundDrawable(ColorDrawable(pal.bg))
        Tracker.ensureLoaded(this)
        buildRoot()
        selectTab(Live.tab)
    }

    override fun onResume() {
        super.onResume()
        if (store.prefs.getBoolean("monitor", false)) startMonitor()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
        PilWidget.updateNow(this)
    }

    // ------------------------------------------------------------ yardımcılar

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    private fun cardBg(): GradientDrawable = GradientDrawable().apply {
        setColor(pal.card)
        cornerRadius = dp(16).toFloat()
    }

    private fun newCard(parent: LinearLayout): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg()
        }
        parent.addView(card, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return card
    }

    private fun sectionTitle(parent: LinearLayout, title: String) {
        val t = TextView(this).apply {
            text = title.uppercase(Locale("tr", "TR"))
            textSize = 12f
            setTextColor(pal.muted)
            setPadding(dp(4), dp(20), 0, dp(6))
        }
        parent.addView(t)
    }

    private fun rowView(card: LinearLayout, key: String, value: String, onClick: (() -> Unit)?): TextView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val k = TextView(this).apply {
            text = key
            textSize = 15f
            setTextColor(pal.sub)
        }
        val v = TextView(this).apply {
            text = value
            textSize = 15f
            setTextColor(pal.text)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.END
        }
        row.addView(k, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(v, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f))
        if (onClick != null) row.setOnClickListener { onClick() }
        card.addView(row)
        return v
    }

    private fun switchRow(card: LinearLayout, title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        val k = TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(pal.text)
        }
        val sw = SwitchCompat(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, c -> onChange(c) }
        }
        row.addView(k, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(sw, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.addView(row)
    }

    private fun button(parent: LinearLayout, text: String, danger: Boolean, onClick: () -> Unit) {
        val b = TextView(this).apply {
            this.text = text
            textSize = 15f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (danger) pal.red else pal.accent)
            background = cardBg()
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener { onClick() }
        }
        val params = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.topMargin = dp(12)
        parent.addView(b, params)
    }

    private fun page(build: (LinearLayout) -> Unit): View {
        val sv = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        build(col)
        sv.addView(col)
        return sv
    }

    private fun newChart(parent: LinearLayout, title: String, color: Int): ChartView {
        val c = ChartView(this, pal, title, color)
        val p = lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(140))
        p.topMargin = dp(10)
        parent.addView(c, p)
        return c
    }

    private fun startMonitor() {
        try {
            ContextCompat.startForegroundService(this, Intent(this, MonitorService::class.java))
        } catch (e: Exception) {
            toast("Arka plan izleme başlatılamadı")
        }
    }

    private fun askNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ------------------------------------------------------------ iskelet ve sekmeler

    private fun buildRoot() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pal.bg)
        }
        host = FrameLayout(this)
        root.addView(host, lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(pal.card)
        }
        tabViews.clear()
        listOf("Canlı", "Geçmiş", "Ayarlar").forEachIndexed { i, name ->
            val t = TextView(this).apply {
                text = name
                textSize = 15f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(14), 0, dp(14))
                setOnClickListener { selectTab(i) }
            }
            tabViews.add(t)
            tabBar.addView(t, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(tabBar, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            host.setPadding(0, b.top, 0, 0)
            tabBar.setPadding(0, 0, 0, b.bottom)
            insets
        }
        setContentView(root)
        val ctl = WindowCompat.getInsetsController(window, root)
        ctl.isAppearanceLightStatusBars = !pal.dark
        ctl.isAppearanceLightNavigationBars = !pal.dark
    }

    private fun selectTab(i: Int) {
        Live.tab = i
        host.removeAllViews()
        values.clear()
        bigPercent = null
        bigStatus = null
        bar = null
        chMa = null
        chW = null
        chTemp = null
        chPct = null
        val view = when (i) {
            1 -> buildHistory()
            2 -> buildSettings()
            else -> buildLive()
        }
        host.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        tabViews.forEachIndexed { idx, t -> t.setTextColor(if (idx == i) pal.accent else pal.muted) }
        if (i == 0) {
            BatteryReader.read(this)?.let { renderLive(it) }
        }
    }

    // ------------------------------------------------------------ Canlı sekmesi

    private fun buildLive(): View = page { col ->
        bigPercent = TextView(this).apply {
            textSize = 64f
            setTextColor(pal.text)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        bigStatus = TextView(this).apply {
            textSize = 16f
            setTextColor(pal.sub)
            gravity = Gravity.CENTER
        }
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(pal.accent)
            progressBackgroundTintList = ColorStateList.valueOf(pal.muted)
        }
        col.addView(bigPercent)
        col.addView(bigStatus)
        val bp = lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(12))
        bp.topMargin = dp(12)
        col.addView(bar, bp)

        rows(col, "Şarj", listOf("Durum", "Bağlantı", "Tahmini süre", "Akım", "Güç", "Voltaj", "Bu oturumda"))

        sectionTitle(col, "Canlı grafikler (son 10 dk, uygulama açıkken)")
        chMa = newChart(col, "Akım (mA)", pal.accent)
        chW = newChart(col, "Güç (W)", pal.blue)
        chTemp = newChart(col, "Sıcaklık (°C)", pal.warn)
        chPct = newChart(col, "Pil yüzdesi (%)", pal.red)

        rows(col, "Pil", listOf("Sıcaklık", "Teknoloji", "Anlık kapasite", "Tahmini tam kapasite"))
        rows(col, "Sağlık", listOf("Android sağlık durumu", "Sağlık yüzdesi", "Tasarım kapasitesi", "Döngü sayısı"))

        val note = TextView(this).apply {
            text = "Bazı değerleri telefon üreticisi vermiyorsa \"—\" görünür. " +
                "Tasarım kapasitesi görünmüyorsa o satıra dokunup etiketteki mAh değerini girebilirsin."
            textSize = 12f
            setTextColor(pal.muted)
            setPadding(dp(4), dp(16), dp(4), 0)
        }
        col.addView(note)
    }

    private fun rows(col: LinearLayout, title: String, keys: List<String>) {
        sectionTitle(col, title)
        val card = newCard(col)
        for (k in keys) {
            val click: (() -> Unit)? = if (k == "Tasarım kapasitesi") ({ askDesign() }) else null
            values[k] = rowView(card, k, "—", click)
        }
    }

    private fun setV(key: String, text: String) {
        values[key]?.text = text
    }

    private fun tick() {
        val s = BatteryReader.read(this) ?: return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        Tracker.feed(this, s, pm.isInteractive)

        Live.ma.add(s.ma?.toFloat() ?: Float.NaN)
        Live.w.add(s.powerW?.toFloat() ?: Float.NaN)
        Live.temp.add(s.tempC)
        Live.pct.add(s.pct)

        if (Live.lastPlugged != s.isPlugged) {
            Live.lastPlugged = s.isPlugged
            Live.sessionStartPct = s.pct
            Live.sessionStartTime = System.currentTimeMillis()
        }
        if (Live.tab == 0) renderLive(s)
    }

    private fun renderLive(s: Snap) {
        val design = store.designCapacity()
        bigPercent?.text = String.format("%%%.0f", s.pct)
        bigStatus?.text = statusText(s.status)
        bar?.progress = (s.pct * 10).toInt()

        setV("Durum", statusText(s.status))
        setV("Bağlantı", pluggedText(s.plugged))
        val ma = s.ma
        setV("Akım", if (ma != null) String.format("%+.0f mA", ma) else "—")
        val w = s.powerW
        setV("Güç", if (w != null) String.format("%.2f W", w) else "—")
        setV("Voltaj", if (s.voltageMv > 0) String.format("%.3f V", s.voltageMv / 1000.0) else "—")

        var timeText = "—"
        val counter = s.counterMah
        val est = s.estFullMah
        if (s.isPlugged && s.status == BatteryManager.BATTERY_STATUS_FULL) {
            timeText = "Şarj tamamlandı"
        } else if (s.isCharging) {
            var ms = s.chargeMs
            if (ms <= 0 && ma != null && ma > 50 && counter != null && est != null) {
                ms = ((est - counter) / ma * 3_600_000).toLong()
            }
            if (ms > 0) timeText = "Dolmasına ~" + Logic.fmtDuration(ms)
        } else if (!s.isPlugged && ma != null && ma < -20 && counter != null) {
            timeText = "Bitmesine ~" + Logic.fmtDuration((counter / abs(ma) * 3_600_000).toLong()) + " (anlık akımla)"
        }
        setV("Tahmini süre", timeText)

        val elapsed = System.currentTimeMillis() - Live.sessionStartTime
        setV("Bu oturumda", String.format("%+.0f%% / %s", s.pct - Live.sessionStartPct, Logic.fmtDuration(elapsed)))

        setV("Sıcaklık", String.format("%.1f °C", s.tempC))
        values["Sıcaklık"]?.setTextColor(if (s.tempC >= Alerts.HOT_C - 2f) pal.warn else pal.text)
        setV("Teknoloji", s.tech)
        setV("Anlık kapasite", if (counter != null) String.format("%.0f mAh", counter) else "—")
        setV("Tahmini tam kapasite", if (est != null) String.format("%.0f mAh", est) else "—")

        setV("Android sağlık durumu", healthText(s.health))
        setV("Tasarım kapasitesi", if (design > 0) String.format("%.0f mAh", design) else "Dokun ve gir")
        val soh = s.soh
        val hp = Logic.healthPct(est, design)
        setV(
            "Sağlık yüzdesi",
            when {
                soh != null -> "$soh%"
                hp != null -> String.format("~%.0f%% (tahmini)", hp)
                else -> "—"
            }
        )
        setV("Döngü sayısı", if (s.cycles >= 0) s.cycles.toString() else "—")

        chMa?.update(Live.ma.toArray(), if (ma != null) String.format("%+.0f mA", ma) else "—", 0)
        chW?.update(Live.w.toArray(), if (w != null) String.format("%.1f W", w) else "—", 1)
        chTemp?.update(Live.temp.toArray(), String.format("%.1f °C", s.tempC), 1)
        chPct?.update(Live.pct.toArray(), String.format("%%%.0f", s.pct), 0)
    }

    private fun askDesign() {
        val et = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "örn. 5000"
        }
        AlertDialog.Builder(this)
            .setTitle("Tasarım kapasitesi (mAh)")
            .setView(et)
            .setPositiveButton("Kaydet") { _, _ ->
                val n = et.text.toString().toFloatOrNull()
                if (n != null && n > 500f) {
                    store.prefs.edit().putFloat("design", n).apply()
                    if (Live.tab == 2) selectTab(2)
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    // ------------------------------------------------------------ Geçmiş sekmesi

    private fun buildHistory(): View = page { col ->
        val dateFmt = SimpleDateFormat("d MMM HH:mm", Locale("tr", "TR"))
        val hourFmt = SimpleDateFormat("HH:mm", Locale("tr", "TR"))
        val sessions = store.loadSessions().sortedByDescending { it.start }

        sectionTitle(col, "Şarj geçmişi")
        if (sessions.isEmpty()) {
            val card = newCard(col)
            val t = TextView(this).apply {
                text = "Henüz kayıt yok. Şarja takıp çıkardığında oturum otomatik kaydedilir. " +
                    "Uygulama kapalıyken de kayıt için Ayarlar'dan arka plan izlemeyi aç."
                textSize = 14f
                setTextColor(pal.sub)
                setPadding(dp(16), dp(14), dp(16), dp(14))
            }
            card.addView(t)
        } else {
            for (s in sessions.take(40)) {
                val card = newCard(col)
                val p = card.layoutParams as LinearLayout.LayoutParams
                p.bottomMargin = dp(8)
                val l1 = TextView(this).apply {
                    text = dateFmt.format(Date(s.start)) + " → " + hourFmt.format(Date(s.end)) +
                        " · " + Logic.fmtDuration(s.end - s.start)
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(pal.text)
                    setPadding(dp(16), dp(12), dp(16), 0)
                }
                val l2 = TextView(this).apply {
                    text = String.format(
                        "%%%.0f → %%%.0f (%+.0f)  ·  ort. %.1f W  ·  maks. %.1f W  ·  maks. %.1f °C",
                        s.startPct, s.endPct, s.endPct - s.startPct, s.avgW, s.maxW, s.maxTemp
                    )
                    textSize = 13f
                    setTextColor(pal.sub)
                    setPadding(dp(16), dp(4), dp(16), dp(12))
                }
                card.addView(l1)
                card.addView(l2)
            }
        }

        sectionTitle(col, "Pil sağlığı eğilimi")
        val health = store.loadHealth()
        val hc = ChartView(this, pal, "Tahmini tam kapasite (günlük, mAh)", pal.accent)
        val hp = lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(140))
        col.addView(hc, hp)
        if (health.size >= 2) {
            hc.update(
                FloatArray(health.size) { health[it].second.toFloat() },
                String.format("%.0f mAh", health.last().second), 0
            )
        } else {
            hc.update(FloatArray(0), "—", 0)
        }
        val ht = TextView(this).apply {
            text = "Her gün %20–95 arasında bir ölçüm kaydedilir. Eğilim birkaç hafta sonra anlamlı olur."
            textSize = 12f
            setTextColor(pal.muted)
            setPadding(dp(4), dp(8), dp(4), 0)
        }
        col.addView(ht)

        sectionTitle(col, "Tüketim (şarjda değilken)")
        val cc = newCard(col)
        val on = Tracker.cons.onRate()
        val off = Tracker.cons.offRate()
        rowView(
            cc, "Ekran açık",
            if (on != null) String.format("%.0f mAh/sa", on) else "ölçülüyor…", null
        )
        rowView(
            cc, "Ekran kapalı",
            if (off != null) String.format("%.0f mAh/sa", off) else "ölçülüyor…", null
        )
        rowView(
            cc, "Ölçülen süre",
            Logic.fmtDuration(Tracker.cons.onMs) + " açık · " + Logic.fmtDuration(Tracker.cons.offMs) + " kapalı", null
        )
        val ct = TextView(this).apply {
            text = "Ekran kapalı ölçümü için arka plan izleme açık olmalı."
            textSize = 12f
            setTextColor(pal.muted)
            setPadding(dp(4), dp(8), dp(4), 0)
        }
        col.addView(ct)

        button(col, "CSV olarak dışa aktar", false) {
            if (store.loadSessions().isEmpty()) toast("Dışa aktarılacak kayıt yok") else createCsv.launch("pil-sarj-gecmisi.csv")
        }
        button(col, "Geçmişi temizle", true) {
            AlertDialog.Builder(this)
                .setTitle("Geçmişi temizle")
                .setMessage("Tüm şarj kayıtları ve sağlık eğilimi silinecek.")
                .setPositiveButton("Sil") { _, _ ->
                    store.clearSessions()
                    selectTab(1)
                }
                .setNegativeButton("İptal", null)
                .show()
        }
        button(col, "Tüketim sayaçlarını sıfırla", true) {
            Tracker.resetConsumption(this)
            selectTab(1)
        }
    }

    // ------------------------------------------------------------ Ayarlar sekmesi

    private fun buildSettings(): View = page { col ->
        sectionTitle(col, "Arka plan")
        val c1 = newCard(col)
        switchRow(c1, "Arka planda izle (kayıt ve uyarılar)", store.prefs.getBoolean("monitor", false)) { on ->
            store.prefs.edit().putBoolean("monitor", on).apply()
            if (on) {
                askNotifPermission()
                startMonitor()
            } else {
                stopService(Intent(this, MonitorService::class.java))
            }
        }
        val note = TextView(this).apply {
            text = "Açıkken bildirim çubuğunda küçük bir bildirim görünür. Xiaomi gibi telefonlarda uygulamanın " +
                "pil tasarrufunu \"Kısıtlama yok\" yapmak ve son uygulamalarda kilitlemek gerekebilir."
            textSize = 12f
            setTextColor(pal.muted)
            setPadding(dp(4), dp(8), dp(4), 0)
        }
        col.addView(note)

        sectionTitle(col, "Uyarılar")
        val c2 = newCard(col)
        val levels = intArrayOf(0, 80, 85, 90, 100)
        fun levelLabel(v: Int) = if (v == 0) "Kapalı" else if (v >= 100) "Şarj dolunca" else "%$v"
        val levelValue = rowView(c2, "Şarj uyarısı", levelLabel(store.prefs.getInt("alertPct", 0)), null)
        (levelValue.parent as View).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Şarj uyarısı")
                .setItems(levels.map { levelLabel(it) }.toTypedArray()) { _, which ->
                    store.prefs.edit().putInt("alertPct", levels[which]).apply()
                    levelValue.text = levelLabel(levels[which])
                    if (levels[which] > 0) askNotifPermission()
                }
                .show()
        }
        switchRow(c2, "Sıcaklık uyarısı (42 °C ve üstü)", store.prefs.getBoolean("alertTemp", false)) { on ->
            store.prefs.edit().putBoolean("alertTemp", on).apply()
            if (on) askNotifPermission()
        }

        sectionTitle(col, "Görünüm")
        val c3 = newCard(col)
        val themeNames = arrayOf("Sistem", "Koyu", "Açık")
        val themeValue = rowView(c3, "Tema", themeNames[store.prefs.getInt("theme", 0).coerceIn(0, 2)], null)
        (themeValue.parent as View).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Tema")
                .setItems(themeNames) { _, which ->
                    store.prefs.edit().putInt("theme", which).apply()
                    AppCompatDelegate.setDefaultNightMode(
                        when (which) {
                            1 -> AppCompatDelegate.MODE_NIGHT_YES
                            2 -> AppCompatDelegate.MODE_NIGHT_NO
                            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        }
                    )
                    recreate()
                }
                .show()
        }
        switchRow(c3, "Uygulama açıkken ekranı açık tut", store.prefs.getBoolean("keepOn", true)) { on ->
            store.prefs.edit().putBoolean("keepOn", on).apply()
            if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        sectionTitle(col, "Pil")
        val c4 = newCard(col)
        val design = store.designCapacity()
        rowView(c4, "Tasarım kapasitesi", if (design > 0) String.format("%.0f mAh", design) else "Dokun ve gir") { askDesign() }

        sectionTitle(col, "Hakkında")
        val c5 = newCard(col)
        rowView(c5, "Sürüm", "1.4", null)
        rowView(c5, "Veriler", "Sadece telefonda; internet izni yok", null)
    }

    // ------------------------------------------------------------ metinler

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
