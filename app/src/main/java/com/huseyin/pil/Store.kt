package com.huseyin.pil

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Telefonun kendi dosyalarına yazar: şarj oturumları ve günlük kapasite kaydı. */
class Store(private val ctx: Context) {

    companion object {
        private val lock = Any()
        const val MAX_SESSIONS = 300
    }

    val prefs: SharedPreferences = ctx.applicationContext.getSharedPreferences("pil", Context.MODE_PRIVATE)

    private fun file(name: String) = File(ctx.applicationContext.filesDir, name)

    private fun read(f: File, empty: String): String = try {
        if (f.exists()) f.readText() else empty
    } catch (e: Exception) {
        empty
    }

    private fun write(f: File, text: String) {
        val tmp = File(f.path + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) {
            f.writeText(text)
            tmp.delete()
        }
    }

    // ---- şarj oturumları

    fun loadSessions(): List<Session> = synchronized(lock) {
        try {
            val a = JSONArray(read(file("sessions.json"), "[]"))
            (0 until a.length()).map { SessionJson.fromJson(a.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addSession(s: Session) {
        val list = (loadSessions() + s).takeLast(MAX_SESSIONS)
        synchronized(lock) {
            val a = JSONArray()
            list.forEach { a.put(SessionJson.toJson(it)) }
            write(file("sessions.json"), a.toString())
        }
    }

    fun clearSessions() = synchronized(lock) {
        file("sessions.json").delete()
        file("health.json").delete()
    }

    // ---- günlük tahmini tam kapasite

    fun loadHealth(): List<Pair<String, Double>> = synchronized(lock) {
        try {
            val o = JSONObject(read(file("health.json"), "{}"))
            o.keys().asSequence().map { it to o.getDouble(it) }.sortedBy { it.first }.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun noteHealth(day: String, mah: Double) = synchronized(lock) {
        val o = try {
            JSONObject(read(file("health.json"), "{}"))
        } catch (e: Exception) {
            JSONObject()
        }
        o.put(day, mah)
        // en fazla 400 gün tut
        val keys = o.keys().asSequence().toList().sorted()
        if (keys.size > 400) keys.take(keys.size - 400).forEach { o.remove(it) }
        write(file("health.json"), o.toString())
    }

    // ---- tasarım kapasitesi (elle girilen ya da sistemden okunan)

    fun designCapacity(): Double {
        val manual = prefs.getFloat("design", 0f).toDouble()
        if (manual > 0) return manual
        return try {
            val c = Class.forName("com.android.internal.os.PowerProfile")
            val o = c.getConstructor(Context::class.java).newInstance(ctx.applicationContext)
            c.getMethod("getBatteryCapacity").invoke(o) as Double
        } catch (t: Throwable) {
            0.0
        }
    }
}

fun ConsumptionTracker.load(p: SharedPreferences) {
    onMah = p.getString("c_onMah", "0")?.toDoubleOrNull() ?: 0.0
    onMs = p.getLong("c_onMs", 0L)
    offMah = p.getString("c_offMah", "0")?.toDoubleOrNull() ?: 0.0
    offMs = p.getLong("c_offMs", 0L)
}

fun ConsumptionTracker.save(p: SharedPreferences) {
    p.edit()
        .putString("c_onMah", onMah.toString())
        .putLong("c_onMs", onMs)
        .putString("c_offMah", offMah.toString())
        .putLong("c_offMs", offMs)
        .apply()
}

/** Uygulama ve arka plan servisi aynı sayaçları besler. */
object Tracker {
    private val sessions = SessionTracker()
    val cons = ConsumptionTracker()
    private var loaded = false
    private var lastTs = 0L
    private var lastSave = 0L
    private var lastHealthSave = 0L

    @Synchronized
    fun ensureLoaded(ctx: Context) {
        if (!loaded) {
            loaded = true
            cons.load(Store(ctx).prefs)
        }
    }

    @Synchronized
    fun resetConsumption(ctx: Context) {
        ensureLoaded(ctx)
        cons.reset()
        cons.save(Store(ctx).prefs)
    }

    @Synchronized
    fun feed(ctx: Context, s: Snap, screenOn: Boolean) {
        ensureLoaded(ctx)
        if (s.ts <= lastTs) return
        lastTs = s.ts
        val store = Store(ctx)

        sessions.feed(s.ts, s.pct, s.isPlugged, s.powerW, s.tempC, s.ma)?.let { store.addSession(it) }
        cons.feed(s.ts, s.counterMah, s.isPlugged, screenOn)

        val est = s.estFullMah
        if (est != null && s.pct in 20f..95f && s.ts - lastHealthSave > 600_000L) {
            lastHealthSave = s.ts
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(s.ts))
            store.noteHealth(day, est)
        }

        Alerts.check(ctx, s, store.prefs)

        if (s.ts - lastSave > 30_000L) {
            lastSave = s.ts
            cons.save(store.prefs)
        }
    }
}
