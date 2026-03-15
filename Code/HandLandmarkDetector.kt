package com.example.image_ai

import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

fun HandLandmarkerResult.filterHands(
    keepIndices: List<Int>
) {

    val filteredLandmarks = keepIndices.map { this.landmarks()[it] }
    val filteredHandedness = keepIndices.map { this.handedness()[it] }

    val hasWorldLandmarks =
        this.worldLandmarks().isNotEmpty() &&
                this.worldLandmarks().size == this.landmarks().size

    val filteredWorldLandmarks =
        if (hasWorldLandmarks) {
            keepIndices.map { this.worldLandmarks()[it] }
        } else {
            emptyList()
        }
}