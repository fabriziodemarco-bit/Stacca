package com.stacca.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.stacca.app.data.PreferencesManager
import com.stacca.app.notifications.NotificationHelper
import java.util.Calendar

/**
 * Receiver per le azioni delle notifiche (OK Stacco, etc.).
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "ACTION_STOP_WORK" -> {
                // L'utente ha accettato di staccare
                val prefs = PreferencesManager(context)
                prefs.paywallShownToday = false

                // Calcola i minuti di straordinario e salva come pending
                val now = Calendar.getInstance()
                val endTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, prefs.endHour)
                    set(Calendar.MINUTE, prefs.endMinute)
                    set(Calendar.SECOND, 0)
                }
                val overtimeMillis = now.timeInMillis - endTime.timeInMillis
                if (overtimeMillis > 60_000) {
                    prefs.hasPendingTempoNonVissuto = true
                    prefs.pendingTempoNonVissutoMinutes = (overtimeMillis / 60_000).toInt()
                }

                // Cancella gli allarmi futuri per oggi
                AlarmReceiver.cancelAlarm(context)

                // Cancella le notifiche
                val notificationHelper = NotificationHelper(context)
                notificationHelper.cancelAll()

                // Riprogramma per domani
                AlarmReceiver.scheduleAlarm(context, prefs.endHour, prefs.endMinute)
            }
        }
    }
}

