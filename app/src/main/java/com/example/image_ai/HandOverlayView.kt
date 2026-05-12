package com.example.image_ai.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class HandOverlayView(
    context: Context,
    attrs: AttributeSet?
) : View(context, attrs) {

    private var result: HandLandmarkerResult? = null
    private var imageWidth = 1
    private var imageHeight = 1

    private val handConnections = listOf(
        0 to 1, 1 to 2, 2 to 3, 3 to 4,        // 엄지
        0 to 5, 5 to 6, 6 to 7, 7 to 8,        // 검지
        5 to 9, 9 to 10, 10 to 11, 11 to 12,   // 중지
        9 to 13, 13 to 14, 14 to 15, 15 to 16, // 약지
        13 to 17, 17 to 18, 18 to 19, 19 to 20,// 새끼
        0 to 17                                // 손바닥
    )

    private val pointPaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 8f
        style = Paint.Style.FILL
    }

    private val linePaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private var dangerLeft = 0f
    private var dangerTop = 0f
    private var dangerRight = 0f
    private var dangerBottom = 0f
    private var showDangerZone = false

    private val dangerPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    fun setResults(
        result: HandLandmarkerResult,
        imageWidth: Int,
        imageHeight: Int
    ) {
        this.result = result
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        invalidate()
    }

    fun clear() {
        result = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (showDangerZone) {
            canvas.drawRect(
                dangerLeft * width,
                dangerTop * height,
                dangerRight * width,
                dangerBottom * height,
                dangerPaint
            )
        }

        val handLandmarks = result?.landmarks() ?: return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        val imageAspect = imageWidth.toFloat() / imageHeight.toFloat()
        val viewAspect = viewWidth / viewHeight

        val scale: Float
        val offsetX: Float
        val offsetY: Float

        if (imageAspect > viewAspect) {
            scale = viewWidth / imageWidth
            offsetX = 0f
            offsetY = (viewHeight - imageHeight * scale) / 2f
        } else {
            scale = viewHeight / imageHeight
            offsetX = (viewWidth - imageWidth * scale) / 2f
            offsetY = 0f
        }
        for (landmarks in handLandmarks) {
            // 선
            for ((startIdx, endIdx) in handConnections) {
                val start = landmarks[startIdx]
                val end = landmarks[endIdx]

                val startX = offsetX + start.x() * imageWidth * scale
                val startY = offsetY + start.y() * imageHeight * scale
                val endX = offsetX + end.x() * imageWidth * scale
                val endY = offsetY + end.y() * imageHeight * scale

                canvas.drawLine(
                    startX,
                    startY,
                    endX,
                    endY,
                    linePaint
                )
            }

            // 점
            for (landmark in landmarks) {
                val x = offsetX + landmark.x() * imageWidth * scale
                val y = offsetY + landmark.y() * imageHeight * scale

                canvas.drawCircle(
                    x,
                    y,
                    8f,
                    pointPaint
                )
            }
    }
}

    fun setDangerZone(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        show: Boolean
    ) {
        dangerLeft = left
        dangerTop = top
        dangerRight = right
        dangerBottom = bottom
        showDangerZone = show
        invalidate()
    }
    fun clearDangerZone() {
        showDangerZone = false

        dangerLeft = 0f
        dangerTop = 0f
        dangerRight = 0f
        dangerBottom = 0f

        invalidate()
    }
}