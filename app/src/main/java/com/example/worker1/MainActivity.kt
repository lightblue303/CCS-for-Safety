package com.example.worker1

import com.google.firebase.messaging.FirebaseMessaging
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.*
import com.google.firebase.database.*
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var isPopupShowing = false
    private var responseTimer: Timer? = null
    private var safetyDialog: AlertDialog? = null
    private val CHANNEL_ID = "safety_alert_channel"

    private var isSendingSos = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                println("FCM 토큰 가져오기 실패: ${task.exception}")
                return@addOnCompleteListener
            }

            val token = task.result

            registerToServer(token)
        }

        createNotificationChannel()

        val database = FirebaseDatabase.getInstance()
        val controlRef = database.getReference("system_control/isRunning")
        val locationRef = database.getReference("workers/w1/location")
        val statusRef = database.getReference("workers/w1/status")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val tvWorkStatus = findViewById<TextView>(R.id.tvWorkStatus)
        val btnStart = findViewById<LinearLayout>(R.id.btnStart)
        val btnStop = findViewById<LinearLayout>(R.id.btnStop)
        val btnPause = findViewById<LinearLayout>(R.id.btnPause)

        // ⭐ SOS 버튼
        val btnSos = findViewById<LinearLayout>(R.id.btnSos)

        // 앱 시작 시 파이어베이스에서 마지막 상태(isRunning) 읽어오기 (기존 유지)
        controlRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isRunning = snapshot.getValue(Boolean::class.java) ?: false
                if (isRunning) {
                    if (checkLocationPermission()) {
                        startLocationUpdates()
                        val serviceIntent = Intent(this@MainActivity, SafetyService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(serviceIntent)
                        } else {
                            startService(serviceIntent)
                        }
                        tvWorkStatus.text = "현재 상태: 📡 전송 중"
                        tvWorkStatus.setTextColor(Color.parseColor("#4CAF50"))
                    }
                } else {
                    tvWorkStatus.text = "현재 상태: ⏸️ 중단됨"
                    tvWorkStatus.setTextColor(Color.parseColor("#F44336"))
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })


        statusRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.value?.toString() ?: "NORMAL"
                if (status == "CHECKING" && !isPopupShowing) {
                    sendSafetyNotification()
                    showSafetyCheckDialog(statusRef)
                } else if (status == "NORMAL" && isPopupShowing) {
                    dismissSafetyDialog()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val locationData = HashMap<String, Any>()
                    locationData["latitude"] = location.latitude
                    locationData["longitude"] = location.longitude
                    locationRef.setValue(locationData)
                }
            }
        }

        btnStart.setOnClickListener {
            if (checkLocationPermission()) {
                controlRef.setValue(true).addOnSuccessListener {
                    startLocationUpdates()
                    val serviceIntent = Intent(this, SafetyService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                    statusRef.setValue("NORMAL")

                    sendWorkLogToServer("START")
                    tvWorkStatus.text = "현재 상태: 📡 전송 중"
                    tvWorkStatus.setTextColor(Color.parseColor("#4CAF50"))
                    Toast.makeText(this, "✅ 모니터링 시작", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnPause.setOnClickListener {
            // 1. [파이어베이스] 전체 시스템 제어 상태를 false로 (관리자 앱 연동)
            controlRef.setValue(false).addOnSuccessListener {

                // 2. [데이터 전송 중단] 위치 업데이트 및 센서 서비스 중지 (핵심!)
                stopLocationUpdates()
                stopService(Intent(this, SafetyService::class.java))

                // 3. [파이어베이스] 작업자 개별 상태를 PAUSED로 변경
                statusRef.setValue("PAUSED")

                // 4. [서버 DB 기록] 우리 백엔드 서버에 "휴식 시작" 로그 전송
                // sendSosEventToServer를 재활용하여 이벤트를 쏩니다.
                sendCustomEventToServer("WORK_PAUSE", "info")

                // 5. [UI 업데이트]
                tvWorkStatus.text = "현재 상태: ☕ 작업 중단 (휴식)"
                tvWorkStatus.setTextColor(Color.parseColor("#FFA000")) // 주황색

                dismissSafetyDialog() // 혹시 떠 있을지 모를 팝업 닫기
                Toast.makeText(this, "⏸️ 휴식이 기록되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        btnStop.setOnClickListener {
            controlRef.setValue(false).addOnSuccessListener {
                stopLocationUpdates()
                stopService(Intent(this, SafetyService::class.java))
                statusRef.setValue("OFFLINE")
                dismissSafetyDialog()

                sendWorkLogToServer("END")

                tvWorkStatus.text = "현재 상태: ⏸️ 중단됨"
                tvWorkStatus.setTextColor(Color.parseColor("#F44336"))
                Toast.makeText(this, "🛑 모니터링 종료", Toast.LENGTH_SHORT).show()
            }
        }


        btnSos.setOnClickListener {
            // ⭐ 이미 전송 중이면 무시
            if (isSendingSos) return@setOnClickListener

            isSendingSos = true // 잠금
            btnSos.isEnabled = false // 버튼 클릭 막기

            val workerRef = FirebaseDatabase.getInstance().getReference("workers/w1")
            val updates = HashMap<String, Any>()
            updates["status"] = "EMERGENCY"
            updates["last_sos_time"] = com.google.firebase.database.ServerValue.TIMESTAMP

            workerRef.updateChildren(updates).addOnSuccessListener {
                sendSosEventToServer("SOS_BUTTON")
                showSosConfirmDialog()

                // ⭐ 3초 후에 다시 누를 수 있게 해제 (실수로 여러 번 찍히는 것 방지)
                Handler(Looper.getMainLooper()).postDelayed({
                    isSendingSos = false
                    btnSos.isEnabled = true
                }, 3000L)
            }
        }
    }


    private fun showSosConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("🚨 SOS 발신 완료")
            .setMessage("긴급 상황이 관리자에게 전송되었습니다.\n현장에서 안전하게 대기해 주세요.")
            .setCancelable(false)
            .setPositiveButton("확인") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "안전 확인 알림"
            val importance = NotificationManager.IMPORTANCE_HIGH

            // 1. 우리가 추가한 m4a 파일의 경로를 가져옵니다.
            val soundUri = android.net.Uri.parse("android.resource://${packageName}/${R.raw.emergency_sound}")

            // 2. 소리의 속성을 설정합니다. (알림용, 사이렌 같은 소리임을 명시)
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = "위험 상황 발생 시 울리는 비상 알림음입니다."

                // 3. ⭐ 핵심: 채널에 커스텀 소리를 입힙니다.
                setSound(soundUri, audioAttributes)

                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)

            // 4. ⭐ 중요: 안드로이드는 한 번 생성된 채널 설정을 잘 안 바꿉니다.
            // 그래서 기존 채널을 한 번 삭제하고 다시 만들어야 소리가 확실히 바뀝니다.
            notificationManager.deleteNotificationChannel(CHANNEL_ID)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendSafetyNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⚠️ 안전 확인 요청")
            .setContentText("기기 이상 감지! 앱에서 확인 버튼을 눌러주세요.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notify(1, builder.build())
            }
        }
    }

    private fun showSafetyCheckDialog(statusRef: DatabaseReference) {
        if (isPopupShowing) return
        isPopupShowing = true

        Handler(Looper.getMainLooper()).post {
            // 1. 빌더 생성 시 버튼 리스너를 미리 변수로 뺍니다.
            val builder = AlertDialog.Builder(this)
            builder.setTitle("⚠️ 안전 확인")
            builder.setMessage("위험이 감지되었습니다. 20초 내에 응답하지 않으면 관리자에게 비상 호출이 전송됩니다!")
            builder.setCancelable(false)

            builder.setPositiveButton("정상 (I'm OK)") { _, _ ->
                statusRef.setValue("NORMAL")
                cleanUpSafetyCheck()
            }

            safetyDialog = builder.create()
            safetyDialog?.show()

            responseTimer?.cancel()
            responseTimer = Timer()
            responseTimer?.schedule(object : TimerTask() {
                override fun run() {
                    runOnUiThread {
                        if (isPopupShowing && safetyDialog?.isShowing == true) {
                            // 사고 확정!
                            statusRef.setValue("EMERGENCY")

                            if (!isSendingSos) {
                                isSendingSos = true
                                sendSosEventToServer("FALL_CONFIRMED")
                                Handler(Looper.getMainLooper()).postDelayed({ isSendingSos = false }, 3000L)
                            }

                            // ⭐ 핵심 수정: 기존 창의 텍스트만 바꾸고, 클릭 시 중복 dismiss 방지
                            safetyDialog?.setTitle("🚨 비상 호출 전송됨")
                            safetyDialog?.setMessage("20초간 응답이 없어 관리자에게 알림을 전송했습니다.")

                            // 버튼 텍스트와 동작을 변경 (이미 열린 다이얼로그의 버튼 참조)
                            val posButton = safetyDialog?.getButton(AlertDialog.BUTTON_POSITIVE)
                            posButton?.text = "확인"
                            posButton?.setOnClickListener {
                                // 직접 닫지 않고 상태만 정리 (버튼 기본 동작이 창을 닫음)
                                isPopupShowing = false
                                responseTimer?.cancel()
                                responseTimer = null
                                NotificationManagerCompat.from(this@MainActivity).cancel(1)
                                safetyDialog?.dismiss()
                            }
                        }
                    }
                }
            }, 20000)
        }
    }

    private fun dismissSafetyDialog() {
        safetyDialog?.dismiss()
        responseTimer?.cancel()
        isPopupShowing = false
        NotificationManagerCompat.from(this).cancel(1)
    }

    private fun checkLocationPermission(): Boolean {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1000)
            return false
        }
        return true
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun registerToServer(fcmToken: String) {
        val api = RetrofitClient.instance

        // 1단계: 서버에 내 FCM 토큰 등록 ("나 작업자야!")
        val tokenRequest = PushTokenRequest(owner_type = "WORKER", token = fcmToken)
        api.registerPushToken(tokenRequest).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    println("서버 등록 성공!")
                    // 2단계: 성공하면 바로 특정 장비(wearable-001) 구독 신청
                    subscribeToDevice(fcmToken)
                }
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                println("서버 등록 실패: ${t.message}")
            }
        })
    }

    private fun subscribeToDevice(fcmToken: String) {
        val api = RetrofitClient.instance
        val subRequest = SubscribeRequest(token = fcmToken, role = "WORKER")

        // 친구 매뉴얼의 wearable-001 장비를 구독
        api.subscribeDevice("wearable-001", subRequest).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) println("장비 구독 완료!")
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                println("구독 실패: ${t.message}")
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun sendSosEventToServer(eventType: String) {
        val api = RetrofitClient.instance

        // ⭐ 현재 위치를 새로 요청해서 가져오는 방식 (더 확실함)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build()

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                // 위치를 성공적으로 가져오면 실제 값을, 실패하면 기본값을 사용
                val currentLat = location?.latitude ?: 37.5665
                val currentLng = location?.longitude ?: 126.9780



                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault())
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val currentTime = sdf.format(java.util.Date())

                val request = EventRequest(
                    device = DeviceInfo(device_key = "wearable-001", type = "WEARABLE"),
                    event = EventDetail(
                        type = eventType,
                        occurred_at = currentTime,
                        severity = "critical"
                    ),
                    location = LocationData(lat = currentLat, lng = currentLng),
                    payload = emptyMap()
                )

                api.reportEvent(request).enqueue(object : Callback<Void> {
                    override fun onResponse(call: Call<Void>, response: Response<Void>) {
                        if (response.isSuccessful) println("🚀 서버 전송 성공!")
                    }
                    override fun onFailure(call: Call<Void>, t: Throwable) {
                        println("❌ 서버 전송 실패")
                    }
                })
            }
    }

    // 클래스 맨 아래쪽에 추가해 두면 편리합니다.
    @SuppressLint("MissingPermission")
    private fun sendCustomEventToServer(eventType: String, severity: String) {
        val api = RetrofitClient.instance

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                val currentLat = location?.latitude ?: 37.5665
                val currentLng = location?.longitude ?: 126.9780

                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault())
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val currentTime = sdf.format(java.util.Date())

                val request = EventRequest(
                    device = DeviceInfo(device_key = "wearable-001", type = "WEARABLE"),
                    event = EventDetail(
                        type = eventType, // "WORK_START", "WORK_PAUSE", "WORK_END" 등
                        occurred_at = currentTime,
                        severity = severity // "info" 또는 "critical"
                    ),
                    location = LocationData(lat = currentLat, lng = currentLng),
                    payload = emptyMap()
                )

                api.reportEvent(request).enqueue(object : Callback<Void> {
                    override fun onResponse(call: Call<Void>, response: Response<Void>) {
                        if (response.isSuccessful) println("🚀 서버 DB 기록 성공 ($eventType)")
                    }
                    override fun onFailure(call: Call<Void>, t: Throwable) {
                        println("❌ 서버 DB 전송 실패")
                    }
                })
            }
    }

    // [추가] 출퇴근 로그 전송 전용 함수 (관리자 패널 로그용)
    @SuppressLint("MissingPermission")
    private fun sendWorkLogToServer(status: String) {
        val api = RetrofitClient.instance

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                val currentLat = location?.latitude ?: 37.5665
                val currentLng = location?.longitude ?: 126.9780

                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault())
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val currentTime = sdf.format(java.util.Date())

                // 친구와 약속한 데이터 구조 (ApiService에 정의된 대로)
                val request = WorkLogRequest(
                    device_key = "wearable-001",
                    status = status, // "START" 또는 "END"
                    occurred_at = currentTime,
                    location = LocationData(lat = currentLat, lng = currentLng)
                )

                // ⭐ api/v1/worker-status 로 데이터를 쏩니다.
                api.reportWorkLog(request).enqueue(object : Callback<Void> {
                    override fun onResponse(call: Call<Void>, response: Response<Void>) {
                        if (response.isSuccessful) {
                            android.util.Log.d("WORKER_LOG", "✅ 로그 전송 성공 ($status)")
                        } else {
                            android.util.Log.e("WORKER_LOG", "❌ 서버 응답 실패: ${response.code()}")
                        }
                    }
                    override fun onFailure(call: Call<Void>, t: Throwable) {
                        android.util.Log.e("WORKER_LOG", "❌ 네트워크 에러: ${t.message}")
                    }
                })
            }
    }

    private fun cleanUpSafetyCheck() {
        isPopupShowing = false
        responseTimer?.cancel()
        responseTimer = null
        safetyDialog?.dismiss()
        NotificationManagerCompat.from(this).cancel(1)
    }


}
