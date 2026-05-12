package com.example.image_ai

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.image_ai.databinding.ActivityMainBinding
import com.google.mediapipe.framework.image.BitmapImageBuilder
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import androidx.core.graphics.scale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var detector: HandLandmarkDetector

    private var streamJob: Job? = null
    private var motorStopped = false

    private val esp32Host = "192.168.1.181"
    private val esp32StreamUrl = "http://$esp32Host:81/stream"

    private val uiUpdateIntervalMs = 33L
    private var isDetecting = false

    private var reconnectDelayMs = 200L
    private val maxReconnectDelayMs = 3000L

    private val roiLeftRatio = 0.2f
    private val roiTopRatio = 0.2f
    private val roiRightRatio = 0.8f
    private val roiBottomRatio = 0.8f

    private var lastDetectTime = 0L
    private val detectIntervalMs = 400L
    private var lastUiUpdateTime = 0L

    private var lastServerSendTime = 0L
    private val serverSendIntervalMs = 4000L

    private var dangerLeft = 0f
    private var dangerTop = 0f
    private var dangerRight = 0f
    private var dangerBottom = 0f

    private var isSettingDangerZone = false
    private var dangerZoneSet = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnMotorRun.setOnClickListener {
            runMotor()
        }

        binding.btnSetDangerZone.setOnClickListener {
            if (!isSettingDangerZone) {
                isSettingDangerZone = true
                dangerZoneSet = false

                binding.handOverlayView.clearDangerZone()

                binding.handStatusBar.tvHandStatus.text = "양손으로 위험구역을 지정하세요"
                binding.handStatusBar.tvHandStatus.setTextColor(Color.YELLOW)

            } else {
                isSettingDangerZone = false
                dangerZoneSet = true

                binding.handOverlayView.setDangerZone(
                    dangerLeft,
                    dangerTop,
                    dangerRight,
                    dangerBottom,
                    true
                )

                binding.handOverlayView.bringToFront()

                binding.handStatusBar.tvHandStatus.text = "위험구역 설정 완료"
                binding.handStatusBar.tvHandStatus.setTextColor(Color.GREEN)
            }
        }

        detector = HandLandmarkDetector(this) { result, imgW, imgH ->
            runOnUiThread {
                val keepIndices = result.landmarks().mapIndexedNotNull { index, hand ->
                    val cx = hand.map { it.x() }.average().toFloat()
                    val cy = hand.map { it.y() }.average().toFloat()

                    if (
                        cx in roiLeftRatio..roiRightRatio &&
                        cy in roiTopRatio..roiBottomRatio
                    ) index else null
                }

                val handDetected = keepIndices.isNotEmpty()

                try {
                    if (isSettingDangerZone && result.landmarks().size >= 2) {
                        val hands = result.landmarks()

                        val hand1 = hands[0]
                        val hand2 = hands[1]

                        val hand1CenterX = hand1.map { it.x() }.average().toFloat()
                        val hand1CenterY = hand1.map { it.y() }.average().toFloat()

                        val hand2CenterX = hand2.map { it.x() }.average().toFloat()
                        val hand2CenterY = hand2.map { it.y() }.average().toFloat()

                        dangerLeft = minOf(hand1CenterX, hand2CenterX)
                        dangerRight = maxOf(hand1CenterX, hand2CenterX)
                        dangerTop = minOf(hand1CenterY, hand2CenterY)
                        dangerBottom = maxOf(hand1CenterY, hand2CenterY)

                        binding.handOverlayView.setDangerZone(
                            dangerLeft,
                            dangerTop,
                            dangerRight,
                            dangerBottom,
                            true
                        )

                        dangerZoneSet = true

                        Log.d("DANGER_ZONE", "위험구역 갱신: $dangerLeft,$dangerTop,$dangerRight,$dangerBottom")
                    }
                } catch (e: Exception) {
                    Log.e("DANGER_ZONE", "위험구역 설정 중 오류", e)
                }

                val inDangerZone = if (dangerZoneSet && result.landmarks().isNotEmpty()) {
                    val hand = result.landmarks()[0]

                    val centerX = hand.map { it.x() }.average().toFloat()
                    val centerY = hand.map { it.y() }.average().toFloat()

                    centerX in dangerLeft..dangerRight &&
                            centerY in dangerTop..dangerBottom
                } else {
                    true
                }

                if (handDetected) {
                    binding.handStatusBar.tvHandStatus.text =
                        if (isSettingDangerZone) "위험구역 설정 중"
                        else if (inDangerZone) "● HAND DETECTED"
                        else "손 감지됨 - 위험구역 밖"

                    binding.handStatusBar.tvHandStatus.setTextColor(
                        if (inDangerZone) Color.GREEN else Color.YELLOW
                    )

                    binding.handOverlayView.setResults(
                        result,
                        imgW,
                        imgH
                    )

                    if (!isSettingDangerZone && dangerZoneSet && inDangerZone && !motorStopped) {
                        motorStopped = true
                        stopMotor()

                        binding.handStatusBar.tvMotorStatus.text = "Ⅱ MOTOR STOPPED"
                        binding.handStatusBar.tvMotorStatus.setTextColor(Color.RED)
                    }

                } else {
                    binding.handStatusBar.tvHandStatus.text = "○ HAND LOST"
                    binding.handStatusBar.tvHandStatus.setTextColor(Color.RED)

                    binding.handOverlayView.clear()
                }
            }
        }

        startEsp32CamStream()
    }

    private var lastFrameTime = 0L
    private var streamConnected = false

    private fun startEsp32CamStream() {
        if (streamJob?.isActive == true) return

        streamJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {

                val reader = Esp32CamStreamReader(
                    streamUrl = esp32StreamUrl,

                    onFrame = { bitmap ->
                        val now = System.currentTimeMillis()

                        lastFrameTime = now
                        reconnectDelayMs = 200L

                        if (!streamConnected) {
                            streamConnected = true

                            binding.handStatusBar.tvHandStatus.post {
                                binding.handStatusBar.tvHandStatus.text = "ESP32-CAM 연결됨"
                                binding.handStatusBar.tvHandStatus.setTextColor(Color.GREEN)
                            }
                        }

                        if (now - lastUiUpdateTime >= uiUpdateIntervalMs) {
                            lastUiUpdateTime = now

                            binding.previewImageView.post {
                                binding.previewImageView.setImageBitmap(bitmap)
                            }
                        }

                        if (now - lastDetectTime >= detectIntervalMs && !isDetecting) {
                            lastDetectTime = now
                            isDetecting = true

                            lifecycleScope.launch(Dispatchers.Default) {
                                try {
                                    val resizedBitmap = bitmap.scale(320, 240)
                                    detectHand(resizedBitmap)
                                } catch (e: Exception) {
                                    Log.e("HAND", "손 인식 오류", e)
                                } finally {
                                    isDetecting = false
                                }
                            }
                        }
                    },

                    onError = { e ->
                        Log.e("ESP32-CAM", "스트림 오류 감지", e)

                        val now = System.currentTimeMillis()

                        // 최근 3초 안에 프레임이 들어왔으면 실제 끊김 아님
                        if (now - lastFrameTime <= 3000L) {
                            Log.d("ESP32-CAM", "일시적 오류 무시: 프레임 수신 중")
                            return@Esp32CamStreamReader
                        }

                        streamConnected = false

                        binding.handStatusBar.tvHandStatus.post {
                            binding.handStatusBar.tvHandStatus.text = "연결 끊김 - 재연결 중..."
                            binding.handStatusBar.tvHandStatus.setTextColor(Color.YELLOW)
                        }
                    }
                )

                try {
                    Log.d("ESP32-CAM", "스트림 연결 시도: $esp32StreamUrl")
                    reader.start()
                } catch (e: Exception) {
                    Log.e("ESP32-CAM", "reader.start 예외", e)
                }

                if (!isActive) break

                val now = System.currentTimeMillis()

                // 최근 3초 안에 프레임이 들어왔으면 재연결 상태로 표시하지 않음
                if (now - lastFrameTime <= 3000L) {
                    Log.d("ESP32-CAM", "프레임 수신 중이므로 재연결 표시 생략")
                    delay(500L)
                    continue
                }

                streamConnected = false

                binding.handStatusBar.tvHandStatus.post {
                    binding.handStatusBar.tvHandStatus.text = "연결 끊김 - 재연결 중..."
                    binding.handStatusBar.tvHandStatus.setTextColor(Color.YELLOW)
                }

                Log.d("ESP32-CAM", "${reconnectDelayMs}ms 후 재연결 시도")

                delay(reconnectDelayMs)

                reconnectDelayMs = (reconnectDelayMs * 2)
                    .coerceAtMost(maxReconnectDelayMs)
            }
        }
    }

    private fun detectHand(bitmap: Bitmap) {
        try {
            val mpImage = BitmapImageBuilder(bitmap).build()

            detector.detectAsync(
                mpImage,
                SystemClock.uptimeMillis()
            )
        } catch (e: Exception) {
            Log.e("MediaPipe", "손 감지 실패", e)
        }
    }
