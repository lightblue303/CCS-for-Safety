package com.example.manager1

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class WorkLogAdapter(private var logs: List<WorkLogRequest>) :
    RecyclerView.Adapter<WorkLogAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvStatus: TextView = view.findViewById(R.id.tv_log_status)
        val tvTime: TextView = view.findViewById(R.id.tv_log_time)
        val tvDevice: TextView = view.findViewById(R.id.tv_log_device)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_work_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val log = logs[position]
        holder.tvTime.text = log.occurred_at
        holder.tvDevice.text = "Device: ${log.device_key}"

        // 상태에 따라 색상과 텍스트 변경
        if (log.status == "START") {
            holder.tvStatus.text = "시작"
            holder.tvStatus.background.setTint(Color.parseColor("#4CAF50")) // 초록
        } else {
            holder.tvStatus.text = "종료"
            holder.tvStatus.background.setTint(Color.parseColor("#F44336")) // 빨강
        }
    }

    override fun getItemCount() = logs.size

    // 새로운 데이터가 들어오면 리스트를 갱신하는 함수
    fun updateData(newLogs: List<WorkLogRequest>) {
        logs = newLogs
        notifyDataSetChanged()
    }
}