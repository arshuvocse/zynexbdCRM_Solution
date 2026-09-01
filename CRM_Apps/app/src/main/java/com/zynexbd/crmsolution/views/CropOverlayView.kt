package com.zynexbd.crmsolution.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    val cropRect = RectF()
    private val imageBounds = RectF()

    private val dimPaint = Paint().apply {
        color = Color.parseColor("#99000000")
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint().apply {
        color = Color.parseColor("#38BDF8")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#66FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        isAntiAlias = true
    }

    private val cornerPaint = Paint().apply {
        color = Color.parseColor("#38BDF8")
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val handleTouchRadius = 48f
    private val cornerLength = 36f

    private var activeTouchMode = TouchMode.NONE
    private val lastTouch = PointF()

    private enum class TouchMode {
        NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, TOP, BOTTOM, LEFT, RIGHT
    }

    fun initCropBox(imageRect: RectF) {
        imageBounds.set(imageRect)
        val initialPadding = imageBounds.width() * 0.08f
        cropRect.set(
            imageBounds.left + initialPadding,
            imageBounds.top + initialPadding,
            imageBounds.right - initialPadding,
            imageBounds.bottom - initialPadding
        )
        // Ensure square-ish aspect initially if preferred
        val size = min(cropRect.width(), cropRect.height())
        val cx = cropRect.centerX()
        val cy = cropRect.centerY()
        cropRect.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f)
        invalidate()
    }

    fun setSquareCrop() {
        if (imageBounds.isEmpty) return
        val size = min(imageBounds.width(), imageBounds.height()) * 0.85f
        val cx = imageBounds.centerX()
        val cy = imageBounds.centerY()
        cropRect.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f)
        invalidate()
    }

    fun resetToFull() {
        if (imageBounds.isEmpty) return
        cropRect.set(imageBounds)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cropRect.isEmpty) return

        // 1. Draw Dimmed Background (4 rectangles around crop area)
        canvas.drawRect(0f, 0f, width.toFloat(), cropRect.top, dimPaint)
        canvas.drawRect(0f, cropRect.bottom, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, dimPaint)
        canvas.drawRect(cropRect.right, cropRect.top, width.toFloat(), cropRect.bottom, dimPaint)

        // 2. Draw Crop Border
        canvas.drawRect(cropRect, borderPaint)

        // 3. Draw Rule of Thirds Grid
        val oneThirdW = cropRect.width() / 3f
        val oneThirdH = cropRect.height() / 3f
        canvas.drawLine(cropRect.left + oneThirdW, cropRect.top, cropRect.left + oneThirdW, cropRect.bottom, gridPaint)
        canvas.drawLine(cropRect.left + oneThirdW * 2f, cropRect.top, cropRect.left + oneThirdW * 2f, cropRect.bottom, gridPaint)
        canvas.drawLine(cropRect.left, cropRect.top + oneThirdH, cropRect.right, cropRect.top + oneThirdH, gridPaint)
        canvas.drawLine(cropRect.left, cropRect.top + oneThirdH * 2f, cropRect.right, cropRect.top + oneThirdH * 2f, gridPaint)

        // 4. Draw Corner Handles
        // Top-Left
        canvas.drawLine(cropRect.left, cropRect.top, cropRect.left + cornerLength, cropRect.top, cornerPaint)
        canvas.drawLine(cropRect.left, cropRect.top, cropRect.left, cropRect.top + cornerLength, cornerPaint)

        // Top-Right
        canvas.drawLine(cropRect.right - cornerLength, cropRect.top, cropRect.right, cropRect.top, cornerPaint)
        canvas.drawLine(cropRect.right, cropRect.top, cropRect.right, cropRect.top + cornerLength, cornerPaint)

        // Bottom-Left
        canvas.drawLine(cropRect.left, cropRect.bottom, cropRect.left + cornerLength, cropRect.bottom, cornerPaint)
        canvas.drawLine(cropRect.left, cropRect.bottom - cornerLength, cropRect.left, cropRect.bottom, cornerPaint)

        // Bottom-Right
        canvas.drawLine(cropRect.right - cornerLength, cropRect.bottom, cropRect.right, cropRect.bottom, cornerPaint)
        canvas.drawLine(cropRect.right, cropRect.bottom - cornerLength, cropRect.right, cropRect.bottom, cornerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeTouchMode = getTouchMode(x, y)
                lastTouch.set(x, y)
                return activeTouchMode != TouchMode.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastTouch.x
                val dy = y - lastTouch.y
                handleMove(dx, dy)
                lastTouch.set(x, y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeTouchMode = TouchMode.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun getTouchMode(x: Float, y: Float): TouchMode {
        val minSize = 60f
        return when {
            isNear(x, y, cropRect.left, cropRect.top) -> TouchMode.TOP_LEFT
            isNear(x, y, cropRect.right, cropRect.top) -> TouchMode.TOP_RIGHT
            isNear(x, y, cropRect.left, cropRect.bottom) -> TouchMode.BOTTOM_LEFT
            isNear(x, y, cropRect.right, cropRect.bottom) -> TouchMode.BOTTOM_RIGHT
            isNearEdge(x, cropRect.left) && y >= cropRect.top && y <= cropRect.bottom -> TouchMode.LEFT
            isNearEdge(x, cropRect.right) && y >= cropRect.top && y <= cropRect.bottom -> TouchMode.RIGHT
            isNearEdge(y, cropRect.top) && x >= cropRect.left && x <= cropRect.right -> TouchMode.TOP
            isNearEdge(y, cropRect.bottom) && x >= cropRect.left && x <= cropRect.right -> TouchMode.BOTTOM
            cropRect.contains(x, y) -> TouchMode.MOVE
            else -> TouchMode.NONE
        }
    }

    private fun isNear(x: Float, y: Float, targetX: Float, targetY: Float): Boolean {
        return abs(x - targetX) <= handleTouchRadius && abs(y - targetY) <= handleTouchRadius
    }

    private fun isNearEdge(val1: Float, target: Float): Boolean {
        return abs(val1 - target) <= handleTouchRadius
    }

    private fun handleMove(dx: Float, dy: Float) {
        val minBoxSize = 100f
        val boundL = if (!imageBounds.isEmpty) imageBounds.left else 0f
        val boundT = if (!imageBounds.isEmpty) imageBounds.top else 0f
        val boundR = if (!imageBounds.isEmpty) imageBounds.right else width.toFloat()
        val boundB = if (!imageBounds.isEmpty) imageBounds.bottom else height.toFloat()

        when (activeTouchMode) {
            TouchMode.MOVE -> {
                var newL = cropRect.left + dx
                var newT = cropRect.top + dy
                var newR = cropRect.right + dx
                var newB = cropRect.bottom + dy

                if (newL < boundL) {
                    newR += (boundL - newL)
                    newL = boundL
                }
                if (newR > boundR) {
                    newL -= (newR - boundR)
                    newR = boundR
                }
                if (newT < boundT) {
                    newB += (boundT - newT)
                    newT = boundT
                }
                if (newB > boundB) {
                    newT -= (newB - boundB)
                    newB = boundB
                }
                cropRect.set(newL, newT, newR, newB)
            }
            TouchMode.TOP_LEFT -> {
                val newL = min(max(boundL, cropRect.left + dx), cropRect.right - minBoxSize)
                val newT = min(max(boundT, cropRect.top + dy), cropRect.bottom - minBoxSize)
                cropRect.set(newL, newT, cropRect.right, cropRect.bottom)
            }
            TouchMode.TOP_RIGHT -> {
                val newR = max(min(boundR, cropRect.right + dx), cropRect.left + minBoxSize)
                val newT = min(max(boundT, cropRect.top + dy), cropRect.bottom - minBoxSize)
                cropRect.set(cropRect.left, newT, newR, cropRect.bottom)
            }
            TouchMode.BOTTOM_LEFT -> {
                val newL = min(max(boundL, cropRect.left + dx), cropRect.right - minBoxSize)
                val newB = max(min(boundB, cropRect.bottom + dy), cropRect.top + minBoxSize)
                cropRect.set(newL, cropRect.top, cropRect.right, newB)
            }
            TouchMode.BOTTOM_RIGHT -> {
                val newR = max(min(boundR, cropRect.right + dx), cropRect.left + minBoxSize)
                val newB = max(min(boundB, cropRect.bottom + dy), cropRect.top + minBoxSize)
                cropRect.set(cropRect.left, cropRect.top, newR, newB)
            }
            TouchMode.LEFT -> {
                val newL = min(max(boundL, cropRect.left + dx), cropRect.right - minBoxSize)
                cropRect.set(newL, cropRect.top, cropRect.right, cropRect.bottom)
            }
            TouchMode.RIGHT -> {
                val newR = max(min(boundR, cropRect.right + dx), cropRect.left + minBoxSize)
                cropRect.set(cropRect.left, cropRect.top, newR, cropRect.bottom)
            }
            TouchMode.TOP -> {
                val newT = min(max(boundT, cropRect.top + dy), cropRect.bottom - minBoxSize)
                cropRect.set(cropRect.left, newT, cropRect.right, cropRect.bottom)
            }
            TouchMode.BOTTOM -> {
                val newB = max(min(boundB, cropRect.bottom + dy), cropRect.top + minBoxSize)
                cropRect.set(cropRect.left, cropRect.top, cropRect.right, newB)
            }
            TouchMode.NONE -> {}
        }
    }
}
