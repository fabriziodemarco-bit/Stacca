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
 * Gestisce l'escalation delle notifiche.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = PreferencesManager(context)

        if (!prefs.isAlarmActive) return

        // Calcola i minuti di straordinario
        val now = Calendar.getInstance()
        val endTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, prefs.endHour)
            set(Calendar.MINUTE, prefs.endMinute)
            set(Calendar.SECOND, 0)
        }

        val overtimeMillis = now.timeInMillis - endTime.timeInMillis
        val overtimeMinutes = (overtimeMillis / 60000).toInt().coerceAtLeast(0)

        // Determina il livello di escalation (rispetta velocità)
        val adjustedMinutes = adjustForSpeed(overtimeMinutes, prefs.escalationSpeed)
        var level = NotificationMessages.getLevelForMinutes(adjustedMinutes)

        // --- FREEMIUM: cap al livello 3 (INSISTENT) senza accesso completo ---
        if (level.ordinal >= NotificationMessages.Level.AGGRESSIVE.ordinal && !prefs.hasFullAccess) {

            // Prima volta oggi che un livello premium viene bloccato?
            // Aggiungi alla notifica INSISTENT un teaser upsell (una volta al giorno, non ogni allarme)
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Calendar.getInstance().time)
            if (prefs.premiumGateShownDate != today) {
                prefs.premiumGateShownDate = today
                // Salviamo il livello "reale" che avremmo raggiunto per il messaggio ironico
                prefs.paywallShownToday = true   // ricicliamo il flag per evitare il vecchio popup
                // Passiamo il livello bloccato via intent extra — NotificationHelper lo usa per il BigText
                // Nota: sendEscalatingNotification viene chiamata più giù; usiamo un flag in prefs
            } else {
                // Già mostrato oggi: metti solo la notifica normale senza teaser
                prefs.paywallShownToday = true
            }

            // Cap al livello INSISTENT
            level = NotificationMessages.Level.INSISTENT
        }

        // Mantieni la CPU attiva il tempo necessario per inviare la notifica.
        // Lo schermo viene acceso dalla notification full-screen intent
        // e dall'attributo turnScreenOn della FullScreenAlertActivity.
        val wakeLock = if (level.ordinal >= NotificationMessages.Level.INSISTENT.ordinal) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Stacca:WakeLock"
            ).also { it.acquire(10_000L) } // max 10s, rilasciato nel finally
        } else null

        try {
            // Calcola se dobbiamo aggiungere il teaser premium alla notifica INSISTENT
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Calendar.getInstance().time)
            // Il flag premiumGateShownDate viene impostato SOLO la prima volta oggi nel blocco sopra;
            // se coincide con oggi E siamo al livello INSISTENT per effetto del cap, aggiungiamo il teaser
            val mostraTeaserPremium = !prefs.hasFullAccess &&
                level == NotificationMessages.Level.INSISTENT &&
                prefs.premiumGateShownDate == today &&
                adjustForSpeed(overtimeMinutes, prefs.escalationSpeed)
                    .let { NotificationMessages.getLevelForMinutes(it).ordinal } >= NotificationMessages.Level.AGGRESSIVE.ordinal

            // Invia la notifica (con eventuale teaser)
            val notificationHelper = NotificationHelper(context)
            notificationHelper.sendEscalatingNotification(
                level, overtimeMinutes,
                soundEnabled = prefs.soundEnabled,
                vibrationEnabled = prefs.vibrationEnabled,
                premiumTeaser = mostraTeaserPremium
            )

            // Per livello NUCLEAR e APOCALYPSE, apri l'activity a schermo intero
            // MA solo se l'utente ha accesso completo (premium o trial attivo)
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

        // Programma il prossimo allarme
        scheduleNextAlarm(context, level, prefs.escalationSpeed)
    }

    /**
     * Aggiusta i minuti in base alla velocità di escalation.
     * 0 = Rilassato (x0.5 - escalation più lenta)
     * 1 = Normale (x1)
     * 2 = Aggressivo (x2 - escalation più veloce)
     */
    private fun adjustForSpeed(minutes: Int, speed: Int): Int {
        return when (speed) {
            0 -> minutes / 2       // Rilassato: dimezza i minuti -> escalation lenta
            2 -> minutes * 2       // Aggressivo: raddoppia -> escalation veloce
            else -> minutes        // Normale
        }
    }

    /**
     * Programma il prossimo allarme in base al livello corrente.
     * Intervalli più ravvicinati per livelli più alti.
     */
    private fun scheduleNextAlarm(
        context: Context,
        currentLevel: NotificationMessages.Level,
        escalationSpeed: Int
    ) {
        val baseInterval = when (currentLevel) {
            NotificationMessages.Level.GENTLE -> 5      // Ogni 5 min
            NotificationMessages.Level.FRIENDLY -> 5    // Ogni 5 min
            NotificationMessages.Level.INSISTENT -> 3   // Ogni 3 min
            NotificationMessages.Level.AGGRESSIVE -> 2  // Ogni 2 min
            NotificationMessages.Level.NUCLEAR -> 1     // Ogni minuto!
            NotificationMessages.Level.APOCALYPSE -> 1  // Ogni minuto!
        }

        // Adatta l'intervallo alla velocità
        val intervalMinutes = when (escalationSpeed) {
            0 -> (baseInterval * 1.5).toInt().coerceAtLeast(2)  // Rilassato: più lento
            2 -> (baseInterval * 0.7).toInt().coerceAtLeast(1)  // Aggressivo: più veloce
            else -> baseInterval
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + (intervalMinutes * 60 * 1000L)

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

    companion object {

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
}
