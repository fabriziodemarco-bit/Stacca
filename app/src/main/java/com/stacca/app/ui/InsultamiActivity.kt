package com.stacca.app.ui

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.stacca.app.R
import com.stacca.app.data.PreferencesManager
import com.stacca.app.util.SystemBarsHelper
import java.util.Calendar

/**
 * "Insultami": un minuto di fuoco. Un insulto ogni 5 secondi, in 3 fasi sempre più cattive.
 * Finisce quando l'utente preme "Ok, stacco" oppure allo scadere dei 60 secondi.
 * Gratis per tutti: è la funzione da far provare e condividere.
 */
class InsultamiActivity : AppCompatActivity() {

    companion object {
        private const val TOTAL_SECONDS = 60
        private const val SECONDS_PER_INSULT = 5
        private const val SECONDS_PER_PHASE = 20
    }

    private lateinit var prefs: PreferencesManager
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var root: View
    private lateinit var tvPhase: TextView
    private lateinit var tvEmoji: TextView
    private lateinit var tvInsult: TextView
    private lateinit var tvTimer: TextView

    private var elapsedSeconds = 0
    private var finished = false

    // Insulti ancora da usare per ogni fase (pescati a caso, senza ripetizioni)
    private val remaining = mutableMapOf<Int, MutableList<String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_insultami)
        SystemBarsHelper.applyInsets(this)

        prefs = PreferencesManager(this)
        root = findViewById(R.id.insultRoot)
        tvPhase = findViewById(R.id.tvInsultPhase)
        tvEmoji = findViewById(R.id.tvInsultEmoji)
        tvInsult = findViewById(R.id.tvInsult)
        tvTimer = findViewById(R.id.tvInsultTimer)

        findViewById<MaterialButton>(R.id.btnInsultOk).setOnClickListener { onOkStacco() }

        // Il tasto indietro non salva nessuno: si esce solo staccando 😈
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (finished) close()
            }
        })

        showNextInsult()
        handler.postDelayed(tick, 1000)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (finished) return
            elapsedSeconds++
            tvTimer.text = (TOTAL_SECONDS - elapsedSeconds).toString()

            if (elapsedSeconds >= TOTAL_SECONDS) {
                onTimeUp()
                return
            }
            if (elapsedSeconds % SECONDS_PER_INSULT == 0) {
                showNextInsult()
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun currentPhase(): Int = (elapsedSeconds / SECONDS_PER_PHASE).coerceAtMost(2)

    private fun showNextInsult() {
        val phase = currentPhase()
        val list = remaining.getOrPut(phase) { mutableListOf() }
        if (list.isEmpty()) {
            val arrayRes = when (phase) {
                0 -> R.array.insults_phase_1
                1 -> R.array.insults_phase_2
                else -> R.array.insults_phase_3
            }
            list.addAll(resources.getStringArray(arrayRes).toList().shuffled())
        }
        tvInsult.text = list.removeAt(0)

        val (phaseLabel, colorRes, emoji) = when (phase) {
            0 -> Triple(R.string.insult_phase_1, R.color.alert_aggressive, "😏")
            1 -> Triple(R.string.insult_phase_2, R.color.alert_nuclear, "😤")
            else -> Triple(R.string.insult_phase_3, R.color.alert_apocalypse, "🤬")
        }
        tvPhase.setText(phaseLabel)
        tvEmoji.text = emoji
        root.setBackgroundColor(ContextCompat.getColor(this, colorRes))

        shake(tvInsult, phase)
        vibrate(phase)
    }

    /** Scuote il testo: più forte a ogni fase. */
    private fun shake(view: View, phase: Int) {
        val amount = 10f + phase * 12f
        ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0f, amount, -amount, amount, -amount, 0f)
            .apply { duration = 400 }
            .start()
    }

    private fun vibrate(phase: Int) {
        if (!prefs.vibrationEnabled) return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val ms = 150L + phase * 250L
        vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /** L'utente si arrende e stacca: piccolo premio, poi si chiude. */
    private fun onOkStacco() {
        if (finished) {
            close()
            return
        }
        finished = true
        handler.removeCallbacksAndMessages(null)
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.alert_gentle))
        tvPhase.visibility = View.INVISIBLE
        tvTimer.visibility = View.INVISIBLE
        tvEmoji.text = "🎉"
        tvInsult.setText(R.string.insult_win)
        handler.postDelayed({ close() }, 2500)
    }

    /** Il minuto è finito e l'utente è ancora lì. */
    private fun onTimeUp() {
        finished = true
        tvPhase.visibility = View.INVISIBLE
        tvTimer.visibility = View.INVISIBLE
        tvEmoji.text = "💀"
        tvInsult.setText(R.string.insult_lose)
        vibrate(2)
    }

    /**
     * Se l'allarme è attivo e il turno è già finito, "Ok, stacco" vale come "Ho staccato":
     * MainActivity registra lo stacco e ferma l'allarme. Altrimenti si chiude e basta.
     */
    private fun close() {
        handler.removeCallbacksAndMessages(null)
        if (prefs.isAlarmActive && isAfterEndOfShift()) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_HO_STACCATO, true)
            })
        }
        finish()
    }

    private fun isAfterEndOfShift(): Boolean {
        val endTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, prefs.endHour)
            set(Calendar.MINUTE, prefs.endMinute)
            set(Calendar.SECOND, 0)
        }
        return Calendar.getInstance().after(endTime)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
