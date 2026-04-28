package com.example.worker1

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.firebase.database.*
import kotlin.math.sqrt

// ⭐ SensorEventListener를 상속받아 센서 기능을 추가했습니다.
class SafetyService : Service(), SensorEventListener {

    private val CHANNEL_ID = "safety_service_channel"
    private var statusRef: DatabaseReference? = null
    private var statusListener: ValueEventListener? = null
    private var mediaPlayer: MediaPlayer? = null // 알람 노래 재생기

    // ⭐ [추가] 센서 관리 및 흔들기 감지를 위한 변수
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var currentStatus: String = "NORMAL"

    private var shakeCount = 0
    private var lastShakeTime: Long = 0
    private val SHAKE_THRESHOLD = 15.0f // 흔들림 강도 (테스트 후 조정 가능)
    private val SHAKE_INTERVAL = 500L  // 0.5초 이내 연속 흔들림 체크

    // ⭐ [추가] 서비스 생성 시 센서 초기화
    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // 센서 리스너 등록 (평소에도 감지 시작)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("안전 모니터링 작동 중")
            .setContentText("실시간으로 상태를 감시하고 있습니다.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(101, notification)

        statusRef = FirebaseDatabase.getInstance().getReference("workers/w1/status")
        statusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.getValue(String::class.java) ?: "NORMAL"
                currentStatus = status // ⭐ [추가] 실시간 상태를 변수에 저장 (흔들기 판정용)

                if (status == "CHECKING") {
                    sendSafetyAlertNotification()
                } else if (status == "NORMAL" || status == "OFFLINE") {
                    stopAlarmSound() // 정상 복구 시 노래 끄기
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        statusRef?.addValueEventListener(statusListener!!)

        return START_STICKY
    }

    // ⭐ [추가] 실시간 센서 변화 감지 알고리즘
    // ⭐ [수정] 이 부분을 기존 onSensorChanged와 교체하세요!
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            // 현재 상태가 'CHECKING'일 때만 흔들림을 유효한 응답으로 인정함
            if (currentStatus == "CHECKING") {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                // 중력을 제외한 순수 흔들림 계산 (임계값 20.0f 적용)
                val magnitude = sqrt(x * x + y * y + z * z) - 9.8f
                val currentTime = System.currentTimeMillis()

                // 1. 흔들림 강도가 충분히 센가? (20.0f 이상)
                if (magnitude > 20.0f) {
                    val timeDiff = currentTime - lastShakeTime

                    // 2. [오동작 방지] 너무 순식간에 일어난 연속 진동(0.15초 미만)은 1번으로 간주 (무시)
                    if (timeDiff < 200L) {
                        return
                    }

                    // 3. [연속성 체크] 0.2초 ~ 0.7초 사이의 적당한 간격으로 흔들었는가?
                    if (timeDiff <= 700L) {
                        shakeCount++


                        if (shakeCount >= 3) { // ⭐ 드디어 3번 연속 흔들기 성공!
                            statusRef?.setValue("NORMAL")
                            shakeCount = 0

                        }
                    } else {
                        // 마지막 흔들림 이후 너무 오래 지났으면(0.7초 초과) 다시 1번부터 시작
                        shakeCount = 1

                    }

                    lastShakeTime = currentTime
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun sendSafetyAlertNotification() {
        playAlarmSound() // 🚨 알람 노래 시작

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val alertNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🚨 긴급 안전 확인")
            .setContentText("기울기 이상 감지! 즉시 응답 버튼을 누르거나 기기를 흔들어주세요.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true) // 잠금화면 뚫기
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1, alertNotification)
    }

    private fun playAlarmSound() {
        try {
            if (mediaPlayer == null) {
                // [수정] 기본 알람음 대신 우리 m4a 파일을 로드합니다.
                mediaPlayer = MediaPlayer.create(this, R.raw.emergency_sound)
                mediaPlayer?.isLooping = true // 무한 반복
                mediaPlayer?.start()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun stopAlarmSound() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "Safety Monitoring", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), audioAttributes)
                enableVibration(true)
            }
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopAlarmSound()
        statusListener?.let { statusRef?.removeEventListener(it) }
        // ⭐ [추가] 서비스 종료 시 센서 해제 (배터리 절약)
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }
}