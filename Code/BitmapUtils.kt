package com.example.image_ai.Util

import android.graphics.Bitmap
import android.graphics.Matrix

object BitmapUtils {

    fun rotateBitmap( // 회전된 비트맵 생성 함수
        bitmap: Bitmap,
        rotationDegrees: Int
    ): Bitmap {
        if (rotationDegrees == 0) return bitmap  // 비트맵 회전 값이 0 이면, 그대로 출력

        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
        }

        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }

    fun mirrorBitmap(bitmap: Bitmap): Bitmap { // 좌우 반전 비트맵 생성 함수
        val matrix = Matrix().apply {
            preScale(-1f, 1f)
        }

        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }
}
