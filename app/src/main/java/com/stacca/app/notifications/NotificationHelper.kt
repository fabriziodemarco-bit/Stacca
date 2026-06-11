package com.stacca.app.notifications

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
import com.stacca.app.receivers.NotificationActionReceiver
import com.stacca.app.ui.FullScreenAlertActivity


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
        val (title, message) = NotificationMessages.getRandomMessage(level)

        val channel = if (level.ordinal >= NotificationMessages.Level.INSISTENT.ordinal) {
            CHANNEL_URGENT
        } else {
            CHANNEL_NORMAL
        }

        // Intent per aprire l'app
        val fullScreenIntent = Intent(context, FullScreenAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("overtime_minutes", overtimeMinutes)
        }
        val fullScreenPending = PendingIntent.getActivity(
            context, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Azione: OK Stacco
        val stopIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "ACTION_STOP_WORK"
        }
        val stopPending = PendingIntent.getBroadcast(
            context, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )



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
        val collapsedBody = "Basta lavorare. Vivi."
        val expandedBody = if (premiumTeaser) {
            "$title\n$message\n\n🔒 Con Premium a quest'ora ti starei già urlando contro (livello NUCLEARE)"
        } else {
            "$title\n$message"
        }

        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(collapsedBody)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("STACCA!")
                    .bigText(expandedBody)
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(level.ordinal >= NotificationMessages.Level.AGGRESSIVE.ordinal)
            .setContentIntent(fullScreenPending)
            .setFullScreenIntent(fullScreenPending, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.btn_ok_stacco), stopPending)


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
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
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
