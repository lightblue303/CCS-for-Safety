package com.example.image_ai

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.image_ai.Util.BitmapUtils
import com.example.image_ai.databinding.ActivityMainBinding
import com.google.mediapipe.framework.image.BitmapImageBuilder
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var detector: HandLandmarkDetector

    private val CAMERA_PERMISSION = android.Manifest.permission.CAMERA
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    // 손 인식 ROI 비율 (가운데 영역)
    private val roiLeftRatio = 0.2f
    private val roiTopRatio = 0.2f
    private val roiRightRatio = 0.8f
    private val roiBottomRatio = 0.8f

    /* ================= 권한 ================= */

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            CAMERA_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(CAMERA_PERMISSION),
            0
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 0 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        }
    }

    /* ================= 생명 주기 ================= */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSwitchCamera.setOnClickListener {
            switchCamera()
        }

        detector = HandLandmarkDetector(this) { result, imgH, imgW ->
            runOnUiThread {

                val handDetected = result.landmarks().isNotEmpty()

                if (handDetected) {
                    binding.handStatusBar.tvHandStatus.text = "● HAND DETECTED"
                    binding.handStatusBar.tvHandStatus.setTextColor(Color.GREEN)
                } else {
                    binding.handStatusBar.tvHandStatus.text = "○ HAND LOST"
                    binding.handStatusBar.tvHandStatus.setTextColor(Color.RED)
                }

                val keepIndices = result.landmarks().mapIndexedNotNull { index, hand ->
                    val cx = hand.map { it.x() }.average().toFloat()
                    val cy = hand.map { it.y() }.average().toFloat()

                    if (
                        cx in roiLeftRatio..roiRightRatio &&
                        cy in roiTopRatio..roiBottomRatio
                    ) index else null
                }

                if (keepIndices.isEmpty()) {
                    binding.overlayView.clear()
                } else {
                    binding.overlayView.setResults(
                        result,
                        imgH,
                        imgW,
                        binding.previewView.width,
                        binding.previewView.height,
                        isFrontCamera = (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
                    )
                }
            }
        }

        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    /* ================= 카메라 ================= */

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val analysisExecutor = Executors.newSingleThreadExecutor()

            analysis.setAnalyzer(analysisExecutor) { imageProxy ->

                var bitmap = imageProxy.toBitmap()

                // 회전 보정
                bitmap = BitmapUtils.rotateBitmap(
                    bitmap,
                    imageProxy.imageInfo.rotationDegrees
                )

                // 전면 카메라 좌우 반전
                //if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                //    bitmap = BitmapUtils.mirrorBitmap(bitmap)
                //}

                // ROI 적용 (손 인식 범위 제한)
                // val roiBitmap = cropToRoi(bitmap)

                // MediaPipe 입력
                // val mpImage = BitmapImageBuilder(roiBitmap).build()
                val mpImage = BitmapImageBuilder(bitmap).build()

                detector.detectAsync(
                    mpImage,
                    SystemClock.uptimeMillis()
                )

                imageProxy.close()
            }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                analysis
            )

        }, ContextCompat.getMainExecutor(this))
    }

    /* ================= ROI ================= */

    private fun cropToRoi(bitmap: Bitmap): Bitmap {
        val left = (bitmap.width * roiLeftRatio).toInt()
        val top = (bitmap.height * roiTopRatio).toInt()
        val right = (bitmap.width * roiRightRatio).toInt()
        val bottom = (bitmap.height * roiBottomRatio).toInt()

        return Bitmap.createBitmap(
            bitmap,
            left,
            top,
            right - left,
            bottom - top
        )
    }

    /* ================= 카메라 전환 ================= */

    private fun switchCamera() {
        cameraSelector =
            if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                CameraSelector.DEFAULT_FRONT_CAMERA
            else
                CameraSelector.DEFAULT_BACK_CAMERA

        startCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        detector.close()
    }
}