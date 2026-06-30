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
    private lateinit var btnBuy: MaterialButton

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

        btnBuy = findViewById(R.id.btnBuyPremium)
        // Disabilita il bottone finché il billing non è pronto
        btnBuy.isEnabled = false
        btnBuy.text = getString(R.string.store_connecting)

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

        billingManager.onProductsReady = {
            btnBuy.isEnabled = true
            val price = billingManager.getPremiumPrice()
            btnBuy.text = if (price != null) {
                getString(R.string.premium_buy_price, price)
            } else {
                getString(R.string.premium_buy_button)
            }
        }

        // Errore billing (es. app non configurata per fatturazione)
        billingManager.onBillingError = { errorMessage ->
            runOnUiThread {
                btnBuy.isEnabled = false
                btnBuy.text = getString(R.string.premium_buy_button)
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            }
        }

        billingManager.connect()
        setupButtons()
    }

    private fun setupButtons() {
        // Acquisto one-time — non richiede login Supabase
        btnBuy.setOnClickListener {
            if (!billingManager.isReady()) {
                Toast.makeText(this, getString(R.string.store_connecting_toast),
                    Toast.LENGTH_SHORT).show()
                billingManager.connect()
                return@setOnClickListener
            }
            billingManager.launchPurchaseFlow(this)
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
