package com.stacca.app.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.stacca.app.R
import com.stacca.app.data.PreferencesManager
import com.stacca.app.ui.adapter.WorkLogAdapter

/**
 * Activity che mostra lo storico dei work log.
 */
class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val prefs = PreferencesManager(this)
        val logs = prefs.getWorkLogs()

        val recycler = findViewById<RecyclerView>(R.id.recyclerHistory)
        val emptyLayout = findViewById<View>(R.id.layoutEmpty)

        if (logs.isEmpty()) {
            emptyLayout.visibility = View.VISIBLE
            recycler.visibility = View.GONE
        } else {
            emptyLayout.visibility = View.GONE
            recycler.visibility = View.VISIBLE
            recycler.layoutManager = LinearLayoutManager(this)
            recycler.adapter = WorkLogAdapter(logs)
        }
    }
}
