package com.stacca.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.stacca.app.R
import com.stacca.app.billing.BillingManager
import com.stacca.app.data.PreferencesManager

/**
 * Activity per la pagina di upsell premium.
 * Mostra le funzionalità premium e permette l'acquisto.
 */
class PremiumActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var billingManager: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_premium)

        prefs = PreferencesManager(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        // Se è già premium, mostra messaggio e chiudi
        if (prefs.isPremium) {
            Toast.makeText(this, getString(R.string.premium_already),
                Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        billingManager = BillingManager(this) { success ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, getString(R.string.premium_success),
                        Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    Toast.makeText(this, getString(R.string.premium_error),
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
        billingManager.connect()

        setupButtons()
    }

    private fun setupButtons() {
        // Acquisto one-time
        findViewById<MaterialButton>(R.id.btnBuyPremium).setOnClickListener {
            if (!prefs.isLoggedIn) {
                Toast.makeText(this, getString(R.string.premium_login_required),
                    Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            billingManager.launchPurchaseFlow(this, isSubscription = false)
        }

        // Subscription
        findViewById<MaterialButton>(R.id.btnSubscribe).setOnClickListener {
            if (!prefs.isLoggedIn) {
                Toast.makeText(this, getString(R.string.premium_login_required),
                    Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            billingManager.launchPurchaseFlow(this, isSubscription = true)
        }

        // Restore purchases
        findViewById<View>(R.id.tvRestore).setOnClickListener {
            billingManager.checkExistingPurchases()
            Toast.makeText(this, getString(R.string.premium_restoring),
                Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::billingManager.isInitialized) {
            billingManager.destroy()
        }
    }
}
