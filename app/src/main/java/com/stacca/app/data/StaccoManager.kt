package com.stacca.app.data

import android.content.Context
import com.stacca.app.notifications.AlarmSoundManager
import com.stacca.app.notifications.NotificationHelper
import com.stacca.app.receivers.AlarmReceiver

/**
 * "Ho staccato": un solo punto che fa tutto, usato sia dall'app sia dal pulsante
 * della notifica (che così non deve aprire l'app).
 */
object StaccoManager {

    /**
     * Registra lo stacco, ferma suono/notifiche/escalation e riprogramma
     * l'allarme per domani alla stessa ora. Restituisce i minuti di straordinario.
     */
    fun registerStacco(context: Context): Int {
        val prefs = PreferencesManager(context)
        val shiftEnd = prefs.nextShiftEndMillis
        val overtimeMillis = if (shiftEnd > 0) {
            (System.currentTimeMillis() - shiftEnd).coerceAtLeast(0L)
        } else {
            0L
        }
        val overtimeMinutes = (overtimeMillis / 60_000).toInt()

        // Storico: il livello raggiunto è il numero di promemoria già partiti (0-6)
        HistoryStore(context).add(
            HistoryStore.Entry(
                timestampMillis = System.currentTimeMillis(),
                endHour = prefs.endHour,
                endMinute = prefs.endMinute,
                overtimeMinutes = overtimeMinutes,
                level = prefs.currentEscalationStep.coerceIn(0, 6)
            )
        )

        prefs.registraStaccato(overtimeMinutes)

        prefs.paywallShownToday = false
        prefs.resetEscalation()
        AlarmSoundManager.stop()
        AlarmReceiver.cancelAlarm(context)
        NotificationHelper(context).cancelAll()

        // Riavvio automatico: l'orario di oggi è passato, quindi l'allarme scatta domani
        AlarmReceiver.scheduleAlarm(context, prefs.endHour, prefs.endMinute)
        return overtimeMinutes
    }
}
