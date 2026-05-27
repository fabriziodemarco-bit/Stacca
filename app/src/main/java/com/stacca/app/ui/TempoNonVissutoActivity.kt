package com.stacca.app.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.stacca.app.R
import com.stacca.app.data.PreferencesManager

/**
 * Schermata "Tempo Non Vissuto" — mostrata dopo che l'utente stacca dal lavoro in ritardo.
 * Mostra quante ore e minuti di vita ha regalato al lavoro oggi.
 * Tono: ironico e affettuosamente pungente.
 */
class TempoNonVissutoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OVERTIME_MINUTES = "overtime_minutes"

        private val MESSAGGI_IRONICI = listOf(
            "Il tuo gatto si è chiesto dove fossi. 🐱",
            "Tua mamma ti ha chiamato. Hai perso la chiamata. Ovviamente. 📵",
            "Il tramonto è già finito. Che peccato. 🌅",
            "Quante serie TV avresti potuto guardare? Molte. 📺",
            "Potresti aver letto un intero capitolo di un libro. Ma no. 📚",
            "Il divano ti aspettava. Il divano è triste. 🛋️",
            "La pizza si è raffreddata. Ma almeno il bug è fixato. 🍕",
            "Il tuo alter ego rilassato ti invidia. 😎",
            "Anche le piante hanno più vita sociale di te oggi. 🌿",
            "Nessuno sul letto di morte ha mai detto 'avrei voluto lavorare di più'. ⚰️"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tempo_non_vissuto)

        val prefs = PreferencesManager(this)

        // Prendi i minuti di straordinario dall'intent (o dalle prefs se pending)
        val overtimeMinutes = if (intent.hasExtra(EXTRA_OVERTIME_MINUTES)) {
            intent.getIntExtra(EXTRA_OVERTIME_MINUTES, 0)
        } else {
            prefs.pendingTempoNonVissutoMinutes
        }

        // Resetta il pending
        prefs.hasPendingTempoNonVissuto = false
        prefs.pendingTempoNonVissutoMinutes = 0

        setupUI(overtimeMinutes)
        startAnimations()
    }

    private fun setupUI(overtimeMinutes: Int) {
        // Mostra ore:minuti
        val hours = overtimeMinutes / 60
        val minutes = overtimeMinutes % 60
        val tvTime = findViewById<TextView>(R.id.tvTempoTime)
        tvTime.text = String.format("%02d:%02d", hours, minutes)

        // Messaggio ironico casuale
        val tvMessage = findViewById<TextView>(R.id.tvTempoMessage)
        tvMessage.text = "\"${MESSAGGI_IRONICI.random()}\""

        // Bottone chiudi
        findViewById<MaterialButton>(R.id.btnTempoClose).setOnClickListener {
            finish()
        }
    }

    private fun startAnimations() {
        // Fade-in titolo
        val tvTitle = findViewById<TextView>(R.id.tvTempoTitle)
        tvTitle.alpha = 0f
        tvTitle.animate()
            .alpha(1f)
            .setDuration(600)
            .setStartDelay(100)
            .start()

        // Fade-in emoji con overshoot
        val tvEmoji = findViewById<TextView>(R.id.tvTempoEmoji)
        tvEmoji.scaleX = 0f
        tvEmoji.scaleY = 0f
        val scaleX = ObjectAnimator.ofFloat(tvEmoji, View.SCALE_X, 0f, 1.3f, 1f)
        val scaleY = ObjectAnimator.ofFloat(tvEmoji, View.SCALE_Y, 0f, 1.3f, 1f)
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 700
            interpolator = AccelerateDecelerateInterpolator()
            startDelay = 200
        }.start()

        // Fade-in timer (con piccola animazione scale)
        val tvTime = findViewById<TextView>(R.id.tvTempoTime)
        tvTime.alpha = 0f
        tvTime.scaleX = 0.8f
        tvTime.scaleY = 0.8f
        tvTime.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(600)
            .setStartDelay(400)
            .start()

        // Fade-in messaggio ironico
        val tvMessage = findViewById<TextView>(R.id.tvTempoMessage)
        tvMessage.alpha = 0f
        tvMessage.animate()
            .alpha(1f)
            .setDuration(600)
            .setStartDelay(700)
            .start()
    }
}
