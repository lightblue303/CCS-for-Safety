package com.example.image_ai

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.CameraSelector
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


    private fun hasCameraPermission(): Boolean { // 카메라 권한 확인 함수
        return ContextCompat.checkSelfPermission(
            this,
            CAMERA_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() { // 카메라 권한 요청 함수
        ActivityCompat.requestPermissions(
            this,
            arrayOf(CAMERA_PERMISSION),
            0
        )
    }

    override fun onRequestPermissionsResult( // 권한 종합 및 카메라 실행 함수
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater) // 뷰 바인딩
        setContentView(binding.root)

        binding.btnSwitchCamera.setOnClickListener { // 카메라 전환 버튼 바인딩
            switchCamera()
        }

        detector = HandLandmarkDetector(this) { result, imgH, imgW -> // 감지한 내용을 전달
            runOnUiThread {
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

        if (hasCameraPermission()) { // 권한 허가 여부에 따라, 카메라 실행 or 권한 재 요청
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this) // 카메라 비동기 요청

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also { // 화면에 카메라 영상 표시
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder() // 프레임 분석
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // 분석이 느리면, 이전 프레임 버리고 최신만 유지
                .build()

            val analysisExecutor = Executors.newSingleThreadExecutor() // 분석 전용 스레드 생성

            analysis.setAnalyzer(analysisExecutor) { imageProxy -> // 프레임 분석기 등록

                var bitmap = imageProxy.toBitmap()
                bitmap = BitmapUtils.rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees) // 회전 보정
                val mpImage = BitmapImageBuilder(bitmap).build() // MediaPipe 입력 이미지 생성

                if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) { // 전면 카메라 좌우 반전
                    bitmap = BitmapUtils.mirrorBitmap(bitmap)
                }

                detector.detectAsync( // 손 인식 비동기 실행
                    mpImage,
                    SystemClock.uptimeMillis()
                )

                imageProxy.close()
            }

            cameraProvider.unbindAll() // 카메라 해제
            cameraProvider.bindToLifecycle( // 카메라 + 프리뷰 + 분석 바인딩 & 카메라 ON
                this,
                cameraSelector,
                preview,
                analysis
            )

        }, ContextCompat.getMainExecutor(this)) // 메인 스레드에서 실행
    }

    private fun switchCamera() { // 카메라 전환 기능 함수
        cameraSelector =
            if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                CameraSelector.DEFAULT_FRONT_CAMERA
            else
                CameraSelector.DEFAULT_BACK_CAMERA

        startCamera() // 변경된 카메라로 실행
    }

    override fun onDestroy() {
        super.onDestroy()
        detector.close()
    }
}
