package com.stacca.app.ui

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.stacca.app.R
import com.stacca.app.billing.BillingManager
import com.stacca.app.data.PreferencesManager
import com.stacca.app.util.SystemBarsHelper

/**
 * Paywall freemium: mostrata quando l'utente (senza accesso completo) vuole sbloccare
 * i livelli 4-6 (AGGRESSIVE, NUCLEAR, APOCALYPSE) e l'allarme a schermo intero.
 *
 * L'acquisto è one-time (stacca_premium). BillingManager e restore acquisti invariati.
 */
class PaywallActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var billingManager: BillingManager
    private lateinit var btnUnlock: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_paywall)
        window.setBackgroundDrawableResource(R.color.home_bg)
        SystemBarsHelper.applyInsets(this)

        prefs = PreferencesManager(this)

        // Se è già premium, chiudi subito
        if (prefs.isPremium) {
            finish()
            return
        }

        btnUnlock = findViewById(R.id.btnUnlock)
        // Disabilita finché il billing non è pronto
        btnUnlock.isEnabled = false
        btnUnlock.text = getString(R.string.store_connecting)

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

        billingManager.onProductsReady = {
            btnUnlock.isEnabled = true
            val price = billingManager.getPremiumPrice()
            btnUnlock.text = if (price != null) {
                getString(R.string.paywall_unlock_price_v2, price)
            } else {
                getString(R.string.paywall_unlock_v2)
            }
        }

        // Errore billing (es. app non configurata per fatturazione)
        billingManager.onBillingError = { errorMessage ->
            runOnUiThread {
                btnUnlock.isEnabled = false
                btnUnlock.text = getString(R.string.paywall_unlock_v2)
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            }
        }

        billingManager.connect()

        setupUI()

        // Schermi bassi: la riga "sostieni lo sviluppatore" si nasconde, così i pulsanti restano visibili
        if (resources.configuration.screenHeightDp < 720) {
            findViewById<TextView>(R.id.tvPaywallSupport).visibility = android.view.View.GONE
        }
    }

    private fun setupUI() {
        // Bottone acquisto
        btnUnlock.setOnClickListener {
            if (!billingManager.isReady()) {
                Toast.makeText(this, getString(R.string.store_connecting_toast),
                    Toast.LENGTH_SHORT).show()
                billingManager.connect()
                return@setOnClickListener
            }
            billingManager.launchPurchaseFlow(this)
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

    override fun onDestroy() {
        super.onDestroy()
        if (::billingManager.isInitialized) {
            billingManager.destroy()
        }
    }
}
