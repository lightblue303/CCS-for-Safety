package com.example.image_ai.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class HandOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var result: HandLandmarkerResult? = null
    private var imageWidth = 1
    private var imageHeight = 1
    private var isFrontCamera = false

    /* 🔥 ROI 비율 (MainActivity와 동일하게 유지) */
    private val roiLeftRatio = 0.2f
    private val roiTopRatio = 0.2f
    private val roiRightRatio = 0.8f
    private val roiBottomRatio = 0.8f

    private val pointPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        strokeWidth = 8f
    }

    private val linePaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    /* 🔥 ROI 박스 Paint (반투명) */
    private val roiPaint = Paint().apply {
        color = Color.argb(80, 255, 0, 0) // 반투명 빨강
        style = Paint.Style.FILL
    }

    private val roiBorderPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    fun setResults(
        result: HandLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        viewWidth: Int,
        viewHeight: Int,
        isFrontCamera: Boolean
    ) {
        this.result = result
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        this.isFrontCamera = isFrontCamera
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        /* ================= ROI 박스 ================= */

        val left = width * roiLeftRatio
        val top = height * roiTopRatio
        val right = width * roiRightRatio
        val bottom = height * roiBottomRatio

        // 반투명 영역
        canvas.drawRect(left, top, right, bottom, roiPaint)
        // 테두리
        canvas.drawRect(left, top, right, bottom, roiBorderPaint)

        /* ================= 손 랜드마크 ================= */

        val handResult = result ?: return

        for (hand in handResult.landmarks()) {

            // 손 중심 계산 (정규화 좌표)
            val cx = hand.map { it.x() }.average().toFloat()
            val cy = hand.map { it.y() }.average().toFloat()

            // ROI 밖이면 이 손은 그리지 않음
            if (cx < roiLeftRatio || cx > roiRightRatio ||
                cy < roiTopRatio || cy > roiBottomRatio) {
                continue
            }

            fun tx(x: Float): Float {
                val px = x * width
                return if (isFrontCamera) width - px else px
            }

            fun ty(y: Float): Float {
                return y * height
            }

            // 점
            for (lm in hand) {
                canvas.drawCircle(
                    tx(lm.x()),
                    ty(lm.y()),
                    8f,
                    pointPaint
                )
            }

            // 선
            for ((s, e) in HAND_CONNECTIONS) {
                canvas.drawLine(
                    tx(hand[s].x()),
                    ty(hand[s].y()),
                    tx(hand[e].x()),
                    ty(hand[e].y()),
                    linePaint
                )
            }
        }
    }

    companion object {
        val HAND_CONNECTIONS = listOf(
            0 to 1, 1 to 2, 2 to 3, 3 to 4,
            0 to 5, 5 to 6, 6 to 7, 7 to 8,
            5 to 9, 9 to 10, 10 to 11, 11 to 12,
            9 to 13, 13 to 14, 14 to 15, 15 to 16,
            13 to 17, 17 to 18, 18 to 19, 19 to 20,
            0 to 17
        )
    }
    fun clear() {
        result = null
        invalidate()
    }
}