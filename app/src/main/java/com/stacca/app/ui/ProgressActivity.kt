package com.stacca.app.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.stacca.app.R
import com.stacca.app.data.PreferencesManager
import com.stacca.app.data.WorkLog
import com.stacca.app.notifications.NotificationHelper
import com.stacca.app.receivers.AlarmReceiver
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity per registrare i progressi lavorativi.
 * L'utente può inserire cosa ha completato e cosa deve fare domani.
 */
class ProgressActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private var selectedMood: String = "🙂"
    private var overtimeMinutes: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_progress)

        prefs = PreferencesManager(this)
        overtimeMinutes = intent.getIntExtra("overtime_minutes", 0)

        setupDate()
        setupMoodSelector()
        setupSaveButton()
    }

    private fun setupDate() {
        val dateFormat = SimpleDateFormat("EEEE d MMMM yyyy", Locale.ITALIAN)
        findViewById<TextView>(R.id.tvDate).text = dateFormat.format(Date())
    }

    private fun setupMoodSelector() {
        val moods = listOf(
            Pair(R.id.mood1, "😫"),
            Pair(R.id.mood2, "😐"),
            Pair(R.id.mood3, "🙂"),
            Pair(R.id.mood4, "😄"),
            Pair(R.id.mood5, "🚀")
        )

        moods.forEach { (viewId, emoji) ->
            findViewById<TextView>(viewId).apply {
                setOnClickListener {
                    selectedMood = emoji
                    // Evidenzia la selezione
                    moods.forEach { (id, _) ->
                        findViewById<TextView>(id).alpha = 0.4f
                    }
                    this.alpha = 1.0f
                    this.scaleX = 1.3f
                    this.scaleY = 1.3f

                    // Ripristina le scale degli altri
                    moods.filter { it.first != viewId }.forEach { (id, _) ->
                        findViewById<TextView>(id).scaleX = 1.0f
                        findViewById<TextView>(id).scaleY = 1.0f
                    }
                }
            }
        }

        // Seleziona il default
        findViewById<TextView>(R.id.mood3).performClick()
    }

    private fun setupSaveButton() {
        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            val completed = findViewById<TextInputEditText>(R.id.etCompleted)
                .text?.toString()?.trim() ?: ""
            val todo = findViewById<TextInputEditText>(R.id.etTodo)
                .text?.toString()?.trim() ?: ""

            if (completed.isEmpty() && todo.isEmpty()) {
                Toast.makeText(this,
                    "Scrivi almeno qualcosa! Non hai fatto NULLA oggi?! 🤔",
                    Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val now = Date()

            val workLog = WorkLog(
                date = dateFormat.format(now),
                time = timeFormat.format(now),
                completed = completed,
                todo = todo,
                mood = selectedMood,
                overtimeMinutes = overtimeMinutes
            )

            prefs.saveWorkLog(workLog)

            // Cancella allarmi e notifiche
            AlarmReceiver.cancelAlarm(this)
            NotificationHelper(this).cancelAll()

            // Riprogramma per domani
            if (prefs.isAlarmActive) {
                AlarmReceiver.scheduleAlarm(this, prefs.endHour, prefs.endMinute)
            }

            Toast.makeText(this, getString(R.string.progress_saved),
                Toast.LENGTH_LONG).show()

            finish()
        }
    }
}
