package com.example.image_ai

import android.content.Context
import android.util.Log
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
                .setMinHandDetectionConfidence(0.35f)
                .setMinHandPresenceConfidence(0.35f)
                .setMinTrackingConfidence(0.35f)
                .setResultListener { result: HandLandmarkerResult, input: MPImage ->
                    Log.d("HAND_COUNT", "감지된 손 개수: ${result.landmarks().size}")
                    listener(result, input.width, input.height)
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
            try {
                landmarker.detectAsync(mpImage, timestamp)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun close() {
        if (::landmarker.isInitialized) {
            landmarker.close()
        }
    }
}