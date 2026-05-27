package com.stacca.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.stacca.app.R
import com.stacca.app.data.PreferencesManager

/**
 * Activity mostrata quando il trial di 5 giorni è scaduto e l'utente non è loggato.
 * L'utente non può tornare indietro — deve registrarsi.
 */
class TrialExpiredActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trial_expired)

        val prefs = PreferencesManager(this)

        // Mostra i giorni usati
        val tvDays = findViewById<TextView>(R.id.tvTrialDays)
        tvDays.text = getString(R.string.trial_days_used, prefs.consecutiveUseDays)

        // Bottone vai al login/registrazione
        findViewById<MaterialButton>(R.id.btnTrialRegister).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            // Non fare finish() — l'utente non può tornare all'app senza registrarsi
        }

        // Blocca il tasto back
        onBackPressedDispatcher.addCallback(
            this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Non fare nulla — sei bloccato qui finché non ti registri 😈
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        // Se nel frattempo l'utente si è loggato, vai alla Main
        val prefs = PreferencesManager(this)
        if (prefs.isLoggedIn) {
            prefs.trialExpired = false
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }
}
