package com.stacca.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.stacca.app.data.PreferencesManager

/**
 * Receiver che ripristina gli allarmi dopo il riavvio del dispositivo.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = PreferencesManager(context)
            if (prefs.isAlarmActive) {
                AlarmReceiver.scheduleAlarm(context, prefs.endHour, prefs.endMinute)
            }
        }
    }
}
