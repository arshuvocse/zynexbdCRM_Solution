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
import kotlin.math.min

class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Slice(
        val label: String,
        val value: Float,
        val color: Int
    )

    private val slices = mutableListOf<Slice>()
    private var centerPrimaryText = ""
    private var centerSecondaryText = ""
    private var animationProgress = 1f

    private val arcPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val bgTrackPaint = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#1E293B")
        isAntiAlias = true
    }

    private val centerPrimaryPaint = Paint().apply {
        color = Color.WHITE
        textSize = 42f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val centerSecondaryPaint = Paint().apply {
        color = Color.parseColor("#94A3B8")
        textSize = 24f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val oval = RectF()

    fun setData(newSlices: List<Slice>, primaryCenterText: String = "", secondaryCenterText: String = "") {
        slices.clear()
        slices.addAll(newSlices)
        centerPrimaryText = primaryCenterText
        centerSecondaryText = secondaryCenterText

        // Animate
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

        val size = min(width, height).toFloat()
        if (size <= 0) return

        val strokeWidth = size * 0.14f
        arcPaint.strokeWidth = strokeWidth
        bgTrackPaint.strokeWidth = strokeWidth

        val padding = strokeWidth / 2f + 16f
        val left = (width - size) / 2f + padding
        val top = (height - size) / 2f + padding
        val right = (width + size) / 2f - padding
        val bottom = (height + size) / 2f - padding

        oval.set(left, top, right, bottom)

        // 1. Draw Background Ring Track
        canvas.drawArc(oval, 0f, 360f, false, bgTrackPaint)

        val total = slices.sumOf { it.value.toDouble() }.toFloat()
        if (total > 0f) {
            var currentAngle = -90f
            val maxAngleSweep = 360f * animationProgress

            for (slice in slices) {
                if (slice.value <= 0f) continue
                val sweep = (slice.value / total) * maxAngleSweep
                if (sweep <= 0.5f) continue

                arcPaint.color = slice.color
                // Small gap between slices
                val gap = if (slices.count { it.value > 0f } > 1) 3f else 0f
                canvas.drawArc(oval, currentAngle + gap / 2f, maxOf(1f, sweep - gap), false, arcPaint)
                currentAngle += sweep
            }
        }

        // 2. Draw Center Text
        val cx = width / 2f
        val cy = height / 2f

        if (centerPrimaryText.isNotBlank()) {
            canvas.drawText(centerPrimaryText, cx, cy + 4f, centerPrimaryPaint)
            if (centerSecondaryText.isNotBlank()) {
                canvas.drawText(centerSecondaryText, cx, cy + 34f, centerSecondaryPaint)
            }
        }
    }
}
