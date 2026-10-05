package com.stacca.app.ui

import android.content.Intent
import android.animation.ValueAnimator
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.stacca.app.R
import com.stacca.app.data.NotificationMessages
import com.stacca.app.data.PreferencesManager
import com.stacca.app.notifications.AlarmSoundManager
import com.stacca.app.notifications.NotificationHelper
import com.stacca.app.receivers.AlarmReceiver
import com.stacca.app.util.PreviewMode
import com.stacca.app.util.SystemBarsHelper

/**
 * Schermo rosso "bollettino d'emergenza", mostrato quando il livello di escalation
 * è alto (NUCLEAR o APOCALYPSE). Molto invasiva e fastidiosa!
 */
class FullScreenAlertActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private val handler = Handler(Looper.getMainLooper())
    private var overtimeMinutes: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fullscreen_alert)
        SystemBarsHelper.applyInsets(this)

        prefs = PreferencesManager(this)
        overtimeMinutes = intent.getIntExtra("overtime_minutes", 0)
        val levelName = intent.getStringExtra("level") ?: "NUCLEAR"

        setupUI(levelName)
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

        // Livello in alto: "LIVELLO 5 DI 6 · NUCLEARE"
        val levelNames = resources.getStringArray(R.array.notif_level_names)
        findViewById<TextView>(R.id.tvAlertLevel).text = getString(
            R.string.notif_level_label, level.ordinal + 1, levelNames[level.ordinal]
        ).uppercase()

        val (title, message) = NotificationMessages(this).getRandomMessage(level)
        findViewById<TextView>(R.id.tvAlertTitle).text = title
        findViewById<TextView>(R.id.tvAlertMessage).text = message

        // Segmenti: pieni fino al livello raggiunto
        val segments = findViewById<ViewGroup>(R.id.alertSegments)
        for (i in 0 until segments.childCount) {
            val color = if (i <= level.ordinal) 0xFFFFFFFF.toInt() else 0x40FFFFFF
            segments.getChildAt(i).background.mutate().setTint(color)
        }

        // Bordeaux che "respira"; all'Apocalisse diventa rosso pieno
        val (dark, light) = if (level == NotificationMessages.Level.APOCALYPSE) {
            R.color.alert_bg_apocalypse to R.color.alert_bg_apocalypse_light
        } else {
            R.color.alert_bg_nuclear to R.color.alert_bg_nuclear_light
        }
        startBreathing(ContextCompat.getColor(this, dark), ContextCompat.getColor(this, light))

        // Bottone "Ho staccato": ferma tutto per oggi
        findViewById<MaterialButton>(R.id.btnStopWork).setOnClickListener {
            stopWork()
        }

        // Bottone "Ancora X minuti": chiude lo schermo, l'escalation continua
        val snoozeMinutes = AlarmReceiver.getIntervalMinutes(prefs.escalationSpeed)
        findViewById<MaterialButton>(R.id.btnSnooze).apply {
            text = getString(R.string.btn_snooze, snoozeMinutes)
            setOnClickListener { snooze() }
        }
    }

    /** Lo sfondo si schiarisce e si scurisce lentamente, come una spia d'allarme. */
    private fun startBreathing(dark: Int, light: Int) {
        val root = findViewById<View>(R.id.alertRoot)
        window.setBackgroundDrawable(ColorDrawable(dark))
        ValueAnimator.ofArgb(dark, light).apply {
            duration = 1400
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { root.setBackgroundColor(it.animatedValue as Int) }
            start()
        }
    }

    private fun startOvertimeCounter() {
        val tvCounter = findViewById<TextView>(R.id.tvOvertimeCounter)
        // Fine turno vero; in anteprima si simula uno straordinario di 26 minuti
        val endMillis = if (PreviewMode.isOn(intent)) {
            System.currentTimeMillis() - 26 * 60_000L
        } else {
            prefs.nextShiftEndMillis
        }

        handler.post(object : Runnable {
            override fun run() {
                val diffMillis = System.currentTimeMillis() - endMillis
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
        if (prefs.soundEnabled) {
            AlarmSoundManager.start(this, R.raw.stacca_alarm)
        }
        
        // Ferma dopo 5 secondi come fallback
        handler.postDelayed({ AlarmSoundManager.stop() }, 5000)
    }

    private fun stopWork() {
        AlarmSoundManager.stop()
        // In anteprima (solo versione di prova) si chiude e basta, senza registrare lo stacco
        if (PreviewMode.isOn(intent)) {
            finish()
            return
        }
        NotificationHelper(this).cancelAll()

        finish()

        // MainActivity registra lo stacco, ferma l'allarme e mostra il risultato
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_HO_STACCATO, true)
        })
    }

    private fun snooze() {
        AlarmSoundManager.stop()
        NotificationHelper(this).cancelAll()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        AlarmSoundManager.stop()
        handler.removeCallbacksAndMessages(null)
    }
}
