package com.stacca.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.stacca.app.R
import com.stacca.app.data.WorkLog
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter per la lista dei work log nello storico.
 * Supporta long-press per eliminare un elemento.
 */
class WorkLogAdapter(
    private val logs: List<WorkLog>,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<WorkLogAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMood: TextView = view.findViewById(R.id.tvMood)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val tvCompleted: TextView = view.findViewById(R.id.tvCompleted)
        val tvTodo: TextView = view.findViewById(R.id.tvTodo)
        val tvOvertime: TextView = view.findViewById(R.id.tvOvertime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_work_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val log = logs[position]

        holder.tvMood.text = log.mood
        holder.tvTime.text = log.time
        holder.tvCompleted.text = log.completed.ifEmpty { "-" }
        holder.tvTodo.text = log.todo.ifEmpty { "-" }

        // Mostra straordinario
        if (log.overtimeMinutes > 0) {
            holder.tvOvertime.visibility = View.VISIBLE
            holder.tvOvertime.text = "+${log.overtimeMinutes}min"
        } else {
            holder.tvOvertime.visibility = View.GONE
        }

        // Formatta la data in modo leggibile
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("EEEE d MMM", Locale.ITALIAN)
            val date = inputFormat.parse(log.date)
            holder.tvDate.text = if (date != null) {
                outputFormat.format(date).replaceFirstChar { it.uppercase() }
            } else {
                log.date
            }
        } catch (e: Exception) {
            holder.tvDate.text = log.date
        }

        // Long press per eliminare
        holder.itemView.setOnLongClickListener {
            onDeleteClick(position)
            true
        }
    }

    override fun getItemCount() = logs.size
}
