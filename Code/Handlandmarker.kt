package com.example.image_ai

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker.HandLandmarkerOptions
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class HandLandmarkDetector( // 손 랜드마크 탐지 함수
    context: Context,
    private val listener: (HandLandmarkerResult, Int, Int) -> Unit
) {

    private val landmarker: HandLandmarker

    init {
        val options = HandLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath("hand_landmarker.task") // 손 인식 모델 설정
                    .build()
            )
            .setRunningMode(RunningMode.LIVE_STREAM) // 실시간 인식 모드
            .setNumHands(2) // 손 인식 가능한 개수
            .setResultListener { result, input ->
                listener(result, input.height, input.width)
            }
            .build()

        landmarker = HandLandmarker.createFromOptions(context, options)
    }

    fun detectAsync(mpImage: MPImage, timestamp: Long) { // 손 인식 싱크 맞춰주는 함수
        landmarker.detectAsync(mpImage, timestamp)
    }

    fun close() {
        landmarker.close()
    }
}
