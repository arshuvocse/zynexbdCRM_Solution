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
import com.zynexbd.crmsolution.models.ChartFunnelStage
import kotlin.math.max

class FunnelChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val stages = mutableListOf<ChartFunnelStage>()
    private var animationProgress = 1f

    private val bgBarPaint = Paint().apply {
        color = Color.parseColor("#1E293B") // Dark Slate
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val barPaints = listOf(
        Color.parseColor("#3B82F6"), // Stage 1: Blue
        Color.parseColor("#06B6D4"), // Stage 2: Cyan
        Color.parseColor("#10B981"), // Stage 3: Emerald
        Color.parseColor("#8B5CF6")  // Stage 4: Purple
    )

    private val fillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val titlePaint = Paint().apply {
        color = Color.WHITE
        textSize = 28f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val valuePaint = Paint().apply {
        color = Color.parseColor("#E2E8F0")
        textSize = 26f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
        isAntiAlias = true
    }

    private val percentPaint = Paint().apply {
        color = Color.parseColor("#94A3B8")
        textSize = 22f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textAlign = Paint.Align.RIGHT
        isAntiAlias = true
    }

    private val barRect = RectF()
    private val bgRect = RectF()

    fun setData(newStages: List<ChartFunnelStage>) {
        stages.clear()
        stages.addAll(newStages)

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 850
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                animationProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val count = max(1, stages.size)
        val desiredHeight = (count * dpToPx(58f)).toInt() + paddingTop + paddingBottom
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (stages.isEmpty()) return

        val w = width.toFloat()
        val maxVal = max(1, stages.maxOfOrNull { it.stageCount } ?: 1).toFloat()
        val rowHeight = dpToPx(54f)
        val barHeight = dpToPx(12f)
        val leftPad = dpToPx(8f)
        val rightPad = dpToPx(8f)
        val maxBarWidth = w - leftPad - rightPad

        stages.forEachIndexed { index, stage ->
            val topY = paddingTop + index * rowHeight

            // Draw Stage Name & Count
            val labelY = topY + dpToPx(18f)
            canvas.drawText(stage.stageName, leftPad, labelY, titlePaint)

            val countText = "${stage.stageCount} (${String.format("%.1f", stage.conversionPercent)}%)"
            canvas.drawText(countText, w - rightPad, labelY, valuePaint)

            // Draw Background Bar
            val barTop = topY + dpToPx(26f)
            val barBottom = barTop + barHeight
            bgRect.set(leftPad, barTop, w - rightPad, barBottom)
            canvas.drawRoundRect(bgRect, barHeight / 2f, barHeight / 2f, bgBarPaint)

            // Draw Filled Progress Bar
            val progressRatio = (stage.stageCount / maxVal).coerceIn(0.04f, 1.0f)
            val currentBarW = maxBarWidth * progressRatio * animationProgress
            barRect.set(leftPad, barTop, leftPad + currentBarW, barBottom)

            fillPaint.color = barPaints[index % barPaints.size]
            canvas.drawRoundRect(barRect, barHeight / 2f, barHeight / 2f, fillPaint)
        }
    }

    private fun dpToPx(dp: Float): Float = dp * context.resources.displayMetrics.density
}