/*
    private fun sendHandDetectedToServerThrottled() {
        val now = System.currentTimeMillis()

        if (now - lastServerSendTime < serverSendIntervalMs) return

        lastServerSendTime = now
        //sendHandDetectedToServer()
    }

    private fun sendHandDetectedToServer() {
        lifecycleScope.launch {
            try {
                val request = HandStatusRequest(
                    device = DeviceInfo(
                        device_key = "android-001",
                        type = "android"
                    ),
                    event = EventInfo(
                        type = "HAND_DETECTED",
                        occurred_at = java.time.Instant.now().toString(),
                        severity = "critical"
                    ),
                    location = LocationInfo(
                        lat = 37.5665,
                        lng = 126.9780
                    ),
                    payload = PayloadInfo(
                        additionalProp1 = mapOf(
                            "message" to "손 감지됨",
                            "handDetected" to true
                        )
                    )
                )

                val response = RetrofitClient.api.sendHandStatus(request)

                if (response.isSuccessful) {
                    Log.d("AWS", "서버 전송 성공")
                } else {
                    Log.e("AWS", "서버 전송 실패: ${response.code()}")
                }

            } catch (e: Exception) {
                Log.e("AWS", "서버 연결 오류", e)
            }
        }
    }
*/
    private fun runMotor() {
        val request = Request.Builder()
            .url("http://$esp32Host/run")
            .get()
            .build()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    Log.d("MOTOR", "재시작 요청 성공: ${response.code}")

                    if (response.isSuccessful) {
                        motorStopped = false

                        runOnUiThread {
                            binding.handStatusBar.tvMotorStatus.text = "▶ MOTOR RUNNING"
                            binding.handStatusBar.tvMotorStatus.setTextColor(Color.GREEN)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MOTOR", "재시작 요청 실패", e)
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun stopMotor() {
        val request = Request.Builder()
            .url("http://$esp32Host/stop")
            .get()
            .build()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    Log.d("MOTOR", "정지 요청 성공: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("MOTOR", "정지 요청 실패", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        streamJob?.cancel()
        detector.close()
    }
}