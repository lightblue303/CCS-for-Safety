package com.example.image_ai

import android.util.Log
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

fun HandLandmarkerResult.safeKeepIndices(
    keepIndices: List<Int>
): List<Int> {
    val handCount = this.landmarks().size

    return keepIndices.filter { index ->
        index >= 0 && index < handCount
    }
}