package com.stacca.app.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.stacca.app.R
import com.stacca.app.data.PreferencesManager
import com.stacca.app.data.WorkLog
import com.stacca.app.ui.adapter.WorkLogAdapter

/**
 * Activity che mostra lo storico dei work log con statistiche aggregate.
 */
class HistoryActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var recycler: RecyclerView
    private lateinit var emptyLayout: View
    private lateinit var cardStats: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        prefs = PreferencesManager(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        recycler = findViewById(R.id.recyclerHistory)
        emptyLayout = findViewById(R.id.layoutEmpty)
        cardStats = findViewById(R.id.cardStats)

        loadData()
    }

    private fun loadData() {
        val logs = prefs.getWorkLogs()

        if (logs.isEmpty()) {
            emptyLayout.visibility = View.VISIBLE
            recycler.visibility = View.GONE
            cardStats.visibility = View.GONE
        } else {
            emptyLayout.visibility = View.GONE
            recycler.visibility = View.VISIBLE
            cardStats.visibility = View.VISIBLE

            recycler.layoutManager = LinearLayoutManager(this)
            recycler.adapter = WorkLogAdapter(logs) { position ->
                showDeleteDialog(logs, position)
            }

            updateStats(logs)
        }
    }

    private fun updateStats(logs: List<WorkLog>) {
        // Numero di giorni registrati
        findViewById<TextView>(R.id.tvStatDays).text = logs.size.toString()

        // Straordinario totale
        val totalOvertime = logs.sumOf { it.overtimeMinutes }
        val totalHours = totalOvertime / 60
        val totalMins = totalOvertime % 60
        findViewById<TextView>(R.id.tvStatTotalOvertime).text =
            if (totalHours > 0) "${totalHours}h ${totalMins}m" else "${totalMins}m"

        // Media straordinario
        val avgOvertime = if (logs.isNotEmpty()) totalOvertime / logs.size else 0
        findViewById<TextView>(R.id.tvStatAvgOvertime).text = "${avgOvertime}m"

        // Umore più frequente
        val topMood = logs.groupingBy { it.mood }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key ?: "🙂"
        findViewById<TextView>(R.id.tvStatMood).text = topMood
    }

    private fun showDeleteDialog(logs: List<WorkLog>, position: Int) {
        val log = logs[position]
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.history_delete_title))
            .setMessage(getString(R.string.history_delete_message, log.date))
            .setPositiveButton("Elimina") { _, _ ->
                prefs.deleteWorkLog(position)
                Toast.makeText(this, getString(R.string.history_deleted),
                    Toast.LENGTH_SHORT).show()
                loadData() // Ricarica
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }
}
