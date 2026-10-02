package com.stacca.app.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
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
import com.stacca.app.util.SystemBarsHelper


/**
 * Activity principale dell'app Stacca!
 * Un riquadro protagonista che cambia con il momento (spento, countdown,
 * straordinario, stacco), il pulsante Insultami e le azioni secondarie.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        /** Extra usato dal pulsante "Ho staccato" di notifiche e schermo rosso. */
        const val EXTRA_HO_STACCATO = "extra_ho_staccato"
    }

    private lateinit var prefs: PreferencesManager
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var billingManager: BillingManager
    private val handler = Handler(Looper.getMainLooper())

    // Riquadro protagonista: cambia in base al momento (vedi updateUI)
    private lateinit var cardHero: MaterialCardView
    private lateinit var tvHeroLabel: TextView
    private lateinit var tvHeroValue: TextView
    private lateinit var tvHeroSub: TextView
    private lateinit var btnHeroPrimary: MaterialButton
    private lateinit var btnHeroSecondary: MaterialButton

    private lateinit var btnInsultami: MaterialButton
    private lateinit var btnDeactivate: MaterialButton
    private lateinit var btnSettings: MaterialButton
    private lateinit var tvPremiumBadge: TextView

    // Card protezione permessi
    private lateinit var cardPermissions: MaterialCardView
    private lateinit var tvPermNotification: TextView
    private lateinit var tvPermExactAlarm: TextView
    private lateinit var tvPermBattery: TextView

    /** I possibili momenti mostrati dal riquadro protagonista. */
    private enum class HeroState { SPENTO, COUNTDOWN, STRAORDINARIO, STACCATO }

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
        SystemBarsHelper.applyInsets(this)

        prefs = PreferencesManager(this)
        notificationHelper = NotificationHelper(this)

        billingManager = BillingManager(this) { _ -> }
        billingManager.onPremiumRestored = { updateTrialBanner() }
        billingManager.connect()

        initViews()
        setupListeners()
        updateUI()
        startClockUpdate()
        startInsultamiPulse()
        handleHoStaccatoIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleHoStaccatoIntent(intent)
    }

    /** Se l'app è stata aperta dal pulsante "Ho staccato", registra lo stacco. */
    private fun handleHoStaccatoIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_HO_STACCATO, false) != true) return
        intent.removeExtra(EXTRA_HO_STACCATO)
        if (prefs.isAlarmActive) {
            handleHoStaccato()
        }
    }

    private fun initViews() {
        cardHero = findViewById(R.id.cardHero)
        tvHeroLabel = findViewById(R.id.tvHeroLabel)
        tvHeroValue = findViewById(R.id.tvHeroValue)
        tvHeroSub = findViewById(R.id.tvHeroSub)
        btnHeroPrimary = findViewById(R.id.btnHeroPrimary)
        btnHeroSecondary = findViewById(R.id.btnHeroSecondary)
        btnInsultami = findViewById(R.id.btnInsultami)
        btnDeactivate = findViewById(R.id.btnDeactivate)
        btnSettings = findViewById(R.id.btnSettings)
        tvPremiumBadge = findViewById(R.id.tvPremiumBadge)
        // Card protezione permessi
        cardPermissions = findViewById(R.id.cardPermissions)
        tvPermNotification = findViewById(R.id.tvPermNotification)
        tvPermExactAlarm = findViewById(R.id.tvPermExactAlarm)
        tvPermBattery = findViewById(R.id.tvPermBattery)
    }

    private fun setupListeners() {
        // Il pulsante principale cambia funzione in base al momento
        btnHeroPrimary.setOnClickListener {
            when (currentHeroState()) {
                HeroState.SPENTO -> checkPermissionsAndActivate()
                HeroState.STRAORDINARIO -> handleHoStaccato()
                HeroState.STACCATO -> {
                    prefs.isWaitingForNextAlarm = false
                    updateUI()
                }
                HeroState.COUNTDOWN -> Unit
            }
        }
        btnHeroSecondary.setOnClickListener { handleSnooze() }

        // Toccando l'orario (o la riga sotto) si cambia il fine turno
        tvHeroValue.setOnClickListener {
            if (currentHeroState() == HeroState.SPENTO) showTimePicker()
        }
        tvHeroSub.setOnClickListener {
            val state = currentHeroState()
            if (state == HeroState.SPENTO || state == HeroState.COUNTDOWN) showTimePicker()
        }

        btnInsultami.setOnClickListener {
            startActivity(Intent(this, InsultamiActivity::class.java))
        }
        btnDeactivate.setOnClickListener { deactivateAlarm() }
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    /** Insultami "respira": pulsa piano per attirare l'occhio. */
    private fun startInsultamiPulse() {
        listOf(View.SCALE_X, View.SCALE_Y).forEach { property ->
            ObjectAnimator.ofFloat(btnInsultami, property, 1f, 1.06f).apply {
                duration = 900
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                start()
            }
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

            // Se l'allarme è attivo, riprogrammalo
            if (prefs.isAlarmActive) {
                AlarmReceiver.cancelAlarm(this)
                AlarmReceiver.scheduleAlarm(this, picker.hour, picker.minute)
                Toast.makeText(this, "⏰ Allarme aggiornato!", Toast.LENGTH_SHORT).show()
            }
            updateUI()
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

    /** In quale momento siamo: decide cosa mostra il riquadro protagonista. */
    private fun currentHeroState(): HeroState {
        if (prefs.isWaitingForNextAlarm) return HeroState.STACCATO
        if (!prefs.isAlarmActive) return HeroState.SPENTO
        return if (System.currentTimeMillis() < currentShiftEndMillis()) {
            HeroState.COUNTDOWN
        } else {
            HeroState.STRAORDINARIO
        }
    }

    /**
     * Ridisegna la home. Chiamata ogni secondo dall'orologio, quindi
     * tiene aggiornati anche countdown e straordinario.
     */
    private fun updateUI() {
        val state = currentHeroState()
        val endTimeText = String.format("%02d:%02d", prefs.endHour, prefs.endMinute)
        val now = System.currentTimeMillis()

        // Valori di base, sovrascritti caso per caso
        var accent = R.color.primary
        btnHeroPrimary.visibility = View.GONE
        btnHeroSecondary.visibility = View.GONE
        btnDeactivate.visibility = if (prefs.isAlarmActive) View.VISIBLE else View.GONE

        when (state) {
            HeroState.SPENTO -> {
                tvHeroLabel.setText(R.string.end_time_label)
                tvHeroValue.text = endTimeText
                tvHeroSub.setText(R.string.hero_tap_to_change)
                btnHeroPrimary.setText(R.string.activate_alarm)
                btnHeroPrimary.visibility = View.VISIBLE
            }

            HeroState.COUNTDOWN -> {
                val endMillis = currentShiftEndMillis()
                val isToday = isSameDay(endMillis, now)
                tvHeroLabel.text = if (isToday) {
                    getString(R.string.hero_countdown_label)
                } else {
                    getString(R.string.next_shift_tomorrow).uppercase()
                }
                tvHeroValue.text = formatDuration(endMillis - now, withPlus = false)
                tvHeroSub.text = getString(R.string.hero_end_time_sub, endTimeText)
            }

            HeroState.STRAORDINARIO -> {
                accent = R.color.alert_apocalypse
                tvHeroLabel.setText(R.string.hero_overtime_label)
                tvHeroValue.text = formatDuration(now - currentShiftEndMillis(), withPlus = true)
                tvHeroSub.setText(R.string.hero_overtime_sub)
                btnHeroPrimary.setText(R.string.btn_ho_staccato)
                btnHeroPrimary.visibility = View.VISIBLE
                btnHeroSecondary.text = getString(
                    R.string.btn_snooze, AlarmReceiver.getIntervalMinutes(prefs.escalationSpeed)
                )
                btnHeroSecondary.visibility = View.VISIBLE
            }

            HeroState.STACCATO -> {
                // Il momento premio: si celebra lo stacco (con un pizzico di sfottò se in ritardo)
                val overtime = prefs.lastShiftOvertimeMinutes
                val onTime = overtime == 0
                accent = if (onTime) R.color.alert_gentle else R.color.primary
                tvHeroLabel.setText(
                    if (onTime) R.string.hero_free_label_ontime else R.string.hero_free_label_late
                )
                tvHeroValue.text = "$overtime min"
                val firstLine = if (onTime) {
                    getString(R.string.hero_free_sub_ontime)
                } else {
                    getString(R.string.hero_free_sub_late, overtime)
                }
                tvHeroSub.text = if (prefs.isAlarmActive) {
                    firstLine + "\n" + getString(R.string.hero_next_tomorrow, endTimeText)
                } else {
                    firstLine
                }
                btnHeroPrimary.setText(R.string.btn_ok_home)
                btnHeroPrimary.visibility = View.VISIBLE
                // Subito dopo lo stacco "Disattiva" non serve
                btnDeactivate.visibility = View.GONE
            }
        }

        val accentColor = ContextCompat.getColor(this, accent)
        cardHero.strokeColor = accentColor
        tvHeroLabel.setTextColor(accentColor)
        tvHeroValue.setTextColor(accentColor)
        btnHeroPrimary.backgroundTintList = android.content.res.ColorStateList.valueOf(accentColor)
    }

    /** Durata in formato 00:00:00, con "+" davanti per lo straordinario. */
    private fun formatDuration(millis: Long, withPlus: Boolean): String {
        val safe = millis.coerceAtLeast(0L)
        val hours = safe / 3600000
        val minutes = (safe % 3600000) / 60000
        val seconds = (safe % 60000) / 1000
        val text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
        return if (withPlus) "+$text" else text
    }

    private fun isSameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    private fun startClockUpdate() {
        handler.post(object : Runnable {
            override fun run() {
                updateUI()
                handler.postDelayed(this, 1000)
            }
        })
    }

    /**
     * Orario del fine turno in corso o del prossimo (oggi o domani).
     * Se non è ancora stato salvato (versioni precedenti), usa l'orario di oggi.
     */
    private fun currentShiftEndMillis(): Long {
        if (prefs.nextShiftEndMillis > 0) return prefs.nextShiftEndMillis
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, prefs.endHour)
            set(Calendar.MINUTE, prefs.endMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /**
     * "Ancora X minuti" dall'app: zittisce l'allarme, l'escalation continua
     * (la prossima notifica è già programmata e arriverà al livello successivo).
     */
    private fun handleSnooze() {
        AlarmSoundManager.stop()
        notificationHelper.cancelAll()
        val minutes = AlarmReceiver.getIntervalMinutes(prefs.escalationSpeed)
        Toast.makeText(this, getString(R.string.snooze_toast, minutes), Toast.LENGTH_LONG).show()
        moveTaskToBack(true)
    }

    /**
     * Gestisce "Ho staccato!" (dall'app, dalla notifica o dallo schermo rosso).
     * Registra lo stacco, ferma allarme e notifiche e, se il riavvio automatico
     * è attivo, riprogramma l'allarme per domani alla stessa ora.
     */
    private fun handleHoStaccato() {
        val now = Calendar.getInstance()
        val overtimeMillis = (now.timeInMillis - currentShiftEndMillis()).coerceAtLeast(0L)
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

        // Riavvio automatico: l'orario di oggi è passato, quindi l'allarme scatta domani
        AlarmReceiver.scheduleAlarm(this, prefs.endHour, prefs.endMinute)

        // Aggiorna UI
        updateUI()
    }

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

    override fun onStop() {
        super.onStop()
        // Il riepilogo dopo lo stacco è un momento: riaprendo l'app si torna alla home
        prefs.isWaitingForNextAlarm = false
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
     * Aggiorna il badge in alto a destra nella home.
     * - Premium: "👑 Premium", non cliccabile.
     * - Piano gratuito: "👑 Passa a Premium", apre la schermata di acquisto.
     */
    private fun updateTrialBanner() {
        if (prefs.isPremium) {
            tvPremiumBadge.setText(R.string.premium_badge)
            tvPremiumBadge.isClickable = false
            tvPremiumBadge.setOnClickListener(null)
        } else {
            tvPremiumBadge.setText(R.string.settings_upgrade)
            tvPremiumBadge.isClickable = true
            tvPremiumBadge.setOnClickListener {
                startActivity(Intent(this, PremiumActivity::class.java))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        billingManager.destroy()
    }
}
