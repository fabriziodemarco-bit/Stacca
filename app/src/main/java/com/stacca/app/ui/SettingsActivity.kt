package com.stacca.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.stacca.app.R
import com.stacca.app.auth.AuthManager
import com.stacca.app.data.PreferencesManager
import kotlinx.coroutines.launch

/**
 * Activity per le impostazioni dell'app.
 * Permette di configurare intervalli di escalation, suoni, comportamenti,
 * e gestire account e premium.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = PreferencesManager(this)
        authManager = AuthManager(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        setupSoundSwitch()
        setupVibrationSwitch()
        setupFullScreenSwitch()
        setupAutoRestartSwitch()
        setupEscalationSpeed()
        setupAccountSection()
    }

    private fun setupSoundSwitch() {
        val switch = findViewById<MaterialSwitch>(R.id.switchSound)
        switch.isChecked = prefs.soundEnabled
        switch.setOnCheckedChangeListener { _, checked ->
            prefs.soundEnabled = checked
        }
    }

    private fun setupVibrationSwitch() {
        val switch = findViewById<MaterialSwitch>(R.id.switchVibration)
        switch.isChecked = prefs.vibrationEnabled
        switch.setOnCheckedChangeListener { _, checked ->
            prefs.vibrationEnabled = checked
        }
    }

    private fun setupFullScreenSwitch() {
        val switch = findViewById<MaterialSwitch>(R.id.switchFullScreen)
        switch.isChecked = prefs.fullScreenEnabled

        // Feature premium: se non è premium, mostra upsell
        if (!prefs.isPremium) {
            switch.isEnabled = false
            switch.isChecked = false
        }

        switch.setOnCheckedChangeListener { _, checked ->
            if (!prefs.isPremium) {
                switch.isChecked = false
                showPremiumUpsell()
                return@setOnCheckedChangeListener
            }
            prefs.fullScreenEnabled = checked
        }
    }

    private fun setupAutoRestartSwitch() {
        val switch = findViewById<MaterialSwitch>(R.id.switchAutoRestart)
        switch.isChecked = prefs.autoRestartEnabled
        switch.setOnCheckedChangeListener { _, checked ->
            prefs.autoRestartEnabled = checked
        }
    }

    private fun setupEscalationSpeed() {
        val slider = findViewById<Slider>(R.id.sliderEscalation)
        val speedLabels = resources.getStringArray(R.array.escalation_speeds)
        val tvSpeedLabel = findViewById<TextView>(R.id.tvSpeedLabel)

        // Feature premium: se non è premium, blocca
        if (!prefs.isPremium) {
            slider.isEnabled = false
            slider.value = 1f // Normale
            tvSpeedLabel.text = speedLabels[1]
        } else {
            slider.value = prefs.escalationSpeed.toFloat()
            tvSpeedLabel.text = speedLabels[prefs.escalationSpeed]
        }

        slider.addOnChangeListener { _, value, _ ->
            if (!prefs.isPremium) {
                slider.value = 1f
                showPremiumUpsell()
                return@addOnChangeListener
            }
            val index = value.toInt()
            prefs.escalationSpeed = index
            tvSpeedLabel.text = speedLabels[index]
        }
    }

    private fun setupAccountSection() {
        val tvAccountStatus = findViewById<TextView>(R.id.tvAccountStatus)
        val btnAccountAction = findViewById<MaterialButton>(R.id.btnAccountAction)
        val btnPremium = findViewById<MaterialButton>(R.id.btnPremium)

        if (prefs.isLoggedIn) {
            tvAccountStatus.text = getString(R.string.settings_logged_as, prefs.userEmail)

            if (prefs.isPremium) {
                btnPremium.text = getString(R.string.settings_premium_badge)
                btnPremium.isEnabled = false
            } else {
                btnPremium.text = getString(R.string.settings_upgrade)
                btnPremium.setOnClickListener {
                    startActivity(Intent(this, PremiumActivity::class.java))
                }
            }
            btnPremium.visibility = View.VISIBLE

            btnAccountAction.text = getString(R.string.settings_logout)
            btnAccountAction.setOnClickListener {
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.settings_logout))
                    .setMessage(getString(R.string.settings_logout_confirm))
                    .setPositiveButton(getString(R.string.settings_logout)) { _, _ ->
                        performLogout()
                    }
                    .setNegativeButton(getString(R.string.btn_cancel), null)
                    .show()
            }
        } else {
            tvAccountStatus.text = getString(R.string.settings_not_logged)
            btnPremium.visibility = View.GONE

            btnAccountAction.text = getString(R.string.settings_login)
            btnAccountAction.setOnClickListener {
                startActivity(Intent(this, LoginActivity::class.java))
            }
        }
    }

    private fun performLogout() {
        lifecycleScope.launch {
            authManager.signOut()
            Toast.makeText(this@SettingsActivity, "Logout effettuato",
                Toast.LENGTH_SHORT).show()
            startActivity(Intent(this@SettingsActivity, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }



    private fun showPremiumUpsell() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.premium_required))
            .setMessage(getString(R.string.premium_required_message))
            .setPositiveButton(getString(R.string.premium_unlock)) { _, _ ->
                startActivity(Intent(this, PremiumActivity::class.java))
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }
}
