package com.stacca.app.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.stacca.app.R
import com.stacca.app.data.NotificationMessages
import com.stacca.app.receivers.AlarmReceiver
import com.stacca.app.receivers.NotificationActionReceiver
import com.stacca.app.ui.FullScreenAlertActivity
import com.stacca.app.ui.MainActivity


/**
 * Helper per la creazione e l'invio di notifiche con escalation.
 */
class NotificationHelper(private val context: Context) {

    companion object {
        // "_v2": Android non permette di cambiare il suono di un canale già creato,
        // quindi i canali con i suoni di Stacca hanno un nome nuovo
        const val CHANNEL_NORMAL = "stacca_normal_v2"
        const val CHANNEL_URGENT = "stacca_urgent_v2"
        private val OLD_CHANNELS = listOf("stacca_normal", "stacca_urgent")
        const val NOTIFICATION_ID = 42
        const val FULLSCREEN_NOTIFICATION_ID = 43
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        OLD_CHANNELS.forEach { manager.deleteNotificationChannel(it) }

        // Canale notifiche normali (livelli 1-2): "ding" di Stacca sul volume notifiche
        val normalChannel = NotificationChannel(
            CHANNEL_NORMAL,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500)
            setSound(
                rawSoundUri(R.raw.stacca_ding),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }

        // Canale notifiche urgenti (livelli 3-6): bip da sveglia sul volume sveglia
        val urgentChannel = NotificationChannel(
            CHANNEL_URGENT,
            context.getString(R.string.notif_urgent_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_urgent_channel_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
            setSound(
                rawSoundUri(R.raw.stacca_alarm),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }

        manager.createNotificationChannel(normalChannel)
        manager.createNotificationChannel(urgentChannel)
    }

    private fun rawSoundUri(soundRes: Int): Uri =
        Uri.parse("android.resource://${context.packageName}/$soundRes")

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

        // Tocco sulla notifica:
        // - livelli 4-6 con Premium (e schermo intero attivo): apre lo schermo rosso
        // - altrimenti: apre l'app
        val showRedScreen = prefs.hasFullAccess && prefs.fullScreenEnabled &&
            level.ordinal >= NotificationMessages.Level.AGGRESSIVE.ordinal
        val openAppIntent = if (showRedScreen) {
            Intent(context, FullScreenAlertActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("overtime_minutes", overtimeMinutes)
                putExtra("level", level.name)
            }
        } else {
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        }
        val openAppPending = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Azione "Ho staccato": registra lo stacco senza aprire l'app (poi un breve messaggio di premio)
        val hoStaccatoIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_HO_STACCATO
        }
        val hoStaccatoPending = PendingIntent.getBroadcast(
            context, 2, hoStaccatoIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Azione "Ancora un po'": zittisce la notifica, l'escalation continua alla prossima
        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE
        }
        val snoozePending = PendingIntent.getBroadcast(
            context, 1, snoozeIntent,
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

        // Il messaggio del livello si vede già a notifica chiusa, così ogni notifica è diversa.
        // In alto: "Livello X di 6 · Nome", per rendere visibile l'escalation.
        // Se premiumTeaser=true, aggiunge riga upsell ironica (una volta al giorno)
        val levelName = context.resources.getStringArray(R.array.notif_level_names)[level.ordinal]
        val levelLabel = context.getString(R.string.notif_level_label, level.ordinal + 1, levelName)
        val expandedBody = if (premiumTeaser) {
            "$message\n\n${context.getString(R.string.notif_premium_teaser)}"
        } else {
            message
        }

        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setSubText(levelLabel)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedBody))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(level.ordinal >= NotificationMessages.Level.AGGRESSIVE.ordinal)
            .setContentIntent(openAppPending)
            .addAction(0, context.getString(R.string.btn_ho_staccato), hoStaccatoPending)
            .addAction(0, context.getString(R.string.btn_snooze), snoozePending)


        // Vibrazione condizionale
        if (vibrationEnabled) {
            builder.setVibrate(vibrationPattern)
        }

        // Il suono lo decide il canale (ding per i livelli 1-2, allarme dal 3);
        // se l'utente ha spento il suono nelle impostazioni, la notifica è muta
        if (!soundEnabled) {
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
     * Dopo "Ho staccato" dalla notifica: un breve messaggio di premio, muto,
     * che sparisce da solo dopo qualche secondo.
     */
    fun showStaccoReward(overtimeMinutes: Int, endTimeText: String) {
        val title = if (overtimeMinutes <= com.stacca.app.data.PreferencesManager.ON_TIME_THRESHOLD_MINUTES) {
            context.getString(R.string.notif_reward_title_ontime)
        } else {
            context.getString(R.string.notif_reward_title_late)
        }
        val openApp = PendingIntent.getActivity(
            context, 3, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_NORMAL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notif_reward_text, endTimeText))
            .setSilent(true)
            .setAutoCancel(true)
            .setTimeoutAfter(8_000)
            .setContentIntent(openApp)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    /** Stacca si arrende per la sera (3 ore senza risposta): avviso silenzioso, senza allarme. */
    fun showGiveUp() {
        val openApp = PendingIntent.getActivity(
            context, 4, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = context.getString(R.string.notif_giveup_text)
        val notification = NotificationCompat.Builder(context, CHANNEL_NORMAL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.notif_giveup_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
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
