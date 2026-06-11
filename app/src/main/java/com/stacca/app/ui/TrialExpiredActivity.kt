package com.stacca.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.stacca.app.R
import com.stacca.app.data.PreferencesManager

/**
 * Schermata informativa mostrata UNA SOLA VOLTA quando il trial scade.
 *
 * NON blocca più l'app: l'utente può scegliere tra "Passa a Premium"
 * e "Continua gratis" (→ MainActivity con piano gratuito livelli 1-3).
 *
 * Il flag [PreferencesManager.trialEndShown] garantisce che venga mostrata
 * al massimo una volta. Dopo il dismiss, l'app degrada al piano gratuito
 * senza ulteriori interruzioni.
 */
class TrialExpiredActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trial_expired)

        val prefs = PreferencesManager(this)

        // Segna subito come mostrata: anche se l'utente preme "indietro",
        // non rivedrà questa schermata (il routing la mostra una sola volta)
        prefs.trialEndShown = true

        // Bottone principale: vai al paywall
        findViewById<MaterialButton>(R.id.btnTrialRegister).setOnClickListener {
            startActivity(Intent(this, PaywallActivity::class.java))
        }

        // Bottone secondario: continua gratis → MainActivity
        findViewById<MaterialButton>(R.id.btnTrialContinueFree).setOnClickListener {
            navigateToMain()
        }

        // Back presssed → continua gratis (non blocchiamo più nulla)
        onBackPressedDispatcher.addCallback(
            this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    navigateToMain()
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        // Se nel frattempo l'utente ha acquistato Premium (dalla PaywallActivity),
        // vai direttamente alla Main senza tornare qui
        val prefs = PreferencesManager(this)
        if (prefs.isPremium) {
            navigateToMain()
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
