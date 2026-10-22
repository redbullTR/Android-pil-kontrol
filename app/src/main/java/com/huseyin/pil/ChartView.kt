package com.huseyin.pil

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/** Basit çizgi grafik. NaN değerler çizgide boşluk bırakır. */
class ChartView(
    ctx: Context,
    private val pal: Pal,
    private val title: String,
    private val lineColor: Int
) : View(ctx) {

    private val d = resources.displayMetrics.density
    private var values = FloatArray(0)
    private var valueText = "—"
    private var decimals = 1

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.card }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
        color = lineColor
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = lineColor
        alpha = 40
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pal.sub
        textSize = 13f * d
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pal.text
        textSize = 15f * d
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.RIGHT
    }
    private val footPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pal.muted
        textSize = 11f * d
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pal.muted
        alpha = 50
        strokeWidth = 1f
    }

    fun update(v: FloatArray, text: String, decimalPlaces: Int = 1) {
        values = v
        valueText = text
        decimals = decimalPlaces
        invalidate()
    }

    private fun fmt(x: Float): String = String.format("%." + decimals + "f", x)

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = 12f * d
        c.drawRoundRect(RectF(0f, 0f, w, h), 16f * d, 16f * d, bgPaint)
        c.drawText(title, pad, pad + 13f * d, titlePaint)
        c.drawText(valueText, w - pad, pad + 14f * d, valuePaint)

        val left = pad
        val right = w - pad
        val top = 40f * d
        val bottom = h - 24f * d

        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        var any = false
        for (v in values) {
            if (v.isNaN()) continue
            any = true
            if (v < min) min = v
            if (v > max) max = v
        }
        if (!any) {
            c.drawText("veri yok", left, (top + bottom) / 2f, footPaint)
            return
        }
        val shownMin = min
        val shownMax = max
        if (max - min < 1e-6f) {
            min -= 1f
            max += 1f
        }
        c.drawLine(left, bottom, right, bottom, gridPaint)

        val n = values.size
        val step = if (n > 1) (right - left) / (n - 1) else 0f
        var i = 0
        while (i < n) {
            if (values[i].isNaN()) {
                i++
                continue
            }
            var j = i
            while (j + 1 < n && !values[j + 1].isNaN()) j++
            val line = Path()
            val fill = Path()
            for (k in i..j) {
                val x = left + k * step
                val y = bottom - (values[k] - min) / (max - min) * (bottom - top)
                if (k == i) {
                    line.moveTo(x, y)
                    fill.moveTo(x, bottom)
                    fill.lineTo(x, y)
                } else {
                    line.lineTo(x, y)
                    fill.lineTo(x, y)
                }
            }
            fill.lineTo(left + j * step, bottom)
            fill.close()
            c.drawPath(fill, fillPaint)
            c.drawPath(line, linePaint)
            i = j + 1
        }
        c.drawText("en düşük " + fmt(shownMin) + "  ·  en yüksek " + fmt(shownMax), pad, h - 8f * d, footPaint)
    }
}
