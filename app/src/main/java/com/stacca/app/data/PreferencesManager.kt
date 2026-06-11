package com.stacca.app.data

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.TimeUnit


/**
 * Gestisce le preferenze dell'app.
 *
 * Le preferenze sono divise in due file distinti:
 *  - "stacca_prefs"   → impostazioni normali (svuotabili da "Cancella dati")
 *  - "stacca_license" → dati di licenza/trial (MAI cancellabili dall'utente)
 */
class PreferencesManager(context: Context) {

    /** Preferenze normali — possono essere azzerate da "Cancella dati" */
    private val prefs: SharedPreferences =
        context.getSharedPreferences("stacca_prefs", Context.MODE_PRIVATE)

    /** Preferenze di licenza — protette, mai toccate da "Cancella dati" */
    private val licensePrefs: SharedPreferences =
        context.getSharedPreferences("stacca_license", Context.MODE_PRIVATE)

    companion object {
        // --- Chiavi preferenze normali ---
        private const val KEY_END_HOUR = "end_hour"
        private const val KEY_END_MINUTE = "end_minute"
        private const val KEY_ALARM_ACTIVE = "alarm_active"
        private const val KEY_LAST_TRIGGER_DATE = "last_trigger_date"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_FULLSCREEN_ENABLED = "fullscreen_enabled"
        private const val KEY_AUTO_RESTART_ENABLED = "auto_restart_enabled"
        private const val KEY_ESCALATION_SPEED = "escalation_speed"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_PAYWALL_SHOWN_TODAY = "paywall_shown_today"

        // Tempo Non Vissuto
        private const val KEY_PENDING_TEMPO_NON_VISSUTO = "pending_tempo_non_vissuto"
        private const val KEY_HAS_PENDING_TEMPO = "has_pending_tempo"

        // --- Chiavi preferenze di licenza (in stacca_license) ---
        private const val KEY_IS_PREMIUM = "is_premium"
        private const val KEY_FIRST_USE_DATE = "first_use_date"

        // Flag migrazione: scritto in stacca_license dopo la prima migrazione riuscita
        private const val KEY_MIGRATED_V2 = "migrated_v2"

        // Durata del trial in giorni
        private const val TRIAL_DURATION_DAYS = 7L
    }

    // =========================================================================
    // MIGRAZIONE (eseguita una sola volta all'init)
    // =========================================================================

    init {
        eseguiMigrazioneV2SePendente()
    }

    /**
     * Migra le chiavi di licenza dal vecchio file "stacca_prefs" al nuovo
     * file "stacca_license". Viene eseguita una volta sola (flag migrated_v2).
     *
     * Regole:
     * - Se is_premium=true nel vecchio file → copialo in stacca_license
     * - Se first_use_date esiste nel vecchio file → copialo; altrimenti → oggi
     * - Al termine rimuove le chiavi di licenza da stacca_prefs per pulizia
     */
    private fun eseguiMigrazioneV2SePendente() {
        if (licensePrefs.getBoolean(KEY_MIGRATED_V2, false)) return

        val editor = licensePrefs.edit()

        // Copia is_premium se era true nel vecchio file
        if (prefs.getBoolean(KEY_IS_PREMIUM, false)) {
            editor.putBoolean(KEY_IS_PREMIUM, true)
        }

        // Copia first_use_date; se non esiste inizializza a oggi
        val vecchiaData = prefs.getLong(KEY_FIRST_USE_DATE, 0L)
        val dataInizio = if (vecchiaData > 0L) vecchiaData else System.currentTimeMillis()
        editor.putLong(KEY_FIRST_USE_DATE, dataInizio)

        // Segna la migrazione come completata
        editor.putBoolean(KEY_MIGRATED_V2, true)
        editor.apply()

        // Rimuove le chiavi di licenza dal vecchio file (pulizia opzionale ma consigliata)
        prefs.edit()
            .remove(KEY_IS_PREMIUM)
            .remove(KEY_FIRST_USE_DATE)
            // Rimuove anche le chiavi della vecchia logica consecutiva, se presenti
            .remove("consecutive_use_days")
            .remove("last_use_date")
            .remove("trial_expired")
            .apply()
    }

