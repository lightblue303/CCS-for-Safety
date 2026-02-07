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

    // 초기값 설정
    private var isFrontCamera = false // 기본값 = 후면 카메라

    private var result: HandLandmarkerResult? = null
    private var imageWidth = 1
    private var imageHeight = 1

    private var scaleFactor = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    private val pointPaint = Paint().apply { // 정점 색상, 두께 설정
        color = Color.GREEN
        style = Paint.Style.FILL
        strokeWidth = 8f
    }

    private val linePaint = Paint().apply { // 간선 색상, 두께 설정
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    fun setResults( // 출력 값 설정 함수
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
        
        val scaleX = viewWidth.toFloat() / imageWidth
        val scaleY = viewHeight.toFloat() / imageHeight
        scaleFactor = maxOf(scaleX, scaleY)

        val scaledWidth = imageWidth * scaleFactor
        val scaledHeight = imageHeight * scaleFactor

        offsetX = (viewWidth - scaledWidth) / 2f
        offsetY = (viewHeight - scaledHeight) / 2f

        invalidate()
    }

    override fun onDraw(canvas: Canvas) { // 그리기 함수
        super.onDraw(canvas)

        val handResult = result ?: return

        for (hand in handResult.landmarks()) {

            fun tx(x: Float): Float {
                val px = x * imageWidth * scaleFactor + offsetX
                return if (isFrontCamera) width - px else px
            }

            fun ty(y: Float): Float {
                return y * imageHeight * scaleFactor + offsetY
            }

            // 정점
            for (lm in hand) {
                canvas.drawCircle(
                    tx(lm.x()),
                    ty(lm.y()),
                    8f,
                    pointPaint
                )
            }

            // 간선
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

    companion object { // 정점 간의 연결 리스트
        val HAND_CONNECTIONS = listOf(
            0 to 1, 1 to 2, 2 to 3, 3 to 4,
            0 to 5, 5 to 6, 6 to 7, 7 to 8,
            5 to 9, 9 to 10, 10 to 11, 11 to 12,
            9 to 13, 13 to 14, 14 to 15, 15 to 16,
            13 to 17, 17 to 18, 18 to 19, 19 to 20,
            0 to 17
        )
    }
}
