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

/**
 * Splash screen dell'app Stacca!
 * Mostra il logo e il tagline con un'animazione fade-in,
 * poi passa automaticamente a LoginActivity dopo 2.5 secondi.
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

        // Dopo 2.5 secondi totali, vai a LoginActivity
        handler.postDelayed({
            startActivity(Intent(this, LoginActivity::class.java))
            // Transizione fade-out / fade-in
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 2500)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
