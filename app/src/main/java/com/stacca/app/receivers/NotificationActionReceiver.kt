package com.stacca.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.stacca.app.data.PreferencesManager
import com.stacca.app.notifications.NotificationHelper
import com.stacca.app.ui.CelebrationActivity
import com.stacca.app.ui.TempoNonVissutoActivity
import java.util.Calendar

/**
 * Receiver per le azioni delle notifiche (OK Stacco / Ho staccato!, etc.).
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "ACTION_STOP_WORK" -> {
                val prefs = PreferencesManager(context)
                prefs.paywallShownToday = false

                // Calcola i minuti di straordinario (mai negativi)
                val now = Calendar.getInstance()
                val endTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, prefs.endHour)
                    set(Calendar.MINUTE, prefs.endMinute)
                    set(Calendar.SECOND, 0)
                }
                val overtimeMillis = (now.timeInMillis - endTime.timeInMillis).coerceAtLeast(0L)
                val overtimeMinutes = (overtimeMillis / 60_000).toInt()

                // Legge lo streak PRIMA di registraStaccato (che lo azzera in caso di ritardo)
                val streakBeforeReset = prefs.streakCount

                // Registra lo staccato e ottieni il risultato (idempotente)
                val result = prefs.registraStaccato(overtimeMinutes)

                // Cancella gli allarmi futuri per oggi e le notifiche attive
                AlarmReceiver.cancelAlarm(context)
                val notificationHelper = NotificationHelper(context)
                notificationHelper.cancelAll()

                // Riprogramma per domani (se l'allarme era attivo)
                if (prefs.isAlarmActive) {
                    AlarmReceiver.scheduleAlarm(context, prefs.endHour, prefs.endMinute)
                }

                if (result.isOnTime) {
                    // Staccato in orario → apri CelebrationActivity 🎉
                    val celebIntent = Intent(context, CelebrationActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(CelebrationActivity.EXTRA_STREAK_COUNT, result.streakCount)
                        putExtra(CelebrationActivity.EXTRA_BEST_STREAK, result.bestStreak)
                        putExtra(CelebrationActivity.EXTRA_IS_NEW_RECORD, result.isNewRecord)
                    }
                    context.startActivity(celebIntent)
                } else {
                    // Staccato in ritardo → imposta pending e apri TempoNonVissutoActivity
                    if (overtimeMinutes > 0) {
                        prefs.hasPendingTempoNonVissuto = true
                        prefs.pendingTempoNonVissutoMinutes = overtimeMinutes
                    }
                    val tempoIntent = Intent(context, TempoNonVissutoActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(TempoNonVissutoActivity.EXTRA_OVERTIME_MINUTES, overtimeMinutes)
                        putExtra(TempoNonVissutoActivity.EXTRA_LOST_STREAK, streakBeforeReset)
                    }
                    context.startActivity(tempoIntent)
                }
            }
        }
    }
}
