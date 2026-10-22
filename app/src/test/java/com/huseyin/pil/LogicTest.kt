package com.huseyin.pil

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {

    @Test
    fun currentIsNormalizedToSignedMilliamps() {
        assertEquals(1500.0, Logic.normalizeCurrentMa(-1_500_000L, true)!!, 1e-9)
        assertEquals(-500.0, Logic.normalizeCurrentMa(500_000L, false)!!, 1e-9)
        assertEquals(3000.0, Logic.normalizeCurrentMa(3000L, true)!!, 1e-9)
        assertNull(Logic.normalizeCurrentMa(0L, true))
        assertNull(Logic.normalizeCurrentMa(Long.MIN_VALUE, false))
    }

    @Test
    fun estimatedFullCapacityNeedsEnoughCharge() {
        assertEquals(5000.0, Logic.estFullMah(2500.0, 50f)!!, 1e-9)
        assertNull(Logic.estFullMah(200.0, 4f))
        assertNull(Logic.estFullMah(null, 50f))
    }

    @Test
    fun healthIsClampedAndNeedsDesignCapacity() {
        assertEquals(100.0, Logic.healthPct(5200.0, 5000.0)!!, 1e-9)
        assertEquals(90.0, Logic.healthPct(4500.0, 5000.0)!!, 1e-9)
        assertNull(Logic.healthPct(null, 5000.0))
        assertNull(Logic.healthPct(4500.0, 0.0))
    }

    @Test
    fun powerIsVoltageTimesCurrent() {
        assertEquals(12.0, Logic.powerW(4000, 3000.0)!!, 1e-9)
        assertEquals(12.0, Logic.powerW(4000, -3000.0)!!, 1e-9)
        assertNull(Logic.powerW(0, 3000.0))
        assertNull(Logic.powerW(4000, null))
    }

    @Test
    fun durationFormat() {
        assertEquals("1 sa 30 dk", Logic.fmtDuration(5_400_000L))
        assertEquals("2 dk", Logic.fmtDuration(120_000L))
        assertEquals("0 dk", Logic.fmtDuration(-5L))
    }

    @Test
    fun csvHasHeaderAndTurkishNumbers() {
        val s = Session(0L, 3_600_000L, 20f, 80f, 12.5, 18.0, 38.5f, 3000.0)
        val lines = Logic.csv(listOf(s), TimeZone.getTimeZone("UTC")).trim().split("\n")
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("Başlangıç;Bitiş"))
        assertEquals("1970-01-01 00:00;1970-01-01 01:00;60;20;80;12,5;18,0;38,5;3000", lines[1])
    }

    @Test
    fun sessionJsonRoundTrip() {
        val s = Session(1000L, 9_000_000L, 12f, 88f, 10.25, 21.5, 40.5f, 3200.0)
        val back = SessionJson.fromJson(SessionJson.toJson(s))
        assertEquals(s, back)
    }

    @Test
    fun sessionTrackerAveragesPowerByTime() {
        val t = SessionTracker()
        assertNull(t.feed(0L, 40f, true, 10.0, 30f, 2000.0))
        assertNull(t.feed(30_000L, 41f, true, 20.0, 35f, 3000.0))
        assertNull(t.feed(60_000L, 42f, true, 20.0, 36f, 3000.0))
        val s = t.feed(90_000L, 43f, false, null, 36f, null)
        assertNotNull(s)
        assertEquals(0L, s!!.start)
        assertEquals(60_000L, s.end)
        assertEquals(40f, s.startPct, 0f)
        assertEquals(42f, s.endPct, 0f)
        assertEquals(20.0, s.avgW, 1e-6)
        assertEquals(20.0, s.maxW, 1e-9)
        assertEquals(36f, s.maxTemp, 0f)
        assertEquals(3000.0, s.maxMa, 1e-9)
    }

    @Test
    fun shortSessionsAreDropped() {
        val t = SessionTracker()
        t.feed(0L, 40f, true, 10.0, 30f, 2000.0)
        t.feed(10_000L, 40f, true, 10.0, 30f, 2000.0)
        assertNull(t.feed(20_000L, 40f, false, null, 30f, null))
        assertTrue(!t.isActive())
    }

    @Test
    fun unpluggedWithoutSessionDoesNothing() {
        val t = SessionTracker()
        assertNull(t.feed(0L, 50f, false, null, 30f, null))
    }

    @Test
    fun consumptionSplitsByScreenState() {
        val c = ConsumptionTracker()
        c.feed(1_000L, 3000.0, false, true)
        c.feed(61_000L, 2990.0, false, true)
        c.feed(121_000L, 2985.0, false, false)
        assertEquals(10.0, c.onMah, 1e-9)
        assertEquals(5.0, c.offMah, 1e-9)
        assertEquals(600.0, c.onRate()!!, 1e-6)
        assertEquals(300.0, c.offRate()!!, 1e-6)
    }

    @Test
    fun consumptionIgnoresChargingAndGaps() {
        val c = ConsumptionTracker()
        c.feed(1_000L, 3000.0, false, true)
        c.feed(61_000L, 2990.0, false, true)
        // şarjda: sayılmaz
        c.feed(121_000L, 3100.0, true, true)
        // şarjdan hemen sonraki ilk örnek sayılmaz
        c.feed(181_000L, 3090.0, false, true)
        // büyük boşluk sayılmaz
        c.feed(1_000_000L, 2900.0, false, true)
        assertEquals(10.0, c.onMah, 1e-9)
        assertEquals(60_000L, c.onMs)
        assertEquals(0.0, c.offMah, 1e-9)
        assertNull(c.offRate())
    }
}
