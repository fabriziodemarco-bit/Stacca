package com.stacca.app.receivers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import com.stacca.app.data.NotificationMessages
import com.stacca.app.data.PreferencesManager
import com.stacca.app.notifications.NotificationHelper
import com.stacca.app.ui.FullScreenAlertActivity
import com.stacca.app.ui.PaywallActivity
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

        // --- PAYWALL: blocco al livello 3 (INSISTENT) se non premium ---
        if (level.ordinal >= NotificationMessages.Level.AGGRESSIVE.ordinal && !prefs.isPremium) {
            // Se non abbiamo ancora mostrato il paywall in questa sessione, mostralo
            if (!prefs.paywallShownToday) {
                prefs.paywallShownToday = true
                val paywallIntent = Intent(context, PaywallActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(paywallIntent)
            }
            // Cap al livello INSISTENT per utenti non premium
            level = NotificationMessages.Level.INSISTENT
        }

        // Mantieni la CPU attiva il tempo necessario per inviare la notifica.
        // Lo schermo viene acceso dalla notification full-screen intent
        // e dall'attributo turnScreenOn della FullScreenAlertActivity.
        if (level.ordinal >= NotificationMessages.Level.INSISTENT.ordinal) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Stacca:WakeLock"
            )
            wakeLock.acquire(5000L)
        }

        // Invia la notifica
        val notificationHelper = NotificationHelper(context)
        notificationHelper.sendEscalatingNotification(
            level, overtimeMinutes,
            soundEnabled = prefs.soundEnabled,
            vibrationEnabled = prefs.vibrationEnabled
        )

        // Per livello NUCLEAR e APOCALYPSE, apri anche l'activity a schermo intero
        if (prefs.fullScreenEnabled &&
            level.ordinal >= NotificationMessages.Level.NUCLEAR.ordinal) {
            val fullScreenIntent = Intent(context, FullScreenAlertActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("overtime_minutes", overtimeMinutes)
                putExtra("level", level.name)
            }
            context.startActivity(fullScreenIntent)
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

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } catch (e: SecurityException) {
            // Fallback ad allarme non esatto
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
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

            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } catch (e: SecurityException) {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
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
        }
    }
}
