package com.example.worker1

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        createNotificationChannel()

        val database = FirebaseDatabase.getInstance()
        val controlRef = database.getReference("system_control/isRunning")
        val locationRef = database.getReference("workers/w1/location")
        val statusRef = database.getReference("workers/w1/status")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val tvWorkStatus = findViewById<TextView>(R.id.tvWorkStatus)
        val btnStart = findViewById<LinearLayout>(R.id.btnStart)
        val btnStop = findViewById<LinearLayout>(R.id.btnStop)

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
                    tvWorkStatus.text = "현재 상태: 📡 전송 중"
                    tvWorkStatus.setTextColor(Color.parseColor("#4CAF50"))
                    Toast.makeText(this, "✅ 모니터링 시작", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnStop.setOnClickListener {
            controlRef.setValue(false).addOnSuccessListener {
                stopLocationUpdates()
                stopService(Intent(this, SafetyService::class.java))
                statusRef.setValue("OFFLINE")
                dismissSafetyDialog()
                tvWorkStatus.text = "현재 상태: ⏸️ 중단됨"
                tvWorkStatus.setTextColor(Color.parseColor("#F44336"))
                Toast.makeText(this, "🛑 모니터링 종료", Toast.LENGTH_SHORT).show()
            }
        }


        btnSos.setOnClickListener {
            val workerRef = FirebaseDatabase.getInstance().getReference("workers/w1")

            val updates = HashMap<String, Any>()
            updates["status"] = "EMERGENCY"
            updates["last_sos_time"] = com.google.firebase.database.ServerValue.TIMESTAMP

            workerRef.updateChildren(updates).addOnSuccessListener {
                showSosConfirmDialog()
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
            val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH)
            val notificationManager = getSystemService(NotificationManager::class.java)
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
        isPopupShowing = true
        Handler(Looper.getMainLooper()).post {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("⚠️ 안전 확인")


            builder.setMessage("위험 기울기가 감지되었습니다. 20초 내에 응답하지 않으면 관리자에게 비상 호출이 전송됩니다!")

            builder.setCancelable(false)
            builder.setPositiveButton("정상 (I'm OK)") { _, _ ->
                statusRef.setValue("NORMAL")
                isPopupShowing = false
                responseTimer?.cancel()
                NotificationManagerCompat.from(this).cancel(1)
            }
            safetyDialog = builder.create()
            safetyDialog?.show()

            responseTimer = Timer()
            responseTimer?.schedule(object : TimerTask() {
                override fun run() {
                    runOnUiThread {
                        if (isPopupShowing) {
                            safetyDialog?.dismiss()
                            statusRef.setValue("EMERGENCY")
                            isPopupShowing = false
                        }
                    }
                }
            }, 20000) // 20초 대기 후 EMERGENCY로 변경
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
}