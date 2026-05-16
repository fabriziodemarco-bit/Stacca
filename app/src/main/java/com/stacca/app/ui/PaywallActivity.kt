package com.stacca.app.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.stacca.app.R
import com.stacca.app.billing.BillingManager
import com.stacca.app.data.PreferencesManager

/**
 * Activity paywall che si mostra tra la notifica 3 (INSISTENT) e la 4 (AGGRESSIVE).
 * Richiede un acquisto one-time di €0.99 per sbloccare i livelli 4-6 (fullscreen).
 * Se l'utente non acquista, le notifiche si fermano al livello 3.
 */
class PaywallActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var billingManager: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_paywall)

        prefs = PreferencesManager(this)

        // Se è già premium, chiudi subito (non dovrebbe mai succedere)
        if (prefs.isPremium) {
            finish()
            return
        }

        billingManager = BillingManager(this) { success ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this,
                        getString(R.string.paywall_success),
                        Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    Toast.makeText(this,
                        getString(R.string.premium_error),
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
        billingManager.connect()

        setupUI()
        startAnimations()
    }

    private fun setupUI() {
        // Bottone acquisto
        findViewById<MaterialButton>(R.id.btnUnlock).setOnClickListener {
            billingManager.launchPurchaseFlow(this, isSubscription = false)
        }

        // Bottone "No grazie" - chiude e resta al livello 3
        findViewById<MaterialButton>(R.id.btnNoThanks).setOnClickListener {
            finish()
        }

        // Restore purchases
        findViewById<TextView>(R.id.tvPaywallRestore).setOnClickListener {
            billingManager.checkExistingPurchases()
            Toast.makeText(this, getString(R.string.premium_restoring),
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun startAnimations() {
        // Animazione emoji
        val tvEmoji = findViewById<TextView>(R.id.tvPaywallEmoji)
        val scaleX = ObjectAnimator.ofFloat(tvEmoji, View.SCALE_X, 0f, 1.2f, 1f)
        val scaleY = ObjectAnimator.ofFloat(tvEmoji, View.SCALE_Y, 0f, 1.2f, 1f)
        val emojiAnim = AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 800
            interpolator = OvershootInterpolator(2f)
        }
        emojiAnim.start()

        // Animazione pulsante del bottone acquisto
        val btnUnlock = findViewById<MaterialButton>(R.id.btnUnlock)
        val pulseX = ObjectAnimator.ofFloat(btnUnlock, View.SCALE_X, 1f, 1.05f, 1f)
        val pulseY = ObjectAnimator.ofFloat(btnUnlock, View.SCALE_Y, 1f, 1.05f, 1f)
        val pulseAnim = AnimatorSet().apply {
            playTogether(pulseX, pulseY)
            duration = 1200
            interpolator = AccelerateDecelerateInterpolator()
        }
        pulseAnim.start()

        // Animazione fade-in del titolo
        val tvTitle = findViewById<TextView>(R.id.tvPaywallTitle)
        tvTitle.alpha = 0f
        tvTitle.animate()
            .alpha(1f)
            .setDuration(600)
            .setStartDelay(300)
            .start()

        // Animazione fade-in del sottotitolo
        val tvSubtitle = findViewById<TextView>(R.id.tvPaywallSubtitle)
        tvSubtitle.alpha = 0f
        tvSubtitle.animate()
            .alpha(1f)
            .setDuration(600)
            .setStartDelay(500)
            .start()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::billingManager.isInitialized) {
            billingManager.destroy()
        }
    }
}
