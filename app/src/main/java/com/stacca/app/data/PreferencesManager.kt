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
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_FULLSCREEN_ENABLED = "fullscreen_enabled"
        private const val KEY_AUTO_RESTART_ENABLED = "auto_restart_enabled"
        private const val KEY_ESCALATION_SPEED = "escalation_speed"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_IS_PREMIUM = "is_premium"
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

    // --- Impostazioni ---

    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()

    var vibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, value).apply()

    var fullScreenEnabled: Boolean
        get() = prefs.getBoolean(KEY_FULLSCREEN_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_FULLSCREEN_ENABLED, value).apply()

    var autoRestartEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RESTART_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_RESTART_ENABLED, value).apply()

    /** 0 = Rilassato, 1 = Normale, 2 = Aggressivo */
    var escalationSpeed: Int
        get() = prefs.getInt(KEY_ESCALATION_SPEED, 1)
        set(value) = prefs.edit().putInt(KEY_ESCALATION_SPEED, value).apply()

    // --- Auth & Premium ---

    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()

    var userEmail: String
        get() = prefs.getString(KEY_USER_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value).apply()

    var isPremium: Boolean
        get() = prefs.getBoolean(KEY_IS_PREMIUM, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_PREMIUM, value).apply()

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

    /**
     * Cancella un work log dalla posizione specificata.
     */
    fun deleteWorkLog(position: Int) {
        val logs = getWorkLogs().toMutableList()
        if (position in logs.indices) {
            logs.removeAt(position)
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
    }

    /**
     * Cancella tutti i work log.
     */
    fun clearWorkLogs() {
        prefs.edit().putString(KEY_WORK_LOGS, "[]").apply()
    }
}
