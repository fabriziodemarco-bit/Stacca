package com.stacca.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.stacca.app.data.PreferencesManager
import com.stacca.app.notifications.NotificationHelper

/**
 * Receiver per le azioni delle notifiche (OK Stacco, etc.).
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "ACTION_STOP_WORK" -> {
                // L'utente ha accettato di staccare
                val prefs = PreferencesManager(context)
                prefs.alarmTriggeredToday = true

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
