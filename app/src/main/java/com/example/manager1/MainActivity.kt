package com.example.manager1

import com.google.firebase.messaging.FirebaseMessaging
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.*
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.util.MarkerIcons

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var logAdapter: WorkLogAdapter
    private val workLogs = mutableListOf<WorkLogRequest>()

    // 상태 관리 변수
    private var isDialogShowing = false
    private var isChecking5Sec = false
    private var isAlarmTimerRunning = false
    private var ringtone: android.media.Ringtone? = null
    private var currentTiltRef: DatabaseReference? = null
    private var statusRef: DatabaseReference? = null
    private var isCooldownMode = false

    private lateinit var tvX: TextView
    private lateinit var tvY: TextView
    private lateinit var tvStatus: TextView

    // 네이버 지도 관련
    private var naverMap: NaverMap? = null
    private val workerMarker = Marker()

    // 🚨 [사고 발생] 알람 실행 및 확인 창 띄우기 (수정됨)
    private val alarmRunnable = Runnable {
        // 알람음 시작
        val soundUri = android.net.Uri.parse("android.resource://${packageName}/${R.raw.emergency_sound}")

        if (ringtone == null) {
            ringtone = RingtoneManager.getRingtone(applicationContext, soundUri)
        }
        ringtone?.play()


        tvStatus.text = "상태: 🚨 사고 발생!! (현장 즉시 확인)"
        tvStatus.setTextColor(Color.RED)


    }

    // 🕒 [10초 후] 작업자 앱에 알림 전송 로직 (30초 -> 10초로 수정됨)
    private val check30secRunnable = Runnable {
        statusRef?.setValue("CHECKING")
        tvStatus.text = "상태: ⏳ 작업자 확인 중 (20초 대기)"
        tvStatus.setTextColor(Color.parseColor("#FFA500"))
        Toast.makeText(this, "10초 경과: 작업자에게 확인 요청을 보냈습니다.", Toast.LENGTH_SHORT).show()

        handler.postDelayed(alarmRunnable, 20000L)
    }

    // [5초 후] 위험 확정 로직 (기울기 유지 시 실행)
    private val check5secRunnable = Runnable {
        isChecking5Sec = false
        isAlarmTimerRunning = true

        // ⭐ 10초 타이머로 변경 (기존 30000L -> 10000L)
        handler.postDelayed(check30secRunnable, 10000L)

        tvStatus.text = "상태: ⚠️ 위험 확정 (10초 후 작업자 확인)"
        tvStatus.setTextColor(Color.parseColor("#FF5722"))
    }

    // 센서 리스너 (원본 유지)
    // 센서 리스너 (수직 장착 기준 수정본)
    // 센서 리스너 (새로운 기준점 및 감도 수정본)
    // 센서 리스너 (감도 완화 및 안정화 버전)
    private val tiltListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if (isCooldownMode) return

            val rawX = snapshot.child("x").getValue(Float::class.java) ?: 0.0f
            val rawY = snapshot.child("y").getValue(Float::class.java) ?: 0.0f

            // 기준점은 제희님이 말씀하신 대로 유지 (10.1 / -0.9)
            val STAND_X = 10.1f
            val STAND_Y = -0.9f

            val calX = rawX - STAND_X
            val calY = rawY - STAND_Y

            handler.post {
                tvX.text = "X: %.1f".format(calX)
                tvY.text = "Y: %.1f".format(calY)
            }

            val absX = Math.abs(calX)
            val absY = Math.abs(calY)

            // ⭐ 4. 판단 로직 수정 (감도를 낮춤: 1.0f -> 3.0f)
            // 이제 기준점에서 약 20도 이상 확 기울어져야 감지가 시작됩니다.
            // 1.0f나 2.0f 정도의 미세한 움직임은 '정상'으로 무시합니다.
            if (absX > 5.0f || absY > 5.0f) {
                if (!isChecking5Sec && !isAlarmTimerRunning) {
                    isChecking5Sec = true
                    handler.postDelayed(check5secRunnable, 5000L)
                    tvStatus.text = "상태: ⚠️ 흔들림 감지 (5초 대기)"
                    tvStatus.setTextColor(Color.parseColor("#FFA500"))
                }
            }
            // ⭐ 5. 정상 복귀 기준 수정 (2.0f 미만으로 돌아오면 정상)
            // 너무 타이트하게 0.6f로 잡으면 복귀가 안 될 수 있으므로 2.0f로 여유를 줍니다.
            else if (absX < 2.0f && absY < 2.0f) {
                if (isChecking5Sec || isAlarmTimerRunning) {
                    resetAlerts()
                    statusRef?.setValue("NORMAL")
                    tvStatus.text = "상태: ✅ 정상 가동 중"
                    tvStatus.setTextColor(Color.parseColor("#2E7D32"))
                }
            }
        }
        override fun onCancelled(error: DatabaseError) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sdk = NaverMapSdk.getInstance(this)
        sdk.client = NaverMapSdk.NcpKeyClient("client-key")
        setContentView(R.layout.activity_main)

        tvX = findViewById(R.id.tv_x)
        tvY = findViewById(R.id.tv_y)
        tvStatus = findViewById(R.id.tv_status)

        val rvLogs = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_work_logs)
        logAdapter = WorkLogAdapter(workLogs)
        rvLogs.adapter = logAdapter
        rvLogs.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        // ⭐ [추가] 서버에서 데이터 가져오기
        fetchWorkLogs("wearable-001")

        addDummyLogs()

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result


                registerAdminToServer(token)
            }
        }


        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_fragment) as MapFragment?
            ?: MapFragment.newInstance().also {
                supportFragmentManager.beginTransaction().add(R.id.map_fragment, it).commit()
            }
        mapFragment.getMapAsync(this)

        val sensorPath = intent.getStringExtra("SENSOR_PATH") ?: "none"
        statusRef = FirebaseDatabase.getInstance().getReference("workers/w1/status")

        statusRef?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.getValue(String::class.java) ?: "NORMAL"

                if (status == "NORMAL" && (isAlarmTimerRunning || isChecking5Sec)) {
                    resetAlerts()
                    startCooldownTimer()
                    tvStatus.text = "상태: ✅ 작업자 확인 완료 (15초 휴식)"
                    tvStatus.setTextColor(Color.parseColor("#2E7D32"))
                }
                else if (status == "EMERGENCY") {
                    // 1. 타이머가 돌고 있었다면 -> 자동 감지 무응답 상황
                    if (isAlarmTimerRunning || isChecking5Sec) {
                        resetAlerts() // 타이머 중복 실행 방지
                        tvStatus.text = "상태: 🚨 자동 사고 감지 (무응답)"
                        tvStatus.setTextColor(Color.RED)
                        showAutoFallDialog() // 자동 전용 다이얼로그 호출!
                    }
                    // 2. 타이머가 없는데 EMERGENCY라면 -> 수동 SOS 상황
                    else {
                        tvStatus.text = "상태: 🆘 작업자 긴급 구조 요청!!"
                        tvStatus.setTextColor(Color.RED)
                        showDirectSosDialog() // 수동 전용 다이얼로그 호출!
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        /*val sosTimeRef = FirebaseDatabase.getInstance().getReference("workers/w1/last_sos_time")
        sosTimeRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lastTime = snapshot.getValue(Long::class.java) ?: 0L

                // 앱 실행 시점(0) 이후에 데이터가 갱신될 때마다 실행
                if (lastTime > 0) {
                    // 이미 EMERGENCY 상태더라도 버튼을 누를 때마다 소리가 다시 나고 팝업이 뜸
                    if (!isAlarmTimerRunning && !isChecking5Sec) {
                        tvStatus.text = "상태: 🆘 작업자 긴급 구조 요청!!"
                        tvStatus.setTextColor(Color.RED)
                        showDirectSosDialog()
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })*/


        if (sensorPath != "none") {
            val database = FirebaseDatabase.getInstance()
            val tiltRef = database.getReference(sensorPath)
            currentTiltRef = tiltRef
            database.getReference("system_control/isRunning").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.getValue(Boolean::class.java) == true) {
                        tiltRef.addValueEventListener(tiltListener)
                    } else {
                        stopMonitoring()
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
        val serviceIntent = android.content.Intent(this, ManagerService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onMapReady(map: NaverMap) {
        this.naverMap = map
        val workerRef = FirebaseDatabase.getInstance().getReference("workers/w1")
        workerRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lat = snapshot.child("location/latitude").getValue(Double::class.java)
                val lng = snapshot.child("location/longitude").getValue(Double::class.java)
                if (lat != null && lng != null) {
                    val pos = LatLng(lat, lng)
                    handler.post {
                        workerMarker.position = pos
                        workerMarker.map = naverMap
                        workerMarker.icon = MarkerIcons.RED
                        workerMarker.captionText = snapshot.child("name").getValue(String::class.java) ?: "작업자"
                        naverMap?.moveCamera(CameraUpdate.scrollTo(pos).animate(CameraAnimation.Easing))
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun resetAlerts() {
        handler.removeCallbacks(check5secRunnable)
        handler.removeCallbacks(check30secRunnable)
        handler.removeCallbacks(alarmRunnable)

        // 🚨 추가: 소리가 나고 있다면 확실히 끄고 변수를 비워줍니다.
        if (ringtone?.isPlaying == true) {
            ringtone?.stop()
        }
        ringtone = null // 다시 알람이 울릴 때 새로 불러오도록 초기화

        isChecking5Sec = false
        isAlarmTimerRunning = false
    }

    private fun stopMonitoring() {
        resetAlerts()
        currentTiltRef?.removeEventListener(tiltListener)
        tvStatus.text = "상태: ⏸️ 작업 중지됨"
        tvStatus.setTextColor(Color.GRAY)
    }

    override fun onDestroy() {
        super.onDestroy()
        resetAlerts()
        currentTiltRef?.removeEventListener(tiltListener)
    }
    // ⭐ 수동 SOS 전용 알림창 (클래스 하단에 추가)
    private fun showDirectSosDialog() {
        if (isDialogShowing) return // 이미 떠있으면 무시
        isDialogShowing = true

        handler.post { // UI 스레드 보장
            val soundUri = android.net.Uri.parse("android.resource://${packageName}/${R.raw.emergency_sound}")
            if (ringtone == null) {
                ringtone = RingtoneManager.getRingtone(applicationContext, soundUri)
            }
            ringtone?.play()

            val builder = AlertDialog.Builder(this)
            builder.setTitle("🆘 긴급 SOS 발신")
            builder.setMessage("작업자가 앱에서 직접 SOS 버튼을 눌렀습니다!")
            builder.setCancelable(false)
            builder.setPositiveButton("상황 확인 (알람 끄기)") { _, _ ->
                ringtone?.stop()
                statusRef?.setValue("NORMAL")
                FirebaseDatabase.getInstance().getReference("workers/w1/last_sos_time").setValue(0)

                isDialogShowing = false // 🚩 닫힐 때 방어막 해제
                Toast.makeText(this, "구조 요청 확인 완료.", Toast.LENGTH_SHORT).show()
            }
            builder.show()
        }
    }

    // 🕒 15초간 센서 감지를 중단시키는 함수
    private fun startCooldownTimer() {
        // 🚨 쿨다운 시작 전에 모든 예약된 알람/상태변경 작업을 싹 청소합니다.
        resetAlerts()

        isCooldownMode = true
        handler.postDelayed({
            isCooldownMode = false
            tvStatus.text = "상태: ✅ 정상 가동 중"
            Toast.makeText(this, "재감지를 시작합니다.", Toast.LENGTH_SHORT).show()
        }, 15000L)
    }

    private fun registerAdminToServer(fcmToken: String) {
        val api = RetrofitClient.instance

        // owner_type을 "ADMIN"으로 설정!
        val request = PushTokenRequest(owner_type = "ADMIN", token = fcmToken)

        api.registerPushToken(request).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    println("관리자 서버 등록 성공!")
                    // 관리자도 장비 알림을 받으려면 구독해야 합니다.
                    subscribeDeviceAsAdmin(fcmToken)
                }
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                println("관리자 등록 실패: ${t.message}")
            }
        })
    }

    private fun subscribeDeviceAsAdmin(fcmToken: String) {
        val api = RetrofitClient.instance
        val request = SubscribeRequest(token = fcmToken, role = "ADMIN") // role도 ADMIN

        api.subscribeDevice("wearable-001", request).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) println("관리자 장비 구독 완료!")
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                println("관리자 구독 실패: ${t.message}")
            }
        })
    }

    private fun sendEventToServer(eventType: String, severity: String) {
        val api = RetrofitClient.instance

        // 1. 파이어베이스에서 현재 작업자(w1)의 위치를 읽어옵니다.
        val workerRef = FirebaseDatabase.getInstance().getReference("workers/w1/location")
        workerRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val workerLat = snapshot.child("latitude").getValue(Double::class.java) ?: 37.5665
                val workerLng = snapshot.child("longitude").getValue(Double::class.java) ?: 126.9780

                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault())
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val currentTime = sdf.format(java.util.Date())

                val request = EventRequest(
                    device = DeviceInfo(device_key = "wearable-001", type = "WEARABLE"),
                    event = EventDetail(
                        type = eventType,
                        occurred_at = currentTime,
                        severity = severity
                    ),
                    // ⭐ 파이어베이스에서 가져온 작업자의 실제 위치를 넣습니다!
                    location = LocationData(lat = workerLat, lng = workerLng),
                    payload = emptyMap()
                )

                api.reportEvent(request).enqueue(object : Callback<Void> {
                    override fun onResponse(call: Call<Void>, response: Response<Void>) {
                        if (response.isSuccessful) Log.d("Docker", "🚀 서버 기록 성공")
                    }
                    override fun onFailure(call: Call<Void>, t: Throwable) {
                        Log.e("Docker", "❌ 서버 연결 실패")
                    }
                })
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // ⭐ [이 함수 전체를 복사해서 클래스 하단에 넣으세요]
    private fun fetchWorkLogs(deviceKey: String) {
        val api = RetrofitClient.instance

        // ApiService에 정의된 getWorkLogs를 호출합니다.
        api.getWorkLogs(deviceKey).enqueue(object : retrofit2.Callback<List<WorkLogRequest>> {
            override fun onResponse(
                call: retrofit2.Call<List<WorkLogRequest>>,
                response: retrofit2.Response<List<WorkLogRequest>>
            ) {
                if (response.isSuccessful) {
                    val fetchedLogs = response.body() ?: emptyList()

                    // 기존 리스트를 비우고 서버에서 받아온 새 데이터를 채웁니다.
                    workLogs.clear()
                    workLogs.addAll(fetchedLogs)

                    // 어댑터에게 데이터가 바뀌었으니 화면을 새로고침하라고 알립니다.
                    logAdapter.notifyDataSetChanged()

                    Log.d("AWS_SERVER", "로그 불러오기 성공: ${fetchedLogs.size}개")
                } else {
                    Log.e("AWS_SERVER", "서버 응답 에러: ${response.code()}")
                }
            }

            override fun onFailure(call: retrofit2.Call<List<WorkLogRequest>>, t: Throwable) {
                Log.e("AWS_SERVER", "네트워크 연결 실패: ${t.message}")
            }
        })
    }
    // fetchWorkLogs 실패 시나 테스트용으로 사용
    private fun addDummyLogs() {
        workLogs.add(WorkLogRequest("wearable-001", "START", "2026-04-25T09:00:00Z", LocationData(37.5, 126.9)))
        workLogs.add(WorkLogRequest("wearable-001", "END", "2026-04-25T18:00:00Z", LocationData(37.5, 126.9)))
        logAdapter.notifyDataSetChanged()
    }
    private fun showAutoFallDialog() {
        if (isDialogShowing) return // 이미 떠있으면 무시
        isDialogShowing = true

        handler.post { // UI 스레드 보장
            val soundUri = android.net.Uri.parse("android.resource://${packageName}/${R.raw.emergency_sound}")
            if (ringtone == null) {
                ringtone = RingtoneManager.getRingtone(applicationContext, soundUri)
            }
            ringtone?.play()

            val builder = AlertDialog.Builder(this)
            builder.setTitle("🚨 자동 사고 감지")
            builder.setMessage("작업자가 쓰러짐 감지 후 20초 동안 응답하지 않습니다!")
            builder.setCancelable(false)
            builder.setPositiveButton("상황 확인 (알람 끄기)") { _, _ ->
                ringtone?.stop()
                statusRef?.setValue("NORMAL")

                isDialogShowing = false // 🚩 닫힐 때 방어막 해제
                Toast.makeText(this, "사고 확인 완료", Toast.LENGTH_SHORT).show()
            }
            builder.show()
        }
    }
}
