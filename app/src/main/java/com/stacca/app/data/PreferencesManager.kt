package com.stacca.app.data

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*


/**
 * Gestisce le preferenze dell'app.
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("stacca_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_END_HOUR = "end_hour"
        private const val KEY_END_MINUTE = "end_minute"
        private const val KEY_ALARM_ACTIVE = "alarm_active"

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
        private const val KEY_PAYWALL_SHOWN_TODAY = "paywall_shown_today"

        // Trial tracking
        private const val KEY_FIRST_USE_DATE = "first_use_date"
        private const val KEY_CONSECUTIVE_USE_DAYS = "consecutive_use_days"
        private const val KEY_LAST_USE_DATE = "last_use_date"
        private const val KEY_TRIAL_EXPIRED = "trial_expired"

        // Tempo Non Vissuto
        private const val KEY_PENDING_TEMPO_NON_VISSUTO = "pending_tempo_non_vissuto"
        private const val KEY_HAS_PENDING_TEMPO = "has_pending_tempo"
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

    /** Tiene traccia se il paywall è stato mostrato oggi */
    var paywallShownToday: Boolean
        get() = prefs.getBoolean(KEY_PAYWALL_SHOWN_TODAY, false)
        set(value) = prefs.edit().putBoolean(KEY_PAYWALL_SHOWN_TODAY, value).apply()

    // --- Trial Tracking (5 giorni consecutivi) ---

    var firstUseDateMillis: Long
        get() = prefs.getLong(KEY_FIRST_USE_DATE, 0L)
        set(value) = prefs.edit().putLong(KEY_FIRST_USE_DATE, value).apply()

    var consecutiveUseDays: Int
        get() = prefs.getInt(KEY_CONSECUTIVE_USE_DAYS, 1)
        set(value) = prefs.edit().putInt(KEY_CONSECUTIVE_USE_DAYS, value).apply()

    var lastUseDateString: String
        get() = prefs.getString(KEY_LAST_USE_DATE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_USE_DATE, value).apply()

    var trialExpired: Boolean
        get() = prefs.getBoolean(KEY_TRIAL_EXPIRED, false)
        set(value) = prefs.edit().putBoolean(KEY_TRIAL_EXPIRED, value).apply()

    /**
     * Traccia l'utilizzo giornaliero per il trial di 5 giorni.
     * Incrementa il contatore solo se oggi è un giorno diverso da ieri.
     * Resetta se l'utente salta un giorno (non consecutivo).
     */
    fun trackDailyUsage() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = dateFormat.format(Date())

        if (firstUseDateMillis == 0L) {
            firstUseDateMillis = System.currentTimeMillis()
        }

        val lastDate = lastUseDateString
        if (lastDate.isEmpty()) {
            lastUseDateString = today
            consecutiveUseDays = 1
            return
        }

        if (lastDate == today) {
            return
        }

        try {
            val lastCal = Calendar.getInstance().apply {
                time = dateFormat.parse(lastDate)!!
            }
            val todayCal = Calendar.getInstance().apply {
                time = dateFormat.parse(today)!!
            }
            val diffMillis = todayCal.timeInMillis - lastCal.timeInMillis
            val diffDays = (diffMillis / (1000 * 60 * 60 * 24)).toInt()

            consecutiveUseDays = if (diffDays == 1) {
                consecutiveUseDays + 1
            } else {
                1
            }
        } catch (e: Exception) {
            consecutiveUseDays = 1
        }

        lastUseDateString = today

        if (consecutiveUseDays >= 5 && !isLoggedIn) {
            trialExpired = true
        }
    }

    // --- Tempo Non Vissuto ---

    /** Minuti di straordinario da mostrare nella schermata "Tempo Non Vissuto" */
    var pendingTempoNonVissutoMinutes: Int
        get() = prefs.getInt(KEY_PENDING_TEMPO_NON_VISSUTO, 0)
        set(value) = prefs.edit().putInt(KEY_PENDING_TEMPO_NON_VISSUTO, value).apply()

    /** Flag: c'è un "Tempo Non Vissuto" da mostrare al prossimo avvio */
    var hasPendingTempoNonVissuto: Boolean
        get() = prefs.getBoolean(KEY_HAS_PENDING_TEMPO, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_PENDING_TEMPO, value).apply()
}
