package com.example.manager1

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

    // 상태 관리 변수
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
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        if (ringtone == null) {
            ringtone = RingtoneManager.getRingtone(applicationContext, uri)
        }
        ringtone?.play()

        tvStatus.text = "상태: 🚨 사고 발생!! (현장 즉시 확인)"
        tvStatus.setTextColor(Color.RED)

        // ⭐ 관리자용 확인 다이얼로그 띄우기
        val builder = AlertDialog.Builder(this)
        builder.setTitle("🚨 긴급 사고 발생")
        builder.setMessage("작업자가 20초 동안 응답하지 않습니다!\n현장 상태를 즉시 확인하세요.")
        builder.setCancelable(false) // 버튼을 눌러야만 닫힘
        builder.setPositiveButton("상황 확인 (알람 끄기)") { _, _ ->
            ringtone?.stop() // 알람음 중지
            Toast.makeText(this, "알람을 종료합니다. 후속 조치를 취해주세요.", Toast.LENGTH_SHORT).show()
        }
        builder.show()
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
    private val tiltListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if (isCooldownMode) return
            val rawX = snapshot.child("x").getValue(Float::class.java) ?: 0.0f
            val rawY = snapshot.child("y").getValue(Float::class.java) ?: 0.0f
            val calX = rawX - 1.5f
            val calY = rawY

            tvX.text = "%.1f°".format(calX)
            tvY.text = "%.1f°".format(calY)

            val absX = Math.abs(calX)
            val absY = Math.abs(calY)

            if (absX > 7.0f || absY > 7.0f) {
                if (!isChecking5Sec && !isAlarmTimerRunning) {
                    isChecking5Sec = true
                    handler.postDelayed(check5secRunnable, 5000L)
                    tvStatus.text = "상태: ⚠️ 흔들림 감지 (5초 대기)"
                    tvStatus.setTextColor(Color.parseColor("#FFA500"))
                }
            } else if (absX < 3.0f && absY < 3.0f) {
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
        sdk.client = NaverMapSdk.NcpKeyClient("YOUR_CLIENT_ID")
        setContentView(R.layout.activity_main)

        tvX = findViewById(R.id.tv_x)
        tvY = findViewById(R.id.tv_y)
        tvStatus = findViewById(R.id.tv_status)


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

                // [수정] 알람 타이머가 돌고 있거나, 5초 대기 중일 때 '정상'이 되면 쿨다운 시작
                if (status == "NORMAL" && (isAlarmTimerRunning || isChecking5Sec)) {
                    resetAlerts()

                    // 🚨 [핵심 추가] 15초간 센서 감지를 멈추는 함수를 실행합니다!
                    startCooldownTimer()

                    tvStatus.text = "상태: ✅ 작업자 확인 완료 (15초 휴식)"
                    tvStatus.setTextColor(Color.parseColor("#2E7D32"))
                }
                else if (status == "EMERGENCY") {
                    // ... (기존 EMERGENCY 로직과 동일) ...
                    if (isAlarmTimerRunning || isChecking5Sec) {
                        tvStatus.text = "상태: 🚨 자동 사고 감지 (무응답)"
                        tvStatus.setTextColor(Color.RED)
                        handler.post(alarmRunnable)
                    } else {
                        tvStatus.text = "상태: 🆘 작업자 긴급 구조 요청!!"
                        tvStatus.setTextColor(Color.RED)
                        showDirectSosDialog()
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        val sosTimeRef = FirebaseDatabase.getInstance().getReference("workers/w1/last_sos_time")
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
        })


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
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        if (ringtone == null) {
            ringtone = RingtoneManager.getRingtone(applicationContext, uri)
        }
        ringtone?.play()

        val builder = AlertDialog.Builder(this)
        builder.setTitle("🆘 긴급 SOS 발신")
        builder.setMessage("작업자가 앱에서 직접 SOS 버튼을 눌렀습니다!")
        builder.setCancelable(false)
        builder.setPositiveButton("상황 확인 (알람 끄기)") { _, _ ->
            ringtone?.stop()

            // 🚨 [중요] 확인을 눌렀으므로 파이어베이스 상태를 NORMAL로 변경
            // 이렇게 해야 다시 화면에 들어왔을 때 알람이 울리지 않습니다.
            statusRef?.setValue("NORMAL")

            // 시간 값도 초기화하고 싶다면 (선택사항)
            FirebaseDatabase.getInstance().getReference("workers/w1/last_sos_time").setValue(0)

            Toast.makeText(this, "구조 요청 확인 완료. 상태가 정상으로 복구되었습니다.", Toast.LENGTH_SHORT).show()
        }
        builder.show()
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
}