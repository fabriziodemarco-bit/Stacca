package com.stacca.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.stacca.app.notifications.AlarmSoundManager
import com.stacca.app.notifications.NotificationHelper

/**
 * Receiver per i pulsanti delle notifiche.
 * "Ho staccato" non passa da qui: apre direttamente MainActivity.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SNOOZE = "ACTION_SNOOZE_NOTIFICATION"
        // Azione delle notifiche create dalle versioni precedenti (pulsante "Chiudi")
        private const val ACTION_DISMISS_OLD = "ACTION_DISMISS_NOTIFICATION"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
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
