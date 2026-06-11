package com.stacca.app.ui

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.stacca.app.R
import com.stacca.app.auth.AuthManager
import com.stacca.app.data.PreferencesManager
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SplashActivity"
    }

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

        // Assicura che la data del primo avvio sia registrata (per il trial a calendario)
        prefs.ensureFirstUseDateSet()

        // Controlla se il trial è appena scaduto e la schermata soft NON è ancora stata mostrata.
        // In quel caso mostriamo TrialExpiredActivity UNA SOLA VOLTA come primo step,
        // poi l'utente arriva sempre alla MainActivity (piano gratuito o premium).
        val trialAppenaScaduto = !prefs.isTrialActive && !prefs.isPremium && !prefs.trialEndShown

        if (prefs.isLoggedIn) {
            // Se l'utente risulta loggato, verifica che la sessione Supabase
            // sia ancora valida prima di mandarlo alla main
            lifecycleScope.launch {
                val authManager = AuthManager(this@SplashActivity)
                val sessionValid = authManager.restoreSession()

                if (sessionValid) {
                    Log.d(TAG, "Sessione Supabase valida → ${if (trialAppenaScaduto) "TrialExpired (soft)" else "MainActivity"}")
                    if (trialAppenaScaduto) {
                        navigateToDestination(Intent(this@SplashActivity, TrialExpiredActivity::class.java))
                    } else {
                        navigateToDestination(Intent(this@SplashActivity, MainActivity::class.java))
                    }
                } else {
                    Log.w(TAG, "Sessione Supabase non valida → LoginActivity")
                    // restoreSession() ha già resettato isLoggedIn = false
                    // L'app non si blocca mai: va alla Login (l'utente può usarla da guest)
                    navigateToDestination(Intent(this@SplashActivity, LoginActivity::class.java))
                }
            }
        } else {
            // Non loggato — va sempre alla Login (da cui si può "Continua senza account")
            // Se il trial è appena scaduto, TrialExpired sarà mostrata al ritorno dalla Login
            // oppure subito se l'utente è già in grado di accedere alla Main (es. skip login)
            // Semplicità: mostriamo TrialExpired prima di Login se il flag è attivo
            val destination = if (trialAppenaScaduto) {
                Intent(this, TrialExpiredActivity::class.java)
            } else {
                Intent(this, LoginActivity::class.java)
            }
            navigateToDestination(destination)
        }
    }

    private fun navigateToDestination(destination: Intent) {
        startActivity(destination)
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
