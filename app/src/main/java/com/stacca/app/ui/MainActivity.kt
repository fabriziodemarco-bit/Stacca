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
    private lateinit var btnActivate: MaterialButton
    private lateinit var btnDeactivate: MaterialButton
    private lateinit var layoutEscalation: View
    private lateinit var levelDots: List<View>

    // Card protezione permessi
    private lateinit var cardPermissions: MaterialCardView
    private lateinit var tvPermNotification: TextView
    private lateinit var tvPermExactAlarm: TextView
    private lateinit var tvPermBattery: TextView

    // Card banner trial (visibile solo ai non-premium)
    private lateinit var cardTrialBanner: MaterialCardView
    private lateinit var tvTrialBanner: TextView

    // Streak badge e bottone "Ho staccato!" (in cardCountdown)
    private lateinit var tvStreakBadge: TextView
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


    // Permesso notifiche
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PreferencesManager(this)
        notificationHelper = NotificationHelper(this)

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
        btnActivate = findViewById(R.id.btnActivate)
        btnDeactivate = findViewById(R.id.btnDeactivate)
        layoutEscalation = findViewById(R.id.layoutEscalation)
        levelDots = listOf(
            findViewById(R.id.level1Dot),
            findViewById(R.id.level2Dot),
            findViewById(R.id.level3Dot),
            findViewById(R.id.level4Dot),
            findViewById(R.id.level5Dot),
            findViewById(R.id.level6Dot)
        )
        // Card protezione permessi
        cardPermissions = findViewById(R.id.cardPermissions)
        tvPermNotification = findViewById(R.id.tvPermNotification)
        tvPermExactAlarm = findViewById(R.id.tvPermExactAlarm)
        tvPermBattery = findViewById(R.id.tvPermBattery)
        // Card banner trial
        cardTrialBanner = findViewById(R.id.cardTrialBanner)
        tvTrialBanner = findViewById(R.id.tvTrialBanner)

        // Streak badge e bottone "Ho staccato!"
        tvStreakBadge = findViewById(R.id.tvStreakBadge)
        btnHoStaccato = findViewById(R.id.btnHoStaccato)
    }


    private fun setupListeners() {
        // Imposta orario di fine
        findViewById<MaterialButton>(R.id.btnSetTime).setOnClickListener {
            showTimePicker()
        }

        // Click sulla card dell'orario per aprire il picker
        findViewById<MaterialCardView>(R.id.cardEndTime).setOnClickListener {
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
        findViewById<MaterialButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Bottone "Ho staccato!" in MainActivity
        btnHoStaccato.setOnClickListener {
            handleHoStaccato()
        }

        // Tap sul badge streak → mostra dialog con stats
        tvStreakBadge.setOnClickListener {
            showStreakDialog()
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
                AlarmSoundManager.stop()
                AlarmReceiver.cancelAlarm(this)
                notificationHelper.cancelAll()
                updateUI()
                Toast.makeText(this, "Allarme disattivato 😴", Toast.LENGTH_SHORT).show()

                // Se c'è straordinario, mostra il "Tempo Non Vissuto"
                val now = java.util.Calendar.getInstance()
                val endTime = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, prefs.endHour)
                    set(java.util.Calendar.MINUTE, prefs.endMinute)
                    set(java.util.Calendar.SECOND, 0)
                }
                val overtimeMillis = now.timeInMillis - endTime.timeInMillis
                if (overtimeMillis > 60_000) { // almeno 1 minuto di straordinario
                    val overtimeMinutes = (overtimeMillis / 60_000).toInt()
                    startActivity(
                        Intent(this, TempoNonVissutoActivity::class.java).apply {
                            putExtra(TempoNonVissutoActivity.EXTRA_OVERTIME_MINUTES, overtimeMinutes)
                        }
                    )
                }
            }
            .setNegativeButton("No, tienilo attivo", null)
            .show()
    }

    private fun updateUI() {
        val isActive = prefs.isAlarmActive
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
                val hours = diffMillis / 3600000
                val minutes = (diffMillis % 3600000) / 60000
                val seconds = (diffMillis % 60000) / 1000
                tvCountdown.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                tvCountdownLabel.text = getString(R.string.time_remaining)
                tvCountdown.setTextColor(ContextCompat.getColor(this, R.color.tertiary))
                layoutEscalation.visibility = View.GONE
                btnHoStaccato.visibility = View.GONE
            } else {
                // Straordinario!
                val overtimeMillis = -diffMillis
                val hours = overtimeMillis / 3600000
                val minutes = (overtimeMillis % 3600000) / 60000
                val seconds = (overtimeMillis % 60000) / 1000
                tvCountdown.text = String.format("+%02d:%02d:%02d", hours, minutes, seconds)
                tvCountdownLabel.text = getString(R.string.overtime)

                val overtimeMinutes = (overtimeMillis / 60000).toInt()
                val level = NotificationMessages.getLevelForMinutes(overtimeMinutes)

                // Colore basato sul livello
                val color = when (level) {
                    NotificationMessages.Level.GENTLE -> R.color.alert_gentle
                    NotificationMessages.Level.FRIENDLY -> R.color.alert_friendly
                    NotificationMessages.Level.INSISTENT -> R.color.alert_insistent
                    NotificationMessages.Level.AGGRESSIVE -> R.color.alert_aggressive
                    NotificationMessages.Level.NUCLEAR -> R.color.alert_nuclear
                    NotificationMessages.Level.APOCALYPSE -> R.color.alert_apocalypse
                }
                tvCountdown.setTextColor(ContextCompat.getColor(this, color))
                tvCountdownLabel.setTextColor(ContextCompat.getColor(this, color))

                // Mostra indicatori di escalation
                layoutEscalation.visibility = View.VISIBLE
                updateEscalationDots(level)

                // Mostra il bottone "Ho staccato!" solo se non ancora staccato oggi
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(java.util.Calendar.getInstance().time)
                val alreadyStaccato = prefs.lastStaccatoDate == today
                btnHoStaccato.visibility = if (alreadyStaccato) View.GONE else View.VISIBLE
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
        AlarmSoundManager.stop()
        AlarmReceiver.cancelAlarm(this)
        notificationHelper.cancelAll()

        // Riprogramma per domani se l'allarme era attivo
        if (prefs.isAlarmActive) {
            AlarmReceiver.scheduleAlarm(this, prefs.endHour, prefs.endMinute)
        }

        // Aggiorna badge streak immediatamente
        updateStreakBadge()
        // Nascondi il bottone (già staccato oggi)
        btnHoStaccato.visibility = View.GONE

        if (result.isOnTime) {
            startActivity(Intent(this, CelebrationActivity::class.java).apply {
                putExtra(CelebrationActivity.EXTRA_STREAK_COUNT, result.streakCount)
                putExtra(CelebrationActivity.EXTRA_BEST_STREAK, result.bestStreak)
                putExtra(CelebrationActivity.EXTRA_IS_NEW_RECORD, result.isNewRecord)
            })
        } else {
            if (overtimeMinutes > 0) {
                prefs.hasPendingTempoNonVissuto = true
                prefs.pendingTempoNonVissutoMinutes = overtimeMinutes
            }
            startActivity(Intent(this, TempoNonVissutoActivity::class.java).apply {
                putExtra(TempoNonVissutoActivity.EXTRA_OVERTIME_MINUTES, overtimeMinutes)
                putExtra(TempoNonVissutoActivity.EXTRA_LOST_STREAK, streakBeforeReset)
            })
        }
    }

    /**
     * Aggiorna il badge 🔥 con lo streak corrente.
     * Il badge è visibile solo se streak > 0 e l'allarme è attivo.
     */
    private fun updateStreakBadge() {
        val streak = prefs.streakCount
        if (streak > 0 && prefs.isAlarmActive) {
            tvStreakBadge.text = getString(R.string.streak_badge_label, streak)
            tvStreakBadge.visibility = View.VISIBLE
        } else {
            tvStreakBadge.visibility = View.GONE
        }
    }

    /**
     * Mostra un dialog con le statistiche streak dell'utente.
     */
    private fun showStreakDialog() {
        val streak = prefs.streakCount
        val best = prefs.bestStreak
        val total = prefs.totalOnTimeDays
        val message = getString(R.string.streak_dialog_message, streak, best, total)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.streak_dialog_title))
            .setMessage(message)
            .setPositiveButton("Top! 💪", null)
            .show()
    }

    private fun updateEscalationDots(currentLevel: NotificationMessages.Level) {
        val levels = NotificationMessages.Level.entries
        for (i in levelDots.indices) {
            if (i < levels.size && i <= currentLevel.ordinal) {
                levelDots[i].setBackgroundResource(R.drawable.status_dot_active)
            } else {
                levelDots[i].setBackgroundResource(R.drawable.status_dot_inactive)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()

        // (Rimosso: safety net del login obbligatorio - ora il login è opzionale)

        // Aggiorna la card dei permessi: l'utente potrebbe tornare dalle impostazioni di sistema
        updatePermissionsCard()
        updateTrialBanner()
        updateStreakBadge()

        // Registra il receiver per i cambiamenti del permesso allarmi esatti (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            registerReceiver(
                exactAlarmPermissionReceiver,
                IntentFilter(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
            )
        }

        // Mostra TempoNonVissuto se c'era un pending (es. da notifica senza activity in foreground)
        if (prefs.hasPendingTempoNonVissuto && prefs.pendingTempoNonVissutoMinutes > 0) {
            startActivity(Intent(this, TempoNonVissutoActivity::class.java))
            // La activity stessa resetta il pending
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
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
        if (prefs.hasFullAccess) {
            // Accesso completo: mostra badge non cliccabile
            cardTrialBanner.visibility = View.VISIBLE
            tvTrialBanner.text = getString(R.string.escalation_full_badge)
            cardTrialBanner.isClickable = false
            cardTrialBanner.setOnClickListener(null)
        } else {
            // Piano gratuito: mostra badge cliccabile verso il paywall
            cardTrialBanner.visibility = View.VISIBLE
            tvTrialBanner.text = getString(R.string.escalation_limited_badge)
            cardTrialBanner.isClickable = true
            cardTrialBanner.setOnClickListener {
                startActivity(Intent(this, PaywallActivity::class.java))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
