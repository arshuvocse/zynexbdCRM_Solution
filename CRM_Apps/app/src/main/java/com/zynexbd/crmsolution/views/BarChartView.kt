package com.zynexbd.crmsolution.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.max

class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class BarEntry(
        val label: String,
        val primaryValue: Float,   // e.g. Visits
        val secondaryValue: Float  // e.g. Follow-ups
    )

    private val entries = mutableListOf<BarEntry>()
    private var animationProgress = 1f

    private val bar1Paint = Paint().apply {
        color = Color.parseColor("#4F46E5") // Indigo
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val bar2Paint = Paint().apply {
        color = Color.parseColor("#06B6D4") // Cyan / Emerald
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val labelPaint = Paint().apply {
        color = Color.parseColor("#94A3B8")
        textSize = 22f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val valuePaint = Paint().apply {
        color = Color.WHITE
        textSize = 18f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val gridLinePaint = Paint().apply {
        color = Color.parseColor("#1E293B")
        strokeWidth = 1.5f
        isAntiAlias = true
    }

    fun setData(newEntries: List<BarEntry>) {
        entries.clear()
        entries.addAll(newEntries)

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                animationProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (entries.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()

        val bottomPadding = 48f
        val topPadding = 36f
        val chartHeight = h - bottomPadding - topPadding

        val maxVal = max(5f, entries.maxOf { max(it.primaryValue, it.secondaryValue) })
        val count = entries.size
        val groupWidth = w / count

        // Draw Base Grid Line
        val baselineY = h - bottomPadding
        canvas.drawLine(16f, baselineY, w - 16f, baselineY, gridLinePaint)

        val barW = (groupWidth * 0.28f).coerceAtMost(28f)
        val cornerRadius = 8f

        for (i in 0 until count) {
            val entry = entries[i]
            val groupCenterX = (i * groupWidth) + (groupWidth / 2f)

            // Bar 1 (Primary - Visits)
            val h1 = (entry.primaryValue / maxVal) * chartHeight * animationProgress
            val x1 = groupCenterX - barW - 3f
            val y1 = baselineY - h1
            val rect1 = RectF(x1, y1, x1 + barW, baselineY)
            canvas.drawRoundRect(rect1, cornerRadius, cornerRadius, bar1Paint)

            if (entry.primaryValue > 0f && animationProgress > 0.6f) {
                canvas.drawText("${entry.primaryValue.toInt()}", x1 + barW / 2f, (y1 - 6f).coerceAtLeast(topPadding), valuePaint)
            }

            // Bar 2 (Secondary - Follow-ups)
            val h2 = (entry.secondaryValue / maxVal) * chartHeight * animationProgress
            val x2 = groupCenterX + 3f
            val y2 = baselineY - h2
            val rect2 = RectF(x2, y2, x2 + barW, baselineY)
            canvas.drawRoundRect(rect2, cornerRadius, cornerRadius, bar2Paint)

            if (entry.secondaryValue > 0f && animationProgress > 0.6f) {
                canvas.drawText("${entry.secondaryValue.toInt()}", x2 + barW / 2f, (y2 - 6f).coerceAtLeast(topPadding), valuePaint)
            }

            // X-Axis Day Label
            canvas.drawText(entry.label, groupCenterX, h - 14f, labelPaint)
        }
    }
}
