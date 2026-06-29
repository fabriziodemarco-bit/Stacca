package com.stacca.app.data

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
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
        // Flag: la schermata soft "fine trial" è già stata mostrata una volta
        private const val KEY_TRIAL_END_SHOWN = "trial_end_shown"
        // Flag: data dell'ultimo blocco premium durante l'escalation (per il messaggio una-tantum)
        private const val KEY_PREMIUM_GATE_SHOWN_DATE = "premium_gate_shown_date"

        // Tempo Non Vissuto
        private const val KEY_PENDING_TEMPO_NON_VISSUTO = "pending_tempo_non_vissuto"
        private const val KEY_HAS_PENDING_TEMPO = "has_pending_tempo"

        // --- Streak & Statistiche ---
        private const val KEY_STREAK_COUNT = "streak_count"
        private const val KEY_BEST_STREAK = "best_streak"
        private const val KEY_LAST_STACCATO_DATE = "last_staccato_date"   // formato yyyy-MM-dd
        private const val KEY_TOTAL_ON_TIME_DAYS = "total_on_time_days"
        private const val KEY_TOTAL_OVERTIME_MINUTES = "total_overtime_minutes"
        private const val KEY_TODAY_OVERTIME_MINUTES = "today_overtime_minutes"

        // --- Chiavi preferenze di licenza (in stacca_license) ---
        private const val KEY_IS_PREMIUM = "is_premium"
        private const val KEY_FIRST_USE_DATE = "first_use_date"

        // Flag migrazione: scritto in stacca_license dopo la prima migrazione riuscita
        private const val KEY_MIGRATED_V2 = "migrated_v2"

        // Durata del trial in giorni
        private const val TRIAL_DURATION_DAYS = 7L

        // Soglia "in orario": se lo straordinario è <= 5 minuti, conta come staccato in orario
        const val ON_TIME_THRESHOLD_MINUTES = 5

        // Formato data per lastStaccatoDate
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
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

    var isWaitingForNextAlarm: Boolean
        get() {
            if (lastStaccatoDate != today()) {
                prefs.edit().putBoolean("is_waiting_for_next_alarm", false).apply()
                return false
            }
            return prefs.getBoolean("is_waiting_for_next_alarm", false)
        }
        set(value) = prefs.edit().putBoolean("is_waiting_for_next_alarm", value).apply()

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
    // STREAK & STATISTICHE (in stacca_prefs, azzerate da "Cancella dati")
    // =========================================================================

    /** Numero di giorni consecutivi in cui l'utente ha staccato in orario (<=5 min). */
    var streakCount: Int
        get() = prefs.getInt(KEY_STREAK_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_STREAK_COUNT, value).apply()

    /** Miglior streak di sempre. */
    var bestStreak: Int
        get() = prefs.getInt(KEY_BEST_STREAK, 0)
        set(value) = prefs.edit().putInt(KEY_BEST_STREAK, value).apply()

    /**
     * Data dell'ultimo staccato registrato (formato yyyy-MM-dd).
     * Usata per l'idempotenza di registraStaccato().
     */
    var lastStaccatoDate: String
        get() = prefs.getString(KEY_LAST_STACCATO_DATE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_STACCATO_DATE, value).apply()

    /** Numero totale di giorni in cui l'utente ha staccato in orario (cumulativo lifetime). */
    var totalOnTimeDays: Int
        get() = prefs.getInt(KEY_TOTAL_ON_TIME_DAYS, 0)
        set(value) = prefs.edit().putInt(KEY_TOTAL_ON_TIME_DAYS, value).apply()

    /** Minuti cumulativi di straordinario accumulati nei giorni di ritardo. */
    var totalOvertimeMinutes: Int
        get() = prefs.getInt(KEY_TOTAL_OVERTIME_MINUTES, 0)
        set(value) = prefs.edit().putInt(KEY_TOTAL_OVERTIME_MINUTES, value).apply()

    /** Minuti di straordinario di oggi. */
    var todayOvertimeMinutes: Int
        get() {
            if (lastStaccatoDate != today()) {
                prefs.edit().putInt(KEY_TODAY_OVERTIME_MINUTES, 0).apply()
                return 0
            }
            return prefs.getInt(KEY_TODAY_OVERTIME_MINUTES, 0)
        }
        set(value) = prefs.edit().putInt(KEY_TODAY_OVERTIME_MINUTES, value).apply()

    /** Minuti di ritardo dell'ultimo turno effettuato. */
    var lastShiftOvertimeMinutes: Int
        get() = prefs.getInt("last_shift_overtime_minutes", 0)
        set(value) = prefs.edit().putInt("last_shift_overtime_minutes", value).apply()

    /**
     * Data odierna nel formato yyyy-MM-dd (usata internamente per i confronti).
     */
    private fun today(): String = DATE_FORMAT.format(Calendar.getInstance().time)

    /** Quanti turni ha completato oggi l'utente */
    var shiftsCompletedToday: Int
        get() {
            if (lastStaccatoDate != today()) {
                prefs.edit().putInt("shifts_completed_today", 0).apply()
                return 0
            }
            return prefs.getInt("shifts_completed_today", 0)
        }
        set(value) = prefs.edit().putInt("shifts_completed_today", value).apply()

    /**
     * Registra l'azione "Ho staccato!" per oggi.
     *
     * Logica:
     * - IDEMPOTENTE: se [lastStaccatoDate] == oggi, non fa nulla e restituisce lo stato corrente.
     * - "In orario" = [overtimeMinutes] <= ON_TIME_THRESHOLD_MINUTES (5 minuti di tolleranza).
     * - In orario → incrementa streakCount (o 1 se ieri non era staccato in orario),
     *   aggiorna bestStreak, incrementa totalOnTimeDays.
     * - In ritardo → azzera streakCount a 0, aggiunge [overtimeMinutes] a totalOvertimeMinutes.
     *
     * NOTA: lo streak NON si azzera automaticamente per i giorni saltati (weekend/ferie).
     * Si azzera SOLO quando si stacca in ritardo. Questo semplifica la logica ed evita
     * di dover tracciare l'ultimo allarme schedulato. Se in futuro si vuole la logica
     * "azzera anche per i giorni lavorativi saltati", bisognerà salvare la data dell'ultimo
     * allarme attivo e confrontarla qui.
     *
     * @param overtimeMinutes  Minuti di straordinario (mai negativo — usare coerceAtLeast(0)).
     * @return [StaccatoResult] con lo stato aggiornato.
     */
    fun registraStaccato(overtimeMinutes: Int): StaccatoResult {
        val todayStr = today()

        // Se è un nuovo giorno, resettiamo i contatori giornalieri
        if (lastStaccatoDate != todayStr) {
            shiftsCompletedToday = 0
            todayOvertimeMinutes = 0
        }

        val isOnTime = overtimeMinutes <= ON_TIME_THRESHOLD_MINUTES
        val prevBest = bestStreak
        val newStreak: Int

        // Incrementiamo i turni giornalieri
        val currentShifts = shiftsCompletedToday + 1
        val newTodayOvertime = todayOvertimeMinutes + overtimeMinutes

        if (isOnTime) {
            newStreak = streakCount + 1
            val newBest = maxOf(prevBest, newStreak)

            prefs.edit()
                .putInt(KEY_STREAK_COUNT, newStreak)
                .putInt(KEY_BEST_STREAK, newBest)
                .putInt(KEY_TOTAL_ON_TIME_DAYS, totalOnTimeDays + 1)
                .putInt(KEY_TODAY_OVERTIME_MINUTES, newTodayOvertime)
                .putInt("shifts_completed_today", currentShifts)
                .putString(KEY_LAST_STACCATO_DATE, todayStr)
                .putBoolean("is_waiting_for_next_alarm", true)
                .putInt("last_shift_overtime_minutes", overtimeMinutes)
                .apply()

            return StaccatoResult(
                isOnTime    = true,
                streakCount = newStreak,
                bestStreak  = newBest,
                isNewRecord = newStreak > prevBest
            )
        } else {
            // Ritardo: azzera streak e accumula overtime
            newStreak = 0

            prefs.edit()
                .putInt(KEY_STREAK_COUNT, 0)
                .putInt(KEY_TOTAL_OVERTIME_MINUTES, totalOvertimeMinutes + overtimeMinutes)
                .putInt(KEY_TODAY_OVERTIME_MINUTES, newTodayOvertime)
                .putInt("shifts_completed_today", currentShifts)
                .putString(KEY_LAST_STACCATO_DATE, todayStr)
                .putBoolean("is_waiting_for_next_alarm", true)
                .putInt("last_shift_overtime_minutes", overtimeMinutes)
                .apply()

            return StaccatoResult(
                isOnTime    = false,
                streakCount = 0,
                bestStreak  = prevBest,
                isNewRecord = false
            )
        }
    }

    /**
     * Risultato della chiamata a [registraStaccato].
     *
     * @property isOnTime    true se l'utente ha staccato entro 5 minuti dall'orario previsto.
     * @property streakCount Streak corrente dopo la registrazione.
     * @property bestStreak  Miglior streak di sempre (aggiornato se [isNewRecord]).
     * @property isNewRecord true se [streakCount] ha superato il precedente [bestStreak].
     */
    data class StaccatoResult(
        val isOnTime: Boolean,
        val streakCount: Int,
        val bestStreak: Int,
        val isNewRecord: Boolean
    )

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
     * Accesso completo = premium acquistato OPPURE trial ancora attivo.
     * Usato per il gating freemium: se false, l'app degrada al piano gratuito
     * (livelli 1-3) senza bloccarsi.
     */
    val hasFullAccess: Boolean
        get() = isPremium || isTrialActive

    /**
     * True se la schermata informativa "fine trial" è già stata mostrata.
     * Salvato in stacca_prefs (azzerabile con "Cancella dati", ma non critico).
     */
    var trialEndShown: Boolean
        get() = prefs.getBoolean(KEY_TRIAL_END_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_TRIAL_END_SHOWN, value).apply()

    /**
     * Data (yyyy-MM-dd) dell'ultimo giorno in cui abbiamo mostrato il messaggio
     * "sei al livello X ma potresti avere il Nucleare" nella notifica.
     * Una sola notifica al giorno — nessun bombardamento psicologico gratis.
     */
    var premiumGateShownDate: String
        get() = prefs.getString(KEY_PREMIUM_GATE_SHOWN_DATE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PREMIUM_GATE_SHOWN_DATE, value).apply()

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
