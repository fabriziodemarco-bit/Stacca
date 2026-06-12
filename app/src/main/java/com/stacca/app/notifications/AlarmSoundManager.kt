package com.stacca.app.notifications

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.util.Log

object AlarmSoundManager {
    private var mediaPlayer: MediaPlayer? = null

    @Synchronized
    fun start(context: Context) {
        if (mediaPlayer?.isPlaying == true) {
            Log.d("AlarmSoundManager", "MediaPlayer già attivo, ignoro lo start.")
            return
        }

        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
            Log.d("AlarmSoundManager", "MediaPlayer avviato con successo.")
        } catch (e: Exception) {
            Log.e("AlarmSoundManager", "Errore nell'avvio del MediaPlayer", e)
        }
    }

    @Synchronized
    fun stop() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                    Log.d("AlarmSoundManager", "MediaPlayer fermato.")
                }
                it.release()
                Log.d("AlarmSoundManager", "MediaPlayer rilasciato.")
            }
        } catch (e: Exception) {
            Log.e("AlarmSoundManager", "Errore nello stop del MediaPlayer", e)
        } finally {
            mediaPlayer = null
        }
    }
}
