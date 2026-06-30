package com.stacca.app.receivers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.stacca.app.data.NotificationMessages
import com.stacca.app.data.PreferencesManager
import com.stacca.app.notifications.AlarmSoundManager
import com.stacca.app.notifications.NotificationHelper
import com.stacca.app.ui.FullScreenAlertActivity
import com.stacca.app.util.PermissionHelper
import java.text.SimpleDateFormat
import java.util.*


/**
 * Receiver che viene triggerato quando scatta l'allarme.
 * Gestisce l'escalation delle notifiche con logica step-based:
 * - Ogni notifica avanza di 1 step
 * - Il livello è determinato dallo step, non dal tempo reale
 * - Intervallo fisso di 5 minuti tra le notifiche
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        /**
         * Restituisce l'intervallo tra le notifiche in millisecondi,
         * in base all'impostazione "Frequenza notifiche" dell'utente.
         *
         * 0 = Rilassato (ogni 8 min)
         * 1 = Normale   (ogni 5 min)
         * 2 = Aggressivo (ogni 3 min)
         */
        private fun getIntervalMs(escalationSpeed: Int): Long {
            return when (escalationSpeed) {
                0 -> 8 * 60 * 1000L    // 🐌 Rilassato
                2 -> 3 * 60 * 1000L    // 🔥 Aggressivo
                else -> 5 * 60 * 1000L // ⚡ Normale
            }
        }

        /**
         * Programma l'allarme iniziale per l'orario di fine turno.
         */
        fun scheduleAlarm(context: Context, hour: Int, minute: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                // Se l'orario è già passato, programma per domani
                if (before(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            // Verifica il permesso prima di usare setExactAndAllowWhileIdle (richiesto API 31+)
            if (PermissionHelper.canScheduleExactAlarms(context)) {
                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } catch (e: SecurityException) {
                    Log.e("AlarmReceiver", "SecurityException nel programmare l'allarme iniziale: ${e.message}")
                    // Fallback: invia subito una notifica affinché l'utente sappia che qualcosa è andato storto
                    val fallbackPrefs = PreferencesManager(context)
                    NotificationHelper(context).sendEscalatingNotification(
                        NotificationMessages.Level.GENTLE, 0,
                        soundEnabled = fallbackPrefs.soundEnabled,
                        vibrationEnabled = fallbackPrefs.vibrationEnabled
                    )
                }
            } else {
                Log.w("AlarmReceiver", "Permesso allarmi esatti non disponibile: impossibile programmare l'allarme iniziale. Invio notifica immediata.")
                // Fallback: invia subito una notifica affinché l'utente sappia che qualcosa è andato storto
                val fallbackPrefs = PreferencesManager(context)
                NotificationHelper(context).sendEscalatingNotification(
                    NotificationMessages.Level.GENTLE, 0,
                    soundEnabled = fallbackPrefs.soundEnabled,
                    vibrationEnabled = fallbackPrefs.vibrationEnabled
                )
            }
        }

        /**
         * Cancella l'allarme programmato.
         */
        fun cancelAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            com.stacca.app.notifications.AlarmSoundManager.stop()
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = PreferencesManager(context)

        if (!prefs.isAlarmActive) return

        // Calcola i minuti di straordinario (per visualizzazione, NON per determinare il livello)
        val now = Calendar.getInstance()
        val endTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, prefs.endHour)
            set(Calendar.MINUTE, prefs.endMinute)
            set(Calendar.SECOND, 0)
        }

        val overtimeMillis = now.timeInMillis - endTime.timeInMillis
        val overtimeMinutes = (overtimeMillis / 60000).toInt().coerceAtLeast(0)

        // Determina il livello dallo step corrente (NON dal tempo reale)
        val currentStep = prefs.currentEscalationStep
        var level = NotificationMessages.getLevelForStep(currentStep)

        // --- FREEMIUM: cap al livello 3 (INSISTENT) senza accesso completo ---
        if (level.ordinal >= NotificationMessages.Level.AGGRESSIVE.ordinal && !prefs.hasFullAccess) {

            // Prima volta oggi che un livello premium viene bloccato?
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(Calendar.getInstance().time)
            if (prefs.premiumGateShownDate != today) {
                prefs.premiumGateShownDate = today
                prefs.paywallShownToday = true
            } else {
                prefs.paywallShownToday = true
            }

            // Cap al livello INSISTENT
            level = NotificationMessages.Level.INSISTENT
        }

        // Mantieni la CPU attiva il tempo necessario per inviare la notifica.
        val wakeLock = if (level.ordinal >= NotificationMessages.Level.INSISTENT.ordinal) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Stacca:WakeLock"
            ).also { it.acquire(10_000L) } // max 10s, rilasciato nel finally
        } else null

        try {
            // Calcola se dobbiamo aggiungere il teaser premium alla notifica INSISTENT
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(Calendar.getInstance().time)
            val realLevel = NotificationMessages.getLevelForStep(currentStep)
            val mostraTeaserPremium = !prefs.hasFullAccess &&
                level == NotificationMessages.Level.INSISTENT &&
                prefs.premiumGateShownDate == today &&
                realLevel.ordinal >= NotificationMessages.Level.AGGRESSIVE.ordinal

            // Invia la notifica (con eventuale teaser) — selezione sequenziale
            val notificationHelper = NotificationHelper(context)
            notificationHelper.sendEscalatingNotification(
                level, overtimeMinutes,
                soundEnabled = prefs.soundEnabled,
                vibrationEnabled = prefs.vibrationEnabled,
                premiumTeaser = mostraTeaserPremium
            )

            // Per livello NUCLEAR e APOCALYPSE, apri l'activity a schermo intero
            if (prefs.fullScreenEnabled &&
                prefs.hasFullAccess &&
                level.ordinal >= NotificationMessages.Level.NUCLEAR.ordinal) {
                val fullScreenIntent = Intent(context, FullScreenAlertActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("overtime_minutes", overtimeMinutes)
                    putExtra("level", level.name)
                }
                context.startActivity(fullScreenIntent)
            }
        } finally {
            // Rilascia il wake lock anche in caso di eccezione
            if (wakeLock?.isHeld == true) wakeLock.release()
        }

        // Avanza lo step per la prossima notifica
        prefs.currentEscalationStep = currentStep + 1

        // Programma il prossimo allarme (intervallo basato su impostazione Frequenza)
        scheduleNextAlarm(context, prefs.escalationSpeed)
    }

    /**
     * Programma il prossimo allarme con intervallo basato sull'impostazione
     * "Frequenza notifiche": 🐌 8 min / ⚡ 5 min / 🔥 3 min.
     */
    private fun scheduleNextAlarm(context: Context, escalationSpeed: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + getIntervalMs(escalationSpeed)

        // Verifica il permesso prima di usare setExactAndAllowWhileIdle (richiesto API 31+)
        if (PermissionHelper.canScheduleExactAlarms(context)) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } catch (e: SecurityException) {
                Log.e("AlarmReceiver", "SecurityException nel riprogrammare il prossimo allarme: ${e.message}")
                // Fallback: invia subito una notifica affinché l'utente riceva almeno qualcosa
                val fallbackPrefs = PreferencesManager(context)
                NotificationHelper(context).sendEscalatingNotification(
                    NotificationMessages.Level.GENTLE, 0,
                    soundEnabled = fallbackPrefs.soundEnabled,
                    vibrationEnabled = fallbackPrefs.vibrationEnabled
                )
            }
        } else {
            Log.w("AlarmReceiver", "Permesso allarmi esatti non disponibile: impossibile riprogrammare. Invio notifica immediata.")
            // Fallback: invia subito una notifica affinché l'utente riceva almeno qualcosa
            val fallbackPrefs = PreferencesManager(context)
            NotificationHelper(context).sendEscalatingNotification(
                NotificationMessages.Level.GENTLE, 0,
                soundEnabled = fallbackPrefs.soundEnabled,
                vibrationEnabled = fallbackPrefs.vibrationEnabled
            )
        }
    }
}
