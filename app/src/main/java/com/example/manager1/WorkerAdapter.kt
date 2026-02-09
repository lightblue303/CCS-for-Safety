package com.example.manager1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class WorkerAdapter(
    private var workerList: MutableList<Worker>, // MutableList여야 추가/삭제가 가능함
    private val onClick: (Worker) -> Unit
) : RecyclerView.Adapter<WorkerAdapter.WorkerViewHolder>() {

    inner class WorkerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvWorkerName)
        val tvLocation: TextView = view.findViewById(R.id.tvWorkerLocation)
        val card: View = view.findViewById(R.id.workerCard)

        fun bind(worker: Worker) {
            tvName.text = worker.name
            tvLocation.text = worker.location
            card.setOnClickListener { onClick(worker) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_worker, parent, false)
        return WorkerViewHolder(view)
    }

    override fun onBindViewHolder(holder: WorkerViewHolder, position: Int) {
        holder.bind(workerList[position])
    }

    override fun getItemCount() = workerList.size

    // ⭐ 데이터를 실시간으로 갈아끼워주는 함수
    fun updateList(newList: List<Worker>) {
        workerList.clear()
        workerList.addAll(newList)
        notifyDataSetChanged() // 리스트 화면 갱신
    }
}