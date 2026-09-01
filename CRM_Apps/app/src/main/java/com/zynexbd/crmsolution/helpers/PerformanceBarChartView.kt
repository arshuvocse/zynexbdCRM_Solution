package com.zynexbd.crmsolution.helpers

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.ceil
import kotlin.math.max

data class BarChartItem(
    val label: String,
    val visitsCount: Int,
    val followUpsCount: Int,
    val customersCount: Int = 0
)

class PerformanceBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val items = mutableListOf<BarChartItem>()
    private var animationProgress = 1f
    private var animator: ValueAnimator? = null

    // Bar Paints
    private val visitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2563EB") // Royal Blue
        style = Paint.Style.FILL
    }

    private val followUpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F59E0B") // Amber / Orange
        style = Paint.Style.FILL
    }

    private val customerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7C3AED") // Purple
        style = Paint.Style.FILL
    }

    // Text & Axis Paints
    private val axisLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#64748B") // Slate Gray
        textSize = dpToPx(9.5f)
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private val employeeLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E293B") // Dark Slate
        textSize = dpToPx(10f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
        textSize = dpToPx(9f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E2E8F0") // Grid lines
        strokeWidth = dpToPx(1f)
        style = Paint.Style.STROKE
    }

    private val axisLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8") // Axis line
        strokeWidth = dpToPx(1.5f)
        style = Paint.Style.STROKE
    }

    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textSize = dpToPx(13f)
        textAlign = Paint.Align.CENTER
    }

    fun setData(newItems: List<BarChartItem>, animate: Boolean = true) {
        items.clear()
        items.addAll(newItems)

        animator?.cancel()
        if (animate) {
            animationProgress = 0f
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 750
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    animationProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            animationProgress = 1f
            invalidate()
        }
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val minWidthPerItem = dpToPx(75f).toInt()
        val calculatedWidth = max(suggestedMinimumWidth, items.size * minWidthPerItem + dpToPx(55f).toInt())
        val resolvedWidth = resolveSize(calculatedWidth, widthMeasureSpec)
        val resolvedHeight = resolveSize(dpToPx(230f).toInt(), heightMeasureSpec)
        setMeasuredDimension(max(resolvedWidth, calculatedWidth), resolvedHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        if (items.isEmpty()) {
            canvas.drawText("কোনো পারফরম্যান্স ডাটা পাওয়া যায়নি", w / 2f, h / 2f, emptyPaint)
            return
        }

        val paddingBottom = dpToPx(38f)
        val paddingTop = dpToPx(28f)
        val paddingLeft = dpToPx(38f) // Left space for Y-Axis numbers
        val paddingRight = dpToPx(20f)

        val chartHeight = h - paddingTop - paddingBottom
        val chartBottom = h - paddingBottom

        // Compute max scale value rounded up to nice steps (e.g. 5, 10, 15, 20)
        val rawMax = items.maxOfOrNull { max(it.visitsCount, max(it.followUpsCount, it.customersCount)) } ?: 1
        val maxVal = if (rawMax <= 5) 5 else ((ceil(rawMax / 5.0) * 5).toInt())

        // 1. Draw Y-Axis Grid Lines & Scale Numbers (0, 25%, 50%, 75%, 100%)
        val steps = 4
        for (step in 0..steps) {
            val stepVal = (maxVal.toFloat() / steps) * step
            val stepY = chartBottom - (step.toFloat() / steps) * chartHeight

            // Grid line
            canvas.drawLine(paddingLeft, stepY, w - paddingRight, stepY, gridPaint)

            // Y-Axis Number Label
            val labelText = stepVal.toInt().toString()
            canvas.drawText(labelText, paddingLeft - dpToPx(6f), stepY + dpToPx(3.5f), axisLabelPaint)
        }

        // Draw Left Y-Axis line & Bottom X-Axis line
        canvas.drawLine(paddingLeft, paddingTop - dpToPx(6f), paddingLeft, chartBottom, axisLinePaint)
        canvas.drawLine(paddingLeft, chartBottom, w - paddingRight, chartBottom, axisLinePaint)

        // 2. Draw Grouped 3-Bars per employee
        val availableWidth = w - paddingLeft - paddingRight
        val columnWidth = availableWidth / items.size
        val barWidth = dpToPx(12f)
        val barRadius = dpToPx(3.5f)
        val barSpacing = dpToPx(2.5f)

        for (i in items.indices) {
            val item = items[i]
            val centerX = paddingLeft + (i + 0.5f) * columnWidth

            val visitHeight = (item.visitsCount.toFloat() / maxVal) * chartHeight * animationProgress
            val followUpHeight = (item.followUpsCount.toFloat() / maxVal) * chartHeight * animationProgress
            val customerHeight = (item.customersCount.toFloat() / maxVal) * chartHeight * animationProgress

            // 1) Visits Bar (Left)
            val vLeft = centerX - (barWidth * 1.5f) - barSpacing
            val vRight = vLeft + barWidth
            val vTop = chartBottom - visitHeight
            val vRect = RectF(vLeft, vTop, vRight, chartBottom)
            canvas.drawRoundRect(vRect, barRadius, barRadius, visitPaint)

            // 2) Follow-up Bar (Center)
            val fLeft = centerX - (barWidth / 2f)
            val fRight = fLeft + barWidth
            val fTop = chartBottom - followUpHeight
            val fRect = RectF(fLeft, fTop, fRight, chartBottom)
            canvas.drawRoundRect(fRect, barRadius, barRadius, followUpPaint)

            // 3) Customers Bar (Right)
            val cLeft = centerX + (barWidth / 2f) + barSpacing
            val cRight = cLeft + barWidth
            val cTop = chartBottom - customerHeight
            val cRect = RectF(cLeft, cTop, cRight, chartBottom)
            canvas.drawRoundRect(cRect, barRadius, barRadius, customerPaint)

            // Values on top of bars
            if (animationProgress > 0.6f) {
                if (item.visitsCount > 0) {
                    canvas.drawText("${item.visitsCount}", (vLeft + vRight) / 2f, max(paddingTop + dpToPx(8f), vTop - dpToPx(3f)), valuePaint)
                }
                if (item.followUpsCount > 0) {
                    canvas.drawText("${item.followUpsCount}", (fLeft + fRight) / 2f, max(paddingTop + dpToPx(8f), fTop - dpToPx(3f)), valuePaint)
                }
                if (item.customersCount > 0) {
                    canvas.drawText("${item.customersCount}", (cLeft + cRight) / 2f, max(paddingTop + dpToPx(8f), cTop - dpToPx(3f)), valuePaint)
                }
            }

            // Employee Name Label
            var displayLabel = item.label
            if (displayLabel.length > 8) {
                displayLabel = displayLabel.take(7) + ".."
            }
            canvas.drawText(displayLabel, centerX, chartBottom + dpToPx(18f), employeeLabelPaint)
        }
    }

    private fun dpToPx(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }
}
