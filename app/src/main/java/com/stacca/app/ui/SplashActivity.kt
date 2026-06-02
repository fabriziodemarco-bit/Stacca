package com.stacca.app.ui

import android.animation.ObjectAnimator
import android.content.Intent
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

        // Traccia l'utilizzo giornaliero (incrementa contatore trial)
        prefs.trackDailyUsage()

        if (prefs.isLoggedIn) {
            // Se l'utente risulta loggato, verifica che la sessione Supabase
            // sia ancora valida prima di mandarlo alla main
            lifecycleScope.launch {
                val authManager = AuthManager(this@SplashActivity)
                val sessionValid = authManager.restoreSession()

                if (sessionValid) {
                    Log.d(TAG, "Sessione Supabase valida → MainActivity")
                    navigateToDestination(Intent(this@SplashActivity, MainActivity::class.java))
                } else {
                    Log.w(TAG, "Sessione Supabase non valida → LoginActivity")
                    // restoreSession() ha già resettato isLoggedIn = false
                    navigateToDestination(
                        if (prefs.trialExpired) {
                            Intent(this@SplashActivity, TrialExpiredActivity::class.java)
                        } else {
                            Intent(this@SplashActivity, LoginActivity::class.java)
                        }
                    )
                }
            }
        } else {
            // Non loggato — decidi in base al trial
            val destination = if (prefs.trialExpired) {
                Intent(this, TrialExpiredActivity::class.java)
            } else {
                Intent(this, LoginActivity::class.java)
            }
            navigateToDestination(destination)
        }
    }

    private fun navigateToDestination(destination: Intent) {
        startActivity(destination)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
