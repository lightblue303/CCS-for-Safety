package com.example.worker1

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.firebase.database.*

class SafetyService : Service() {

    private val CHANNEL_ID = "safety_service_channel"
    private var statusRef: DatabaseReference? = null
    private var statusListener: ValueEventListener? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. 서비스가 죽지 않게 상단에 고정 알림
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("안전 모니터링 작동 중")
            .setContentText("실시간으로 상태를 감시하고 있습니다.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW) // 상주 알림은 조용하게
            .build()

        startForeground(101, notification)

        // 2. 파이어베이스 감시 시작
        statusRef = FirebaseDatabase.getInstance().getReference("workers/w1/status")
        statusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.getValue(String::class.java) ?: "NORMAL"
                if (status == "CHECKING") {
                    // 위험 감지 시 알림 전송
                    sendSafetyAlertNotification()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        statusRef?.addValueEventListener(statusListener!!)

        return START_STICKY
    }

    private fun sendSafetyAlertNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val alertNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⚠️ 안전 확인 요청")
            .setContentText("기기 이상 감지! 지금 바로 확인 버튼을 눌러주세요.")
            // 소리/진동 발생
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1, alertNotification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Safety Monitoring",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "안전 상태 모니터링 및 비상 알림"
                enableVibration(true) // 진동 활성화
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC // 잠금화면 노출
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        statusListener?.let { statusRef?.removeEventListener(it) }
        super.onDestroy()
    }
}