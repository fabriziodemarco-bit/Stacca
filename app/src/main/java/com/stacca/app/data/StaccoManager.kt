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

    /** Dopo quanto straordinario senza risposta Stacca si arrende per la sera. */
    const val GIVE_UP_AFTER_MILLIS = 3 * 60 * 60 * 1000L

    /**
     * Se il turno è finito da più di 3 ore e nessuno ha toccato "Ho staccato" (allarmi ignorati,
     * telefono spento, app chiusa), la sera si chiude da sola: il giorno va nello storico come
     * "non chiuso", la serie si azzera, gli allarmi si fermano e si riparte dal prossimo turno.
     * Restituisce true se ha chiuso qualcosa.
     */
    fun closeAbandonedShift(context: Context, notify: Boolean): Boolean {
        val prefs = PreferencesManager(context)
        val shiftEnd = prefs.nextShiftEndMillis
        if (!prefs.isAlarmActive || shiftEnd <= 0) return false
        if (System.currentTimeMillis() - shiftEnd < GIVE_UP_AFTER_MILLIS) return false

        // Nello storico il giorno è quello del turno (non quello in cui ce ne accorgiamo)
        HistoryStore(context).add(
            HistoryStore.Entry(
                timestampMillis = shiftEnd,
                endHour = prefs.endHour,
                endMinute = prefs.endMinute,
                overtimeMinutes = 0,
                level = prefs.currentEscalationStep.coerceIn(0, 6),
                unclosed = true
            )
        )
        prefs.streakCount = 0

        prefs.paywallShownToday = false
        prefs.resetEscalation()
        AlarmSoundManager.stop()
        AlarmReceiver.cancelAlarm(context)
        NotificationHelper(context).cancelAll()
        AlarmReceiver.scheduleAlarm(context, prefs.endHour, prefs.endMinute)

        if (notify) NotificationHelper(context).showGiveUp()
        return true
    }
}
