package com.stacca.app.data

import android.content.Context
import android.content.SharedPreferences
import com.stacca.app.data.WorkLog
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gestisce le preferenze dell'app e lo storage dei work log.
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("stacca_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_END_HOUR = "end_hour"
        private const val KEY_END_MINUTE = "end_minute"
        private const val KEY_ALARM_ACTIVE = "alarm_active"
        private const val KEY_WORK_LOGS = "work_logs"
        private const val KEY_ALARM_TRIGGERED_TODAY = "alarm_triggered_today"
        private const val KEY_LAST_TRIGGER_DATE = "last_trigger_date"
    }

    var endHour: Int
        get() = prefs.getInt(KEY_END_HOUR, 18)
        set(value) = prefs.edit().putInt(KEY_END_HOUR, value).apply()

    var endMinute: Int
        get() = prefs.getInt(KEY_END_MINUTE, 0)
        set(value) = prefs.edit().putInt(KEY_END_MINUTE, value).apply()

    var isAlarmActive: Boolean
        get() = prefs.getBoolean(KEY_ALARM_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_ALARM_ACTIVE, value).apply()

    var alarmTriggeredToday: Boolean
        get() = prefs.getBoolean(KEY_ALARM_TRIGGERED_TODAY, false)
        set(value) = prefs.edit().putBoolean(KEY_ALARM_TRIGGERED_TODAY, value).apply()

    var lastTriggerDate: String
        get() = prefs.getString(KEY_LAST_TRIGGER_DATE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_TRIGGER_DATE, value).apply()

    /**
     * Salva un work log.
     */
    fun saveWorkLog(log: WorkLog) {
        val logs = getWorkLogs().toMutableList()
        logs.add(0, log) // Aggiunge in cima (più recente)
        val jsonArray = JSONArray()
        logs.forEach { entry ->
            val obj = JSONObject().apply {
                put("date", entry.date)
                put("time", entry.time)
                put("completed", entry.completed)
                put("todo", entry.todo)
                put("mood", entry.mood)
                put("overtimeMinutes", entry.overtimeMinutes)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_WORK_LOGS, jsonArray.toString()).apply()
    }

    /**
     * Recupera tutti i work log.
     */
    fun getWorkLogs(): List<WorkLog> {
        val json = prefs.getString(KEY_WORK_LOGS, "[]") ?: "[]"
        val jsonArray = JSONArray(json)
        val logs = mutableListOf<WorkLog>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            logs.add(
                WorkLog(
                    date = obj.getString("date"),
                    time = obj.getString("time"),
                    completed = obj.getString("completed"),
                    todo = obj.getString("todo"),
                    mood = obj.getString("mood"),
                    overtimeMinutes = obj.getInt("overtimeMinutes")
                )
            )
        }
        return logs
    }
}
