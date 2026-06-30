package com.stacca.app.ui

import android.Manifest
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.stacca.app.R
import com.stacca.app.billing.BillingManager
import com.stacca.app.data.NotificationMessages
import com.stacca.app.data.PreferencesManager
import com.stacca.app.notifications.AlarmSoundManager
import com.stacca.app.notifications.NotificationHelper
import com.stacca.app.receivers.AlarmReceiver
import com.stacca.app.util.PermissionHelper
import java.text.SimpleDateFormat
import java.util.*


/**
 * Activity principale dell'app Stacca!
 * Mostra l'orario corrente, l'orario di fine turno impostato,
 * e permette di attivare/disattivare l'allarme.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var billingManager: BillingManager
    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val timeFormatSeconds = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // Views
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvEndTime: TextView
    private lateinit var tvStatus: TextView
    private lateinit var statusDot: View
    private lateinit var tvCountdown: TextView
    private lateinit var tvCountdownLabel: TextView
    private lateinit var cardCountdown: MaterialCardView
    private lateinit var cardEndTime: MaterialCardView
    private lateinit var cardTempoNonVissuto: MaterialCardView
    private lateinit var tvTempoNonVissuto: TextView
    private lateinit var btnActivate: MaterialButton
    private lateinit var btnDeactivate: MaterialButton
    private lateinit var btnSettings: MaterialButton
    private lateinit var btnAncoraUnaSveglia: MaterialButton

    // Card protezione permessi
    private lateinit var cardPermissions: MaterialCardView
    private lateinit var tvPermNotification: TextView
    private lateinit var tvPermExactAlarm: TextView
    private lateinit var tvPermBattery: TextView

    // Card banner trial (visibile solo ai non-premium)
    private lateinit var cardTrialBanner: MaterialCardView
    private lateinit var tvTrialBanner: TextView

    // Streak badge e bottone "Ho staccato!" (in cardCountdown)
    private lateinit var btnHoStaccato: com.google.android.material.button.MaterialButton

    // Receiver per il cambio di stato del permesso allarmi esatti (API 31+)
    private val exactAlarmPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
                // Aggiorna la card e, se l'allarme era attivo, riprogramma ora che abbiamo il permesso
                updatePermissionsCard()
                if (prefs.isAlarmActive && PermissionHelper.canScheduleExactAlarms(this@MainActivity)) {
                    AlarmReceiver.scheduleAlarm(this@MainActivity, prefs.endHour, prefs.endMinute)
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.alarm_rescheduled),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }


    // Permesso notifiche (flusso attivazione allarme → dopo OK attiva l'allarme)
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                activateAlarm()
            } else {
                Toast.makeText(this,
                    "Senza permesso notifiche l'app non può funzionare! 😢",
                    Toast.LENGTH_LONG).show()
            }
        }

    // Permesso notifiche (dalla card "Protezione allarmi" → aggiorna solo la UI, NON attiva l'allarme)
    private val notificationPermissionFromCardLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this,
                    "Senza permesso notifiche l'app non può funzionare! 😢",
                    Toast.LENGTH_LONG).show()
            }
            updatePermissionsCard()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PreferencesManager(this)
        notificationHelper = NotificationHelper(this)

        billingManager = BillingManager(this) { _ -> }
        billingManager.onPremiumRestored = { updateTrialBanner() }
        billingManager.connect()

        initViews()
        setupListeners()
        updateUI()
        startClockUpdate()
    }

    private fun initViews() {
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvEndTime = findViewById(R.id.tvEndTime)
        tvStatus = findViewById(R.id.tvStatus)
        statusDot = findViewById(R.id.statusDot)
        tvCountdown = findViewById(R.id.tvCountdown)
        tvCountdownLabel = findViewById(R.id.tvCountdownLabel)
        cardCountdown = findViewById(R.id.cardCountdown)
        cardEndTime = findViewById(R.id.cardEndTime)
        cardTempoNonVissuto = findViewById(R.id.cardTempoNonVissuto)
        tvTempoNonVissuto = findViewById(R.id.tvTempoNonVissuto)
        btnActivate = findViewById(R.id.btnActivate)
        btnDeactivate = findViewById(R.id.btnDeactivate)
        btnSettings = findViewById(R.id.btnSettings)
        btnAncoraUnaSveglia = findViewById(R.id.btnAncoraUnaSveglia)
        // Card protezione permessi
        cardPermissions = findViewById(R.id.cardPermissions)
        tvPermNotification = findViewById(R.id.tvPermNotification)
        tvPermExactAlarm = findViewById(R.id.tvPermExactAlarm)
        tvPermBattery = findViewById(R.id.tvPermBattery)
        // Card banner trial
        cardTrialBanner = findViewById(R.id.cardTrialBanner)
        tvTrialBanner = findViewById(R.id.tvTrialBanner)

        // Bottone "Ho staccato!"
        btnHoStaccato = findViewById(R.id.btnHoStaccato)
    }


    private fun setupListeners() {
        // Imposta orario di fine
        findViewById<MaterialButton>(R.id.btnSetTime).setOnClickListener {
            showTimePicker()
        }

        // Click sulla card dell'orario per aprire il picker
        cardEndTime.setOnClickListener {
            showTimePicker()
        }

        // Attiva allarme
        btnActivate.setOnClickListener {
            checkPermissionsAndActivate()
        }

        // Disattiva allarme
        btnDeactivate.setOnClickListener {
            deactivateAlarm()
        }

        // Impostazioni
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Bottone "Ho staccato!" in MainActivity
        btnHoStaccato.setOnClickListener {
            handleHoStaccato()
        }

        btnAncoraUnaSveglia.setOnClickListener {
            prefs.isWaitingForNextAlarm = false
            updateUI()
        }
    }

    private fun showTimePicker() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(prefs.endHour)
            .setMinute(prefs.endMinute)
            .setTitleText(getString(R.string.set_end_time))
            .build()

        picker.addOnPositiveButtonClickListener {
            prefs.endHour = picker.hour
            prefs.endMinute = picker.minute
            tvEndTime.text = String.format("%02d:%02d", picker.hour, picker.minute)

            // Se l'allarme è attivo, riprogrammalo
            if (prefs.isAlarmActive) {
                AlarmReceiver.cancelAlarm(this)
                AlarmReceiver.scheduleAlarm(this, picker.hour, picker.minute)
                Toast.makeText(this, "⏰ Allarme aggiornato!", Toast.LENGTH_SHORT).show()
            }
        }

        picker.show(supportFragmentManager, "timePicker")
    }

    private fun checkPermissionsAndActivate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.permission_notification_title))
                    .setMessage(getString(R.string.permission_notification_message))
                    .setPositiveButton("OK") { _, _ ->
                        notificationPermissionLauncher.launch(
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    }
                    .setNegativeButton(getString(R.string.btn_cancel), null)
                    .show()
                return
            }
        }
        // Controlla il permesso allarmi esatti (API 31+)
        // Se mancante, mostra un dialogo esplicativo in tono coerente con l'app
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !PermissionHelper.canScheduleExactAlarms(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.permission_exact_alarm_title))
                .setMessage(getString(R.string.permission_exact_alarm_message))
                .setPositiveButton(getString(R.string.permission_exact_alarm_btn)) { _, _ ->
                    PermissionHelper.exactAlarmSettingsIntent(this)?.let { startActivity(it) }
                }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
            return
        }
        activateAlarm()
    }


    private fun activateAlarm() {
        prefs.isAlarmActive = true
        prefs.resetEscalation()
        AlarmReceiver.scheduleAlarm(this, prefs.endHour, prefs.endMinute)
        updateUI()
        Toast.makeText(this,
            "⚡ Allarme attivato per le ${String.format("%02d:%02d", prefs.endHour, prefs.endMinute)}!",
            Toast.LENGTH_SHORT).show()
    }

    private fun deactivateAlarm() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Disattivare l'allarme?")
            .setMessage("Sei sicuro? Senza allarme potresti lavorare per sempre! 😱")
            .setPositiveButton("Sì, disattiva") { _, _ ->
                prefs.isAlarmActive = false
                prefs.resetEscalation()
                AlarmSoundManager.stop()
                AlarmReceiver.cancelAlarm(this)
                notificationHelper.cancelAll()
                // Se c'è straordinario, lo registriamo come staccato
                val now = java.util.Calendar.getInstance()
                val endTime = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, prefs.endHour)
                    set(java.util.Calendar.MINUTE, prefs.endMinute)
                    set(java.util.Calendar.SECOND, 0)
                }
                val overtimeMillis = now.timeInMillis - endTime.timeInMillis
                if (overtimeMillis > 0) {
                    val overtimeMinutes = (overtimeMillis / 60_000).toInt()
                    prefs.registraStaccato(overtimeMinutes)
                }

                updateUI()
                Toast.makeText(this, "Allarme disattivato 😴", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("No, tienilo attivo", null)
            .show()
    }

    private fun updateUI() {
        if (prefs.isWaitingForNextAlarm) {
            val overtime = prefs.lastShiftOvertimeMinutes
            cardTempoNonVissuto.visibility = View.VISIBLE
            tvTempoNonVissuto.text = "$overtime min"
            val colorRes = if (overtime > 0) R.color.alert_apocalypse else R.color.alert_friendly
            cardTempoNonVissuto.strokeColor = ContextCompat.getColor(this, colorRes)
            tvTempoNonVissuto.setTextColor(ContextCompat.getColor(this, colorRes))

            cardEndTime.visibility = View.GONE
            cardCountdown.visibility = View.GONE
            btnActivate.visibility = View.GONE
            btnDeactivate.visibility = View.GONE
            btnSettings.visibility = View.VISIBLE
            btnAncoraUnaSveglia.visibility = View.VISIBLE
            
            tvStatus.text = "Stacco registrato!"
            statusDot.setBackgroundResource(R.drawable.status_dot_inactive)
            btnHoStaccato.visibility = View.GONE
            updateTrialBanner()
            return
        }

        val isActive = prefs.isAlarmActive

        if (!isActive && prefs.shiftsCompletedToday > 0) {
            val overtime = prefs.lastShiftOvertimeMinutes
            cardTempoNonVissuto.visibility = View.VISIBLE
            tvTempoNonVissuto.text = "$overtime min"
            val colorRes = if (overtime > 0) R.color.alert_apocalypse else R.color.alert_friendly
            cardTempoNonVissuto.strokeColor = ContextCompat.getColor(this, colorRes)
            tvTempoNonVissuto.setTextColor(ContextCompat.getColor(this, colorRes))
        } else {
            cardTempoNonVissuto.visibility = View.GONE
        }

        cardEndTime.visibility = View.VISIBLE
        btnSettings.visibility = View.VISIBLE
        btnAncoraUnaSveglia.visibility = View.GONE

        tvEndTime.text = String.format("%02d:%02d", prefs.endHour, prefs.endMinute)

        if (isActive) {
            tvStatus.text = getString(R.string.alarm_active)
            statusDot.setBackgroundResource(R.drawable.status_dot_active)
            btnActivate.visibility = View.GONE
            btnDeactivate.visibility = View.VISIBLE
            cardCountdown.visibility = View.VISIBLE
        } else {
            tvStatus.text = getString(R.string.alarm_inactive)
            statusDot.setBackgroundResource(R.drawable.status_dot_inactive)
            btnActivate.visibility = View.VISIBLE
            btnDeactivate.visibility = View.GONE
            cardCountdown.visibility = View.GONE
        }
        updateTrialBanner()
    }

    private fun startClockUpdate() {
        handler.post(object : Runnable {
            override fun run() {
                updateClock()
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun updateClock() {
        val now = Calendar.getInstance()
        tvCurrentTime.text = timeFormatSeconds.format(now.time)

        if (prefs.isAlarmActive) {
            val endTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, prefs.endHour)
                set(Calendar.MINUTE, prefs.endMinute)
                set(Calendar.SECOND, 0)
            }

            val diffMillis = endTime.timeInMillis - now.timeInMillis

            if (diffMillis > 0) {
                // Countdown — nasconde bottone e badge quando non siamo in overtime
                cardEndTime.visibility = View.VISIBLE
                val hours = diffMillis / 3600000
                val minutes = (diffMillis % 3600000) / 60000
                val seconds = (diffMillis % 60000) / 1000
                tvCountdown.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                tvCountdownLabel.text = getString(R.string.time_remaining)
                tvCountdown.setTextColor(ContextCompat.getColor(this, R.color.tertiary))
                cardCountdown.strokeColor = ContextCompat.getColor(this, android.R.color.transparent)
                btnHoStaccato.visibility = View.GONE
            } else {
                // Straordinario!
                val overtimeMillis = -diffMillis
                val hours = overtimeMillis / 3600000
                val minutes = (overtimeMillis % 3600000) / 60000
                val seconds = (overtimeMillis % 60000) / 1000
                tvCountdown.text = String.format("+%02d:%02d:%02d", hours, minutes, seconds)
                tvCountdownLabel.text = getString(R.string.tempo_non_vissuto_title).uppercase()
                cardEndTime.visibility = View.GONE

                val overtimeMinutes = (overtimeMillis / 60000).toInt()
                val level = NotificationMessages.getLevelForMinutes(overtimeMinutes)

                // Colore basato sul livello (rimosso fiamme, usiamo alert_apocalypse se overtime > 0)
                val color = R.color.alert_apocalypse
                tvCountdown.setTextColor(ContextCompat.getColor(this, color))
                tvCountdownLabel.setTextColor(ContextCompat.getColor(this, color))
                cardCountdown.strokeColor = ContextCompat.getColor(this, R.color.alert_apocalypse)

                btnHoStaccato.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Gestisce il tap sul bottone "Ho staccato!" in MainActivity.
     * Calcola l'overtime corrente, chiama registraStaccato, cancella allarmi e
     * apre la schermata appropriata (Celebration o TempoNonVissuto).
     */
    private fun handleHoStaccato() {
        val now = Calendar.getInstance()
        val endTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, prefs.endHour)
            set(Calendar.MINUTE, prefs.endMinute)
            set(Calendar.SECOND, 0)
        }
        val overtimeMillis = (now.timeInMillis - endTime.timeInMillis).coerceAtLeast(0L)
        val overtimeMinutes = (overtimeMillis / 60_000).toInt()

        // Legge lo streak PRIMA di registraStaccato (che lo azzera in caso di ritardo)
        val streakBeforeReset = prefs.streakCount

        // Registra (idempotente)
        val result = prefs.registraStaccato(overtimeMinutes)

        // Cancella allarmi e notifiche
        prefs.paywallShownToday = false
        prefs.resetEscalation()
        AlarmSoundManager.stop()
        AlarmReceiver.cancelAlarm(this)
        notificationHelper.cancelAll()

        // Disattiva definitivamente l'allarme
        prefs.isAlarmActive = false

        // Aggiorna UI
        updateUI()
        updateClock()
    }

    /**
     * Mostra un dialog con le statistiche streak dell'utente.
     */




    override fun onResume() {
        super.onResume()
        updateUI()

        // (Rimosso: safety net del login obbligatorio - ora il login è opzionale)

        // Aggiorna la card dei permessi: l'utente potrebbe tornare dalle impostazioni di sistema
        updatePermissionsCard()
        updateTrialBanner()

        // Registra il receiver per i cambiamenti del permesso allarmi esatti (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            registerReceiver(
                exactAlarmPermissionReceiver,
                IntentFilter(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
            )
        }
    }

    override fun onPause() {
        super.onPause()
        // Deregistra il receiver degli allarmi esatti per evitare memory leak
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                unregisterReceiver(exactAlarmPermissionReceiver)
            } catch (e: IllegalArgumentException) {
                // Già deregistrato, nessun problema
            }
        }
    }

    /**
     * Aggiorna la card "Protezione allarmi" in base allo stato corrente dei permessi.
     * La card è visibile solo se almeno uno dei tre check fallisce.
     * Le righe con ⚠️ sono cliccabili per aprire la schermata di sistema corrispondente.
     */
    private fun updatePermissionsCard() {
        val hasNotif = PermissionHelper.hasNotificationPermission(this)
        val hasExact = PermissionHelper.canScheduleExactAlarms(this)
        val hasBattery = PermissionHelper.isIgnoringBatteryOptimizations(this)

        // Nascondi la card se tutto è a posto
        if (hasNotif && hasExact && hasBattery) {
            cardPermissions.visibility = View.GONE
            return
        }
        cardPermissions.visibility = View.VISIBLE

        // Riga notifiche
        if (hasNotif) {
            tvPermNotification.text = getString(R.string.perm_status_ok_notification)
            tvPermNotification.setOnClickListener(null)
            tvPermNotification.isClickable = false
        } else {
            tvPermNotification.text = getString(R.string.perm_status_warn_notification)
            tvPermNotification.isClickable = true
            tvPermNotification.setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionFromCardLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        // Riga allarmi esatti
        if (hasExact) {
            tvPermExactAlarm.text = getString(R.string.perm_status_ok_exact_alarm)
            tvPermExactAlarm.setOnClickListener(null)
            tvPermExactAlarm.isClickable = false
        } else {
            tvPermExactAlarm.text = getString(R.string.perm_status_warn_exact_alarm)
            tvPermExactAlarm.isClickable = true
            tvPermExactAlarm.setOnClickListener {
                PermissionHelper.exactAlarmSettingsIntent(this)?.let { startActivity(it) }
            }
        }

        // Riga ottimizzazione batteria
        if (hasBattery) {
            tvPermBattery.text = getString(R.string.perm_status_ok_battery)
            tvPermBattery.setOnClickListener(null)
            tvPermBattery.isClickable = false
        } else {
            tvPermBattery.text = getString(R.string.perm_status_warn_battery)
            tvPermBattery.isClickable = true
            tvPermBattery.setOnClickListener {
                startActivity(PermissionHelper.batteryOptimizationIntent(this))
            }
        }
    }

    /**
     * Aggiorna il banner del trial/escalation nella schermata principale.
     *
     * - Accesso completo (premium o trial attivo): "Escalation completa: 6 livelli 💀" — non cliccabile.
     * - Piano gratuito: "Escalation attiva: 3 di 6 livelli 🔒" — cliccabile → PaywallActivity.
     */
    private fun updateTrialBanner() {
        if (prefs.isPremium) {
            cardTrialBanner.visibility = View.VISIBLE
            tvTrialBanner.text = getString(R.string.premium_active)
        } else {
            cardTrialBanner.visibility = View.VISIBLE
            tvTrialBanner.text = getString(R.string.settings_upgrade)
        }

        if (!prefs.isPremium) {
            cardTrialBanner.isClickable = true
            cardTrialBanner.setOnClickListener {
                startActivity(Intent(this, PremiumActivity::class.java))
            }
        } else {
            cardTrialBanner.isClickable = false
            cardTrialBanner.setOnClickListener(null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        billingManager.destroy()
    }
}
