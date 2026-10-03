package com.stacca.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receiver che ripristina gli allarmi dopo il riavvio del dispositivo.
 * Da Android 15 arriva anche quando l'app viene riaperta dopo un arresto forzato.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AlarmReceiver.restoreAfterRestart(context)
        }
    }
}