    // =========================================================================
    // PREFERENZE NORMALI
    // =========================================================================

    var endHour: Int
        get() = prefs.getInt(KEY_END_HOUR, 18)
        set(value) = prefs.edit().putInt(KEY_END_HOUR, value).apply()

    var endMinute: Int
        get() = prefs.getInt(KEY_END_MINUTE, 0)
        set(value) = prefs.edit().putInt(KEY_END_MINUTE, value).apply()

    var isAlarmActive: Boolean
        get() = prefs.getBoolean(KEY_ALARM_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_ALARM_ACTIVE, value).apply()

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

    // --- Auth ---

    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()

    var userEmail: String
        get() = prefs.getString(KEY_USER_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value).apply()

    /** Tiene traccia se il paywall è stato mostrato oggi */
    var paywallShownToday: Boolean
        get() = prefs.getBoolean(KEY_PAYWALL_SHOWN_TODAY, false)
        set(value) = prefs.edit().putBoolean(KEY_PAYWALL_SHOWN_TODAY, value).apply()

    // --- Tempo Non Vissuto ---

    /** Minuti di straordinario da mostrare nella schermata "Tempo Non Vissuto" */
    var pendingTempoNonVissutoMinutes: Int
        get() = prefs.getInt(KEY_PENDING_TEMPO_NON_VISSUTO, 0)
        set(value) = prefs.edit().putInt(KEY_PENDING_TEMPO_NON_VISSUTO, value).apply()

    /** Flag: c'è un "Tempo Non Vissuto" da mostrare al prossimo avvio */
    var hasPendingTempoNonVissuto: Boolean
        get() = prefs.getBoolean(KEY_HAS_PENDING_TEMPO, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_PENDING_TEMPO, value).apply()

    // =========================================================================
    // PREFERENZE DI LICENZA (in stacca_license — protette)
    // =========================================================================

    /**
     * Stato premium dell'utente.
     * Letto e scritto in stacca_license; BillingManager e AuthManager
     * continuano a usare prefs.isPremium come sempre (stesso nome proprietà).
     */
    var isPremium: Boolean
        get() = licensePrefs.getBoolean(KEY_IS_PREMIUM, false)
        set(value) = licensePrefs.edit().putBoolean(KEY_IS_PREMIUM, value).apply()

    /**
     * Data (in millisecondi) del primo avvio dell'app.
     * Usata per calcolare i giorni di prova rimasti.
     */
    var firstUseDateMillis: Long
        get() = licensePrefs.getLong(KEY_FIRST_USE_DATE, 0L)
        set(value) = licensePrefs.edit().putLong(KEY_FIRST_USE_DATE, value).apply()

    // =========================================================================
    // LOGICA TRIAL A CALENDARIO
    // =========================================================================

    /**
     * Giorni di prova rimasti (mai negativo).
     * Basato sul confronto tra oggi e [firstUseDateMillis] + 7 giorni.
     */
    val trialDaysLeft: Int
        get() {
            val primoAvvio = firstUseDateMillis
            if (primoAvvio == 0L) return TRIAL_DURATION_DAYS.toInt()
            val passati = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - primoAvvio)
            val rimasti = TRIAL_DURATION_DAYS - passati
            return rimasti.coerceAtLeast(0L).toInt()
        }

    /**
     * Il trial è ancora attivo se l'utente è premium OPPURE ha giorni rimasti.
     * Sostituisce il vecchio flag trialExpired (ora derivato, non scritto).
     */
    val isTrialActive: Boolean
        get() = isPremium || trialDaysLeft > 0

    /**
     * Garantisce che firstUseDateMillis sia inizializzato.
     * Chiamato all'avvio (SplashActivity) in sostituzione di trackDailyUsage().
     */
    fun ensureFirstUseDateSet() {
        if (firstUseDateMillis == 0L) {
            firstUseDateMillis = System.currentTimeMillis()
        }
    }
}
