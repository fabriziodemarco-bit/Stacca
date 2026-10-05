package com.stacca.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.stacca.app.data.PreferencesManager
import com.stacca.app.data.StaccoManager
import com.stacca.app.notifications.AlarmSoundManager
import com.stacca.app.notifications.NotificationHelper

/**
 * Receiver per i pulsanti delle notifiche ("Ho staccato" e "Ancora X minuti").
 * Nessuno dei due apre l'app.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SNOOZE = "ACTION_SNOOZE_NOTIFICATION"
        const val ACTION_HO_STACCATO = "ACTION_HO_STACCATO"
        // Azione delle notifiche create dalle versioni precedenti (pulsante "Chiudi")
        private const val ACTION_DISMISS_OLD = "ACTION_DISMISS_NOTIFICATION"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_HO_STACCATO -> {
                // Registra lo stacco e riprogramma per domani, poi un breve messaggio di premio
                val overtimeMinutes = StaccoManager.registerStacco(context)
                val prefs = PreferencesManager(context)
                // Il premio arriva con la notifica: riaprendo l'app si va dritti alla home
                prefs.isWaitingForNextAlarm = false
                val endTimeText = String.format("%02d:%02d", prefs.endHour, prefs.endMinute)
                NotificationHelper(context).showStaccoReward(overtimeMinutes, endTimeText)
            }

            ACTION_SNOOZE, ACTION_DISMISS_OLD -> {
                // "Ancora X minuti": zittisce e chiude la notifica.
                // L'allarme successivo è già programmato e l'escalation NON si azzera:
                // la prossima notifica arriva al livello successivo.
                AlarmSoundManager.stop()
                NotificationHelper(context).cancelAll()
            }
        }
    }
}
