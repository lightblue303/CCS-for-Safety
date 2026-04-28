package com.example.manager1

import android.app.*
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.graphics.Color
import androidx.core.app.NotificationCompat
import com.google.firebase.database.*

class ManagerService : Service() {
    private var statusRef: DatabaseReference? = null
    private var sosTimeRef: DatabaseReference? = null // 추가
    private var ringtone: android.media.Ringtone? = null

    override fun onBind(intent: Intent?): IBinder? = null

    // 🚨 추가: 시스템이 서비스를 죽여도 자동으로 다시 살려줌
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundService()

        val database = FirebaseDatabase.getInstance()

        // 1. 상태 감시 (기존)
        statusRef = database.getReference("workers/w1/status")
        statusRef?.addValueEventListener(statusListener)

        // 2. ⭐ 시간 감시 추가 (여러 번 클릭 대응)
        sosTimeRef = database.getReference("workers/w1/last_sos_time")
        sosTimeRef?.addValueEventListener(sosTimeListener)
    }

    private val statusListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val status = snapshot.getValue(String::class.java) ?: "NORMAL"
            if (status == "NORMAL") {
                ringtone?.stop()
            }
        }
        override fun onCancelled(error: DatabaseError) {}
    }

    // ⭐ 추가: 버튼을 누를 때마다 백그라운드에서도 알람을 울림
    private val sosTimeListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val lastTime = snapshot.getValue(Long::class.java) ?: 0L
            if (lastTime > 0) {
                playAlarm()
                sendEmergencyNotification()
            }
        }
        override fun onCancelled(error: DatabaseError) {}
    }

    private fun playAlarm() {
        // [수정] 시스템 기본음 대신 우리 사이렌 소리 경로 지정
        val soundUri = android.net.Uri.parse("android.resource://${packageName}/${R.raw.emergency_sound}")

        if (ringtone == null) {
            ringtone = RingtoneManager.getRingtone(applicationContext, soundUri)
        }
        ringtone?.play()
    }

    private fun sendEmergencyNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        // FLAG_IMMUTABLE 또는 FLAG_UPDATE_CURRENT 확인
        val pendingIntent = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, "MANAGER_CHANNEL")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 긴급 SOS 발생!!")
            .setContentText("작업자가 직접 구조 요청을 보냈습니다!")
            .setPriority(NotificationCompat.PRIORITY_MAX) // 중요도 최대
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(100, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "MANAGER_CHANNEL",
                "관리자 감시 서비스",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "작업자 비상 상황 알림"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                setLockscreenVisibility(Notification.VISIBILITY_PUBLIC)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, "MANAGER_CHANNEL")
            .setContentTitle("관리 시스템 가동 중")
            .setContentText("백그라운드에서 작업자 상태를 실시간 감시합니다.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .build()
        startForeground(1, notification)
    }

    override fun onDestroy() {
        statusRef?.removeEventListener(statusListener)
        sosTimeRef?.removeEventListener(sosTimeListener) // 추가
        ringtone?.stop()
        super.onDestroy()
    }
}