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
import android.view.ViewGroup
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
import com.stacca.app.BuildConfig
import com.stacca.app.R
import com.stacca.app.billing.BillingManager
import com.stacca.app.data.NotificationMessages
import com.stacca.app.data.PreferencesManager
import com.stacca.app.data.StaccoManager
import com.stacca.app.notifications.AlarmSoundManager
import com.stacca.app.notifications.NotificationHelper
import com.stacca.app.receivers.AlarmReceiver
import com.stacca.app.util.PermissionHelper
import java.text.SimpleDateFormat
import java.util.*
import com.stacca.app.util.PreviewMode
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

    // Messaggio principale e scheda informativa: cambiano in base al momento (vedi updateUI)
    private lateinit var tvHeroLabel: TextView
    private lateinit var tvHeroTitle: TextView
    private lateinit var tvHeroSub: TextView
    private lateinit var tvCardLabel: TextView
    private lateinit var tvCardValue: TextView
    private lateinit var segments: View
    private lateinit var segmentViews: List<View>
    private lateinit var tvCardFootLeft: TextView
    private lateinit var tvCardFootRight: TextView
    private lateinit var tvExplain: TextView

    // Azioni
    private lateinit var btnPrimary: MaterialButton
    private lateinit var btnSecondary: MaterialButton
    private lateinit var tvSnooze: TextView
    private lateinit var btnSettings: View
    private lateinit var tvPremiumBadge: TextView

    // Card protezione permessi
    private lateinit var cardPermissions: View
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
                // Non durante lo straordinario: riprogrammare ora sposterebbe il turno a domani
                if (prefs.isAlarmActive &&
                    PermissionHelper.canScheduleExactAlarms(this@MainActivity) &&
                    System.currentTimeMillis() < currentShiftEndMillis()) {
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
        window.setBackgroundDrawableResource(R.color.home_bg)
        SystemBarsHelper.applyInsets(this)

        prefs = PreferencesManager(this)
        notificationHelper = NotificationHelper(this)

        billingManager = BillingManager(this) { _ -> }
        billingManager.onPremiumRestored = { updateTrialBanner() }
        billingManager.connect()

        initViews()
        applyCompactLayoutIfNeeded()
        setupListeners()
        setupPreviewMenu()
        updateUI()
        startClockUpdate()
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
        tvHeroLabel = findViewById(R.id.tvHeroLabel)
        tvHeroTitle = findViewById(R.id.tvHeroTitle)
        tvHeroSub = findViewById(R.id.tvHeroSub)
        tvCardLabel = findViewById(R.id.tvCardLabel)
        tvCardValue = findViewById(R.id.tvCardValue)
        segments = findViewById(R.id.segments)
        segmentViews = listOf(R.id.seg1, R.id.seg2, R.id.seg3, R.id.seg4, R.id.seg5, R.id.seg6)
            .map { findViewById(it) }
        tvCardFootLeft = findViewById(R.id.tvCardFootLeft)
        tvCardFootRight = findViewById(R.id.tvCardFootRight)
        tvExplain = findViewById(R.id.tvExplain)
        btnPrimary = findViewById(R.id.btnPrimary)
        btnSecondary = findViewById(R.id.btnSecondary)
        tvSnooze = findViewById(R.id.tvSnooze)
        btnSettings = findViewById(R.id.btnSettings)
        tvPremiumBadge = findViewById(R.id.tvPremiumBadge)
        // Card protezione permessi
        cardPermissions = findViewById(R.id.cardPermissions)
        tvPermNotification = findViewById(R.id.tvPermNotification)
        tvPermExactAlarm = findViewById(R.id.tvPermExactAlarm)
        tvPermBattery = findViewById(R.id.tvPermBattery)
    }

    /**
     * Schermi bassi (es. 360×640): perché tutto stia in una schermata senza tagliare nulla,
     * riduce prima spaziature e padding, poi titolo e numero grande.
     * Con caratteri molto ingranditi la home può comunque scorrere.
     */
    private fun applyCompactLayoutIfNeeded() {
        if (resources.configuration.screenHeightDp >= 720) return

        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        fun View.setTopMargin(value: Int) {
            (layoutParams as ViewGroup.MarginLayoutParams).topMargin = dp(value)
        }

        findViewById<View>(R.id.spaceTop).layoutParams.height = dp(12)
        findViewById<View>(R.id.spaceBottom).minimumHeight = dp(12)
        tvHeroTitle.setTopMargin(4)
        tvHeroSub.setTopMargin(4)
        val card = findViewById<MaterialCardView>(R.id.cardInfo)
        card.setTopMargin(12)
        card.getChildAt(0).setPadding(dp(16), dp(14), dp(16), dp(6))
        segments.setTopMargin(10)
        (tvCardFootLeft.parent as View).setTopMargin(4)
        btnPrimary.layoutParams.height = dp(48)
        btnSecondary.layoutParams.height = dp(44)
        btnSecondary.setTopMargin(8)

        tvHeroTitle.textSize = 26f
        tvCardValue.textSize = 38f
    }

    /** Solo versione di prova: mostra il riquadro permessi anche se sono tutti attivi. */
    private var previewPermissionsCard = false

    /**
     * Solo versione di prova (debug): pressione lunga su "Stacca!" apre il menu
     * per vedere ogni schermata, anche da utente Premium. Nella versione su Play non esiste.
     */
    private fun setupPreviewMenu() {
        if (!BuildConfig.DEBUG) return
        findViewById<View>(R.id.tvAppTitle).setOnLongClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.preview_title)
                .setItems(R.array.preview_items) { _, which ->
                    fun open(target: Class<*>, extra: Intent.() -> Unit = {}) {
                        startActivity(Intent(this, target).apply {
                            putExtra(PreviewMode.EXTRA, true)
                            extra()
                        })
                    }
                    when (which) {
                        0 -> open(PaywallActivity::class.java)
                        1 -> open(TrialExpiredActivity::class.java)
                        2 -> {
                            previewPermissionsCard = !previewPermissionsCard
                            updatePermissionsCard()
                        }
                        3 -> open(FullScreenAlertActivity::class.java) {
                            putExtra("level", NotificationMessages.Level.NUCLEAR.name)
                        }
                        4 -> open(InsultamiActivity::class.java)
                        5 -> open(LoginActivity::class.java)
                        6 -> open(HistoryActivity::class.java)
                    }
                }
                .show()
            true
        }
    }

    private fun setupListeners() {
        // I due pulsanti cambiano funzione in base al momento (vedi updateUI)
        btnPrimary.setOnClickListener {
            when (currentHeroState()) {
                HeroState.SPENTO -> checkPermissionsAndActivate()
                HeroState.COUNTDOWN, HeroState.STRAORDINARIO -> openInsultami()
                HeroState.STACCATO -> {
                    // "Buona serata": il riepilogo ha fatto il suo lavoro, si chiude l'app
                    prefs.isWaitingForNextAlarm = false
                    finish()
                }
            }
        }
        btnSecondary.setOnClickListener {
            when (currentHeroState()) {
                HeroState.SPENTO -> openInsultami()
                HeroState.STACCATO -> startActivity(Intent(this, HistoryActivity::class.java))
                HeroState.COUNTDOWN -> deactivateAlarm()
                HeroState.STRAORDINARIO -> handleHoStaccato()
            }
        }

        // "Modifica orario" nella scheda, quando l'orario si può cambiare
        findViewById<View>(R.id.tvEditTime).setOnClickListener {
            val state = currentHeroState()
            if (state == HeroState.SPENTO || state == HeroState.COUNTDOWN) showTimePicker()
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.btnHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<View>(R.id.tvDeactivate).setOnClickListener { deactivateAlarm() }
    }

    private fun openInsultami() {
        startActivity(Intent(this, InsultamiActivity::class.java))
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
        segments.visibility = View.GONE
        tvSnooze.visibility = View.GONE // il rimando sta solo nella notifica e nello schermo rosso
        findViewById<View>(R.id.tvDeactivate).visibility = View.GONE
        btnSecondary.visibility = View.VISIBLE
        tvCardFootRight.text = ""
        tvCardFootRight.minHeight = 0
        findViewById<View>(R.id.tvEditTime).visibility = View.GONE
        (tvCardFootLeft.parent as View).visibility = View.VISIBLE
        tvExplain.setText(R.string.home_explain_default)
        tvExplain.visibility = View.VISIBLE
        tvCardValue.setTextColor(ContextCompat.getColor(this, R.color.home_text))
        tvCardFootRight.setTextColor(ContextCompat.getColor(this, R.color.home_text_secondary))

        when (state) {
            HeroState.SPENTO -> {
                tvHeroLabel.setText(R.string.home_off_label)
                tvHeroTitle.setText(R.string.home_off_title)
                tvHeroSub.setText(R.string.home_off_sub)
                tvCardLabel.setText(R.string.home_card_end_time)
                tvCardValue.text = endTimeText
                tvCardFootLeft.setText(R.string.home_off_foot)
                showEditTimeCommand()
                // Porta d'ingresso: prima si attiva l'allarme, Insultami è solo una prova
                tvExplain.visibility = View.INVISIBLE
                btnPrimary.setText(R.string.activate_alarm)
                btnSecondary.setText(R.string.btn_try_insultami)
            }

            HeroState.COUNTDOWN -> {
                val endMillis = currentShiftEndMillis()
                val isToday = isSameDay(endMillis, now)
                tvHeroLabel.setText(
                    if (isToday) R.string.home_cd_label_today else R.string.home_cd_label_tomorrow
                )
                tvHeroTitle.setText(R.string.home_cd_title)
                tvHeroSub.text = getString(
                    if (isToday) R.string.home_cd_sub_today else R.string.home_cd_sub_tomorrow,
                    endTimeText
                )
                tvCardLabel.setText(R.string.home_card_remaining)
                tvCardValue.text = formatRemaining(endMillis - now)
                tvCardFootLeft.text = ""
                showEditTimeCommand()
                btnPrimary.setText(R.string.btn_insultami_home)
                btnSecondary.setText(R.string.btn_deactivate_small)
            }

            HeroState.STRAORDINARIO -> {
                // I segmenti mostrano i promemoria davvero inviati (max 6)
                val sent = prefs.currentEscalationStep.coerceIn(0, 6)
                tvHeroLabel.setText(R.string.home_ot_label)
                tvHeroTitle.setText(R.string.home_ot_title)
                tvHeroSub.text = getString(R.string.home_ot_sub, endTimeText)
                tvCardLabel.setText(R.string.home_card_still_here)
                val overtimeMinutes = ((now - currentShiftEndMillis()) / 60_000).toInt()
                tvCardValue.text = formatMinutes(overtimeMinutes)
                segments.visibility = View.VISIBLE
                val accent = ContextCompat.getColor(this, R.color.home_accent)
                val neutral = ContextCompat.getColor(this, R.color.home_border)
                segmentViews.forEachIndexed { i, seg ->
                    seg.background.mutate().setTint(if (i < sent) accent else neutral)
                }
                tvCardFootLeft.text =
                    resources.getQuantityString(R.plurals.home_reminders_sent, sent, sent)
                tvCardFootRight.text = getString(R.string.home_reminders_total, sent)
                tvExplain.setText(R.string.home_explain_overtime)
                btnPrimary.setText(R.string.btn_insultami_home)
                btnSecondary.setText(R.string.btn_ho_staccato_home)
            }

            HeroState.STACCATO -> {
                // Il momento premio
                val overtime = prefs.lastShiftOvertimeMinutes
                val onTime = overtime <= PreferencesManager.ON_TIME_THRESHOLD_MINUTES
                tvHeroLabel.setText(
                    if (onTime) R.string.home_done_label_ontime else R.string.home_done_label_late
                )
                tvHeroTitle.setText(
                    if (onTime) R.string.home_done_title_ontime else R.string.home_done_title_late
                )
                tvHeroSub.setText(
                    if (onTime) R.string.home_done_sub_ontime else R.string.home_done_sub_late
                )
                // Scheda del premio: la serie se in orario, i minuti regalati se in ritardo
                if (onTime) {
                    val streak = prefs.streakCount
                    tvCardLabel.setText(R.string.home_done_card_streak)
                    tvCardValue.text = resources.getQuantityString(R.plurals.history_days, streak, streak) + " 🔥"
                    tvCardValue.setTextColor(ContextCompat.getColor(this, R.color.home_success))
                    if (streak >= 2 && streak >= prefs.bestStreak) {
                        tvCardFootRight.setText(R.string.home_done_record)
                        tvCardFootRight.setTextColor(ContextCompat.getColor(this, R.color.home_success))
                    }
                } else {
                    tvCardLabel.setText(R.string.home_done_card_gifted)
                    tvCardValue.text = formatMinutes(overtime)
                    tvCardValue.setTextColor(ContextCompat.getColor(this, R.color.home_accent))
                }
                tvCardFootLeft.text = when {
                    !prefs.isAlarmActive -> ""
                    onTime -> getString(R.string.home_done_foot, endTimeText)
                    else -> getString(R.string.home_done_foot_late, endTimeText)
                }
                // Dopo lo stacco non servono spinte: si saluta e si va
                tvExplain.visibility = View.INVISIBLE
                btnPrimary.setText(R.string.btn_good_evening)
                btnSecondary.setText(R.string.btn_open_history)
            }
        }
    }

    /** Mostra "Modifica orario" a destra nella scheda, come comando toccabile. */
    private fun showEditTimeCommand() {
        // Pulsantino "Modifica orario" sotto il numero; la riga in fondo alla scheda non serve
        findViewById<View>(R.id.tvEditTime).visibility = View.VISIBLE
        (tvCardFootLeft.parent as View).visibility = View.GONE
    }

    /** "26 minuti" oppure "1 h 05 min". */
    private fun formatMinutes(totalMinutes: Int): String {
        val minutes = totalMinutes.coerceAtLeast(0)
        if (minutes < 60) {
            return resources.getQuantityString(R.plurals.home_minutes, minutes, minutes)
        }
        return getString(R.string.home_hours_minutes, minutes / 60, minutes % 60)
    }

    /** Tempo che manca, senza secondi: "2 h 14 min", "14 minuti", "meno di 1 min". */
    private fun formatRemaining(millis: Long): String {
        val minutes = (millis / 60_000).toInt()
        return if (minutes < 1) getString(R.string.home_less_than_minute) else formatMinutes(minutes)
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
     * Gestisce "Ho staccato!" (dall'app, dalla notifica o dallo schermo rosso).
     * Registra lo stacco, ferma allarme e notifiche e, se il riavvio automatico
     * è attivo, riprogramma l'allarme per domani alla stessa ora.
     */
    private fun handleHoStaccato() {
        StaccoManager.registerStacco(this)
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        // Turno lasciato aperto da più di 3 ore (allarmi ignorati o telefono spento): si chiude qui
        StaccoManager.closeAbandonedShift(this, notify = false)
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
        val hasNotif = PermissionHelper.hasNotificationPermission(this) && !previewPermissionsCard
        val hasExact = PermissionHelper.canScheduleExactAlarms(this) && !previewPermissionsCard
        val hasBattery = PermissionHelper.isIgnoringBatteryOptimizations(this) && !previewPermissionsCard

        // Tutto a posto: si vede la home normale
        val homeContent = findViewById<View>(R.id.homeContent)
        if (hasNotif && hasExact && hasBattery) {
            cardPermissions.visibility = View.GONE
            homeContent.visibility = View.VISIBLE
            return
        }
        // Mancano permessi: si vede solo il riquadro, centrato
        cardPermissions.visibility = View.VISIBLE
        homeContent.visibility = View.GONE

        showPermissionRow(R.id.rowPermNotification, tvPermNotification, hasNotif) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionFromCardLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        showPermissionRow(R.id.rowPermExactAlarm, tvPermExactAlarm, hasExact) {
            PermissionHelper.exactAlarmSettingsIntent(this)?.let { startActivity(it) }
        }
        showPermissionRow(R.id.rowPermBattery, tvPermBattery, hasBattery) {
            startActivity(PermissionHelper.batteryOptimizationIntent(this))
        }
    }

    /** Una riga del riquadro permessi: "Fatto" se concesso, altrimenti "Attiva" e la riga è toccabile. */
    private fun showPermissionRow(rowId: Int, status: TextView, granted: Boolean, enable: () -> Unit) {
        val row = findViewById<View>(rowId)
        if (granted) {
            status.setText(R.string.perm_v2_done)
            status.background = null
            status.setTextColor(ContextCompat.getColor(this, R.color.home_text_secondary))
            row.setOnClickListener(null)
            row.isClickable = false
        } else {
            status.setText(R.string.perm_v2_enable)
            status.setBackgroundResource(R.drawable.bg_pill_accent)
            status.setTextColor(ContextCompat.getColor(this, R.color.home_on_accent))
            row.setOnClickListener { enable() }
        }
    }

    /**
     * Aggiorna il badge in alto a destra nella home.
     * - Premium: "PREMIUM", non cliccabile.
     * - Piano gratuito: "PASSA A PREMIUM", apre la schermata di acquisto.
     */
    private fun updateTrialBanner() {
        if (prefs.isPremium) {
            tvPremiumBadge.visibility = View.GONE
            tvPremiumBadge.isClickable = false
            tvPremiumBadge.setOnClickListener(null)
        } else {
            tvPremiumBadge.visibility = View.VISIBLE
            tvPremiumBadge.setText(R.string.premium_badge_upgrade)
            tvPremiumBadge.isClickable = true
            tvPremiumBadge.setOnClickListener {
                startActivity(Intent(this, PaywallActivity::class.java))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        billingManager.destroy()
    }
}
