package com.stacca.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.stacca.app.data.PreferencesManager
import com.stacca.app.notifications.AlarmSoundManager
import com.stacca.app.notifications.NotificationHelper
import java.util.Calendar

/**
 * Receiver per le azioni delle notifiche (OK Stacco / Ho staccato!, etc.).
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "ACTION_DISMISS_NOTIFICATION" -> {
                val prefs = PreferencesManager(context)
                
                // Silenzia l'allarme e chiude la notifica, MA NON registra lo staccato
                AlarmSoundManager.stop()
                val notificationHelper = NotificationHelper(context)
                notificationHelper.cancelAll()
                prefs.resetEscalation()
                
                // Opzionale: riapre l'app
                val mainIntent = Intent(context, com.stacca.app.ui.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(mainIntent)
            }
        }
    }
}
