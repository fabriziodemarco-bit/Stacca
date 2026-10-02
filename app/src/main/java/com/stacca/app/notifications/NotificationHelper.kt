package com.stacca.app.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.stacca.app.R
import com.stacca.app.data.NotificationMessages
import com.stacca.app.receivers.AlarmReceiver
import com.stacca.app.receivers.NotificationActionReceiver
import com.stacca.app.ui.MainActivity


/**
 * Helper per la creazione e l'invio di notifiche con escalation.
 */
class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_NORMAL = "stacca_normal"
        const val CHANNEL_URGENT = "stacca_urgent"
        const val NOTIFICATION_ID = 42
        const val FULLSCREEN_NOTIFICATION_ID = 43
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        // Canale notifiche normali
        val normalChannel = NotificationChannel(
            CHANNEL_NORMAL,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500)
        }

        // Canale notifiche urgenti (per livelli alti)
        val urgentChannel = NotificationChannel(
            CHANNEL_URGENT,
            context.getString(R.string.notif_urgent_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_urgent_channel_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            setSound(
                alarmSound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(normalChannel)
        manager.createNotificationChannel(urgentChannel)
    }

    /**
     * Invia una notifica basata sul livello di escalation.
     * Rispetta le preferenze utente per suono e vibrazione.
     *
     * @param premiumTeaser se true (e solo UNA volta al giorno, deciso da AlarmReceiver),
     *                      aggiunge al BigText una riga ironica su cosa si perde senza Premium.
     */
    fun sendEscalatingNotification(
        level: NotificationMessages.Level,
        overtimeMinutes: Int,
        soundEnabled: Boolean = true,
        vibrationEnabled: Boolean = true,
        premiumTeaser: Boolean = false
    ) {
        // Selezione sequenziale: ogni notifica mostra un messaggio diverso
        val prefs = com.stacca.app.data.PreferencesManager(context)
        val (title, message, nextIndex) = NotificationMessages(context).getSequentialMessage(
            level, prefs.currentMessageIndex
        )
        prefs.currentMessageIndex = nextIndex

        val channel = if (level.ordinal >= NotificationMessages.Level.INSISTENT.ordinal) {
            CHANNEL_URGENT
        } else {
            CHANNEL_NORMAL
        }

        // Tocco sulla notifica: apre semplicemente l'app (NON lo schermo rosso)
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPending = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Azione "Ho staccato": apre l'app, che registra lo stacco e ferma tutto per oggi
        val hoStaccatoIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_HO_STACCATO, true)
        }
        val hoStaccatoPending = PendingIntent.getActivity(
            context, 2, hoStaccatoIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Azione "Ancora X minuti": zittisce la notifica, l'escalation continua alla prossima
        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE
        }
        val snoozePending = PendingIntent.getBroadcast(
            context, 1, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozeMinutes = AlarmReceiver.getIntervalMinutes(prefs.escalationSpeed)


        // Vibration pattern basato sul livello
        val vibrationPattern = when {
            level.ordinal >= NotificationMessages.Level.NUCLEAR.ordinal ->
                longArrayOf(0, 1000, 300, 1000, 300, 1000, 300, 1000)
            level.ordinal >= NotificationMessages.Level.INSISTENT.ordinal ->
                longArrayOf(0, 800, 400, 800, 400, 800)
            else -> longArrayOf(0, 500, 200, 500)
        }

        // Testo collassato: sempre "Basta lavorare. Vivi."
        // Testo espanso: titolo grande "STACCA!" + titolo e messaggio originali sotto
        // Se premiumTeaser=true, aggiunge riga upsell ironica (una volta al giorno)
        val collapsedBody = context.getString(R.string.app_tagline)
        val expandedBody = if (premiumTeaser) {
            "$title\n$message\n\n${context.getString(R.string.notif_premium_teaser)}"
        } else {
            "$title\n$message"
        }

        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(collapsedBody)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(context.getString(R.string.fullscreen_title))
                    .bigText(expandedBody)
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(level.ordinal >= NotificationMessages.Level.AGGRESSIVE.ordinal)
            .setContentIntent(openAppPending)
            .addAction(0, context.getString(R.string.btn_ho_staccato), hoStaccatoPending)
            .addAction(0, context.getString(R.string.btn_snooze, snoozeMinutes), snoozePending)


        // Vibrazione condizionale
        if (vibrationEnabled) {
            builder.setVibrate(vibrationPattern)
        }

        // Suono condizionale
        if (soundEnabled && level.ordinal >= NotificationMessages.Level.INSISTENT.ordinal) {
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            builder.setSound(alarmSound)
        } else if (!soundEnabled) {
            builder.setSilent(true)
        }

        try {
            val notification = builder.build()
            // Dal livello Aggressivo il suono si ripete finché l'utente non risponde
            if (soundEnabled && level.ordinal >= NotificationMessages.Level.AGGRESSIVE.ordinal) {
                notification.flags = notification.flags or Notification.FLAG_INSISTENT
            }
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permessi notifica non concessi
            e.printStackTrace()
        }
    }


    /**
     * Cancella tutte le notifiche.
     */
    fun cancelAll() {
        NotificationManagerCompat.from(context).cancelAll()
    }
}
