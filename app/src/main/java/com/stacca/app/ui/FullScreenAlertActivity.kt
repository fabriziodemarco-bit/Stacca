package com.stacca.app.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.stacca.app.R
import com.stacca.app.data.NotificationMessages
import com.stacca.app.data.PreferencesManager
import com.stacca.app.notifications.NotificationHelper
import com.stacca.app.receivers.AlarmReceiver
import java.util.*

/**
 * Activity a schermo intero che appare quando il livello di escalation
 * è alto (NUCLEAR o APOCALYPSE). Molto invasiva e fastidiosa!
 */
class FullScreenAlertActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private val handler = Handler(Looper.getMainLooper())
    private var overtimeMinutes: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fullscreen_alert)

        prefs = PreferencesManager(this)
        overtimeMinutes = intent.getIntExtra("overtime_minutes", 0)
        val levelName = intent.getStringExtra("level") ?: "NUCLEAR"

        setupUI(levelName)
        startAnimations()
        startOvertimeCounter()
        playAlarmSound()

        // Blocca il tasto back — l'utente DEVE premere un bottone! 😈
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Non fare niente! Mwahaha!
            }
        })
    }

    private fun setupUI(levelName: String) {
        val level = try {
            NotificationMessages.Level.valueOf(levelName)
        } catch (e: Exception) {
            NotificationMessages.Level.NUCLEAR
        }

        val (title, message) = NotificationMessages.getRandomMessage(level)

        findViewById<TextView>(R.id.tvAlertTitle).text = title
        findViewById<TextView>(R.id.tvAlertMessage).text = message

        // Emoji animata
        val emojis = listOf("🚨", "💀", "🔥", "☠️", "💣", "😱", "🤯", "⚠️")
        val tvEmoji = findViewById<TextView>(R.id.tvAlertEmoji)
        handler.post(object : Runnable {
            override fun run() {
                tvEmoji.text = emojis.random()
                handler.postDelayed(this, 800)
            }
        })

        // Bottone Stacco
        findViewById<MaterialButton>(R.id.btnStopWork).setOnClickListener {
            stopWork()
        }

    }

    private fun startAnimations() {
        val title = findViewById<TextView>(R.id.tvAlertTitle)

        // Animazione pulsante del titolo
        val scaleX = ObjectAnimator.ofFloat(title, View.SCALE_X, 1f, 1.1f, 1f)
        val scaleY = ObjectAnimator.ofFloat(title, View.SCALE_Y, 1f, 1.1f, 1f)
        val animSet = AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 1000
            interpolator = AccelerateDecelerateInterpolator()
        }
        animSet.start()

        // Ripeti l'animazione
        handler.postDelayed(object : Runnable {
            override fun run() {
                animSet.start()
                handler.postDelayed(this, 1500)
            }
        }, 1500)

        // Bounce sul bottone principale
        val btn = findViewById<MaterialButton>(R.id.btnStopWork)
        val bounceY = ObjectAnimator.ofFloat(btn, View.TRANSLATION_Y, 0f, -20f, 0f)
        bounceY.duration = 600
        bounceY.interpolator = BounceInterpolator()
        handler.postDelayed(object : Runnable {
            override fun run() {
                bounceY.start()
                handler.postDelayed(this, 2000)
            }
        }, 1000)
    }

    private fun startOvertimeCounter() {
        val tvCounter = findViewById<TextView>(R.id.tvOvertimeCounter)
        val endTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, prefs.endHour)
            set(Calendar.MINUTE, prefs.endMinute)
            set(Calendar.SECOND, 0)
        }

        handler.post(object : Runnable {
            override fun run() {
                val now = Calendar.getInstance()
                val diffMillis = now.timeInMillis - endTime.timeInMillis
                if (diffMillis > 0) {
                    val hours = diffMillis / 3600000
                    val minutes = (diffMillis % 3600000) / 60000
                    val seconds = (diffMillis % 60000) / 1000
                    tvCounter.text = String.format("+%02d:%02d:%02d", hours, minutes, seconds)
                    overtimeMinutes = (diffMillis / 60000).toInt()
                }
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun playAlarmSound() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(this, alarmUri)
            ringtone?.play()

            // Ferma dopo 5 secondi
            handler.postDelayed({ ringtone?.stop() }, 5000)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopWork() {
        prefs.paywallShownToday = false
        AlarmReceiver.cancelAlarm(this)
        NotificationHelper(this).cancelAll()

        // Salva i minuti di straordinario come pending — la MainActivity lo mostrerà in onResume
        if (overtimeMinutes > 0) {
            prefs.hasPendingTempoNonVissuto = true
            prefs.pendingTempoNonVissutoMinutes = overtimeMinutes
        }

        // Riprogramma per domani
        if (prefs.isAlarmActive) {
            AlarmReceiver.scheduleAlarm(this, prefs.endHour, prefs.endMinute)
        }

        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
