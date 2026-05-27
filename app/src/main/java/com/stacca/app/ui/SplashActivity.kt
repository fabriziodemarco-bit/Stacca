package com.stacca.app.ui

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.stacca.app.R
import com.stacca.app.data.PreferencesManager

/**
 * Splash screen dell'app Stacca!
 * Mostra il logo e il tagline con un'animazione fade-in,
 * poi smista l'utente in base allo stato del trial:
 *  - Loggato → MainActivity
 *  - Trial scaduto → TrialExpiredActivity
 *  - Tutto OK → LoginActivity
 */
class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val splashContent = findViewById<View>(R.id.splashContent)

        // Fade-in del contenuto (800ms)
        val fadeIn = ObjectAnimator.ofFloat(splashContent, View.ALPHA, 0f, 1f).apply {
            duration = 800
            interpolator = DecelerateInterpolator()
        }
        fadeIn.start()

        // Dopo 2.5 secondi totali, smista l'utente
        handler.postDelayed({
            navigateNext()
        }, 2500)
    }

    private fun navigateNext() {
        val prefs = PreferencesManager(this)

        // Traccia l'utilizzo giornaliero (incrementa contatore trial)
        prefs.trackDailyUsage()

        val destination = when {
            // Già loggato → vai diretto alla main
            prefs.isLoggedIn -> Intent(this, MainActivity::class.java)
            // Trial scaduto e non loggato → muro di registrazione
            prefs.trialExpired -> Intent(this, TrialExpiredActivity::class.java)
            // Tutto OK → login/registrazione (con skip disponibile)
            else -> Intent(this, LoginActivity::class.java)
        }

        startActivity(destination)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
