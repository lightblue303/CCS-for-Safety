package com.example.image_ai

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker.HandLandmarkerOptions
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class HandLandmarkDetector(
    context: Context,
    private val listener: (HandLandmarkerResult, Int, Int) -> Unit
) {

    private lateinit var landmarker: HandLandmarker

    init {
        try {
            val options = HandLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath("hand_landmarker.task")
                        .build()
                )
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(2)
                .setResultListener { result : HandLandmarkerResult, input : MPImage ->
                    // 항상 전달 → OverlayView에서 처리
                    listener(result, input.height, input.width)
                }
                .build()

            landmarker = HandLandmarker.createFromOptions(context, options)

        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("HandLandmarker init failed", e)
        }
    }

    fun detectAsync(mpImage: MPImage, timestamp: Long) {
        if (::landmarker.isInitialized) {
            landmarker.detectAsync(mpImage, timestamp)
        }
    }

    fun close() {
        if (::landmarker.isInitialized) {
            landmarker.close()
        }
    }
}