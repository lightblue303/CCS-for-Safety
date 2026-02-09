package com.example.manager1

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*

class WorkerListActivity : AppCompatActivity() {
    private lateinit var adapter: WorkerAdapter
    private val workerList = mutableListOf<Worker>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_worker_list)

        val recyclerView = findViewById<RecyclerView>(R.id.rvWorkerList)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // 초기 빈 리스트로 어댑터 설정
        adapter = WorkerAdapter(workerList) { worker ->
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("WORKER_NAME", worker.name)
            intent.putExtra("SENSOR_PATH", worker.sensorPath)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        // ⭐ Firebase 연동
        val database = FirebaseDatabase.getInstance()
        val workersRef = database.getReference("workers")

        workersRef.addValueEventListener(object : ValueEventListener {
            // WorkerListActivity.kt 의 onDataChange 부분
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempList = mutableListOf<Worker>()
                for (data in snapshot.children) {
                    try {
                        // .value.toString()을 써서 일단 가져온 뒤 숫자로 바꿉니다. (가장 안전)
                        val idStr = data.child("id").value?.toString() ?: "0"
                        val id = idStr.toIntOrNull() ?: 0

                        val name = data.child("name").value?.toString() ?: ""
                        val location = data.child("location").value?.toString() ?: ""
                        val sensorPath = data.child("sensorPath").value?.toString() ?: "none"

                        tempList.add(Worker(id, name, location, sensorPath))
                    } catch (e: Exception) {
                        android.util.Log.e("DATA_ERROR", "데이터 변환 중 에러: ${e.message}")
                    }
                }
                adapter.updateList(tempList)
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }
}