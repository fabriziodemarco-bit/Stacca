package com.stacca.app.ui

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.stacca.app.R
import com.stacca.app.data.PreferencesManager
import com.stacca.app.util.PreviewMode
import com.stacca.app.util.SystemBarsHelper
import java.util.Locale
import kotlin.random.Random

/**
 * "Insultami": un minuto, sei battute dette dalla voce del telefono.
 * - countdown arancione gigante su nero, che trema e si "sporca" sempre di più
 * - una battuta ogni 10 secondi (a 0, 10, 20, 30, 40, 50), da una delle 3 versioni
 * - unico effetto sonoro: un battito del cuore che accelera
 * - flash della fotocamera e un colpo di vibrazione a ogni battuta
 * - se stacchi: il cuore rallenta e "Finalmente."; se arrivi a zero: esplosione
 * Finisce prima se l'utente preme "Ok, stacco". Gratis per tutti.
 */
class InsultamiActivity : AppCompatActivity() {

    companion object {
        private const val TOTAL_SECONDS = 60
        private const val SECONDS_PER_LINE = 10
        private const val SECONDS_PER_PHASE = 20
        private const val SHAKE_HARD_FROM = 20 // fase della furia: il numero trema di più
        private const val FREEZE_FROM = 10     // ultimi secondi: arriva il gelo e tutto si ferma

        // Si parte appena voce e suoni sono pronti; se tardano, si parte comunque
        private const val READY_TIMEOUT_MS = 2500L

        // Battito del cuore: da tranquillo ad ansia
        private const val BPM_START = 60f
        private const val BPM_END = 150f
        private const val HEART_VOLUME_START = 0.35f
        private const val HEART_VOLUME_END = 1f

        private val VERSIONS = listOf(
            R.array.insults_version_a,
            R.array.insults_version_b,
            R.array.insults_version_c,
            R.array.insults_version_d,
            R.array.insults_version_e
        )

        /**
         * Voce provvisoria (sintesi vocale di Android): velocità e tono di ciascuna battuta.
         * Ordine: sarcasmo, richiamo, impazienza, sfottò, urlo, gelo.
         * Separata dai testi: per cambiare voce si tocca solo questa lista.
         */
        private val LINE_VOICE = listOf(
            0.80f to 1.50f, // lenta e acuta, finta stupita
            1.15f to 1.10f, // svelta
            1.35f to 1.20f, // ancora più veloce
            0.75f to 0.60f, // bassa e lenta, presa in giro
            1.60f to 1.90f, // criceto isterico
            0.60f to 0.55f  // glaciale
        )
    }

    private lateinit var prefs: PreferencesManager
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var root: View
    private lateinit var tvPhase: TextView
    private lateinit var tvInsult: TextView
    private lateinit var bubble: View
    private lateinit var tail: View
    private lateinit var tailFill: ImageView
    private lateinit var icicles: View
    private lateinit var tvTimer: TextView

    private var version = 0
    private lateinit var lines: Array<String>

    private var startAt = 0L      // istante (uptime) in cui è partita la prima battuta
    private var elapsedSeconds = 0
    private var started = false
    private var finished = false

    // --- Suoni (solo battito ed esplosione) ---
    private var soundPool: SoundPool? = null
    private var heartbeatId = 0
    private var explosionId = 0
    private var soundsReady = false

    // --- Voce ---
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsDone = false   // pronta oppure non disponibile: in ogni caso non la aspettiamo più
    private var speaking = false

    // --- Flash e vibrazione ---
    private var cameraManager: CameraManager? = null
    private var torchCameraId: String? = null
    private var torchOn = false
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_insultami)
        window.setBackgroundDrawableResource(android.R.color.black)
        SystemBarsHelper.applyInsets(this)

        prefs = PreferencesManager(this)
        root = findViewById(R.id.insultRoot)
        tvPhase = findViewById(R.id.tvInsultPhase)
        tvInsult = findViewById(R.id.tvInsult)
        bubble = findViewById(R.id.insultBubble)
        tail = findViewById(R.id.insultTail)
        tailFill = findViewById(R.id.ivInsultTailFill)
        icicles = findViewById(R.id.ivInsultIcicles)
        bubble.visibility = View.INVISIBLE // compare con la prima battuta
        tvTimer = findViewById(R.id.tvInsultTimer)

        // Una versione a caso tra quelle non ancora sentite; finito il giro si ricomincia,
        // ma mai con la stessa della volta scorsa
        val fresh = VERSIONS.indices.filter { it !in prefs.usedInsultVersions }
        version = fresh.ifEmpty { VERSIONS.indices.filter { it != prefs.lastInsultVersion } }.random()
        lines = resources.getStringArray(VERSIONS[version])

        findViewById<MaterialButton>(R.id.btnInsultOk).setOnClickListener { onOkStacco() }

        // Il tasto indietro non salva nessuno: si esce solo staccando 😈
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (finished) close()
            }
        })

        setupVibrator()
        if (prefs.flashEnabled) setupTorch()
        if (prefs.soundEnabled) {
            setupVoice()
            loadSounds()
            handler.postDelayed({ startShow() }, READY_TIMEOUT_MS)
        } else {
            startShow()
        }
    }

    // ------------------------------------------------------------------
    // Preparazione
    // ------------------------------------------------------------------

    private fun loadSounds() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attributes).build()
        soundPool = pool

        var toLoad = 2
        pool.setOnLoadCompleteListener { _, _, _ ->
            toLoad--
            if (toLoad == 0) handler.post {
                soundsReady = true
                startIfReady()
            }
        }
        heartbeatId = pool.load(this, R.raw.insult_heartbeat, 1)
        explosionId = pool.load(this, R.raw.insult_explosion, 1)
    }

    private fun setupVoice() {
        tts = TextToSpeech(this) { status ->
            val engine = tts
            val ok = engine != null && status == TextToSpeech.SUCCESS &&
                engine.setLanguage(Locale.getDefault()).let {
                    it != TextToSpeech.LANG_MISSING_DATA && it != TextToSpeech.LANG_NOT_SUPPORTED
                }
            if (ok && engine != null) {
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                // Mentre la voce parla, il battito si abbassa
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { speaking = true }
                    override fun onDone(utteranceId: String?) { speaking = false }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { speaking = false }
                })
                ttsReady = true
            }
            handler.post {
                ttsDone = true
                startIfReady()
            }
        }
    }

    private fun startIfReady() {
        if (soundsReady && ttsDone) startShow()
    }

    private fun setupTorch() {
        try {
            val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            torchCameraId = manager.cameraIdList.firstOrNull { id ->
                val c = manager.getCameraCharacteristics(id)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
            cameraManager = manager
        } catch (e: Exception) {
            torchCameraId = null // niente flash: pazienza, il resto funziona
        }
    }

    private fun setupVibrator() {
        if (!prefs.vibrationEnabled) return
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // ------------------------------------------------------------------
    // Lo spettacolo
    // ------------------------------------------------------------------

    private fun startShow() {
        if (started || finished) return
        started = true
        // Conta come sentita anche se poi viene interrotta
        val used = prefs.usedInsultVersions
        prefs.usedInsultVersions = (if (used.size >= VERSIONS.size) emptySet() else used) + version
        prefs.lastInsultVersion = version

        // Il minuto si misura da qui: i caricamenti non lo consumano
        startAt = SystemClock.uptimeMillis()
        showTimer(TOTAL_SECONDS)
        enterPhase(0)
        showLine(0)
        handler.postAtTime(tick, startAt + 1000)
        handler.post(strobe)
        handler.post(heartbeat)
    }

    // Un tick al secondo, agganciato all'orologio: niente ritardi che si accumulano
    private val tick = object : Runnable {
        override fun run() {
            if (finished) return
            elapsedSeconds++
            val left = TOTAL_SECONDS - elapsedSeconds

            if (left <= 0) {
                onTimeUp()
                return
            }
            showTimer(left)
            if (left == FREEZE_FROM) enterFreeze()
            else if (elapsedSeconds % SECONDS_PER_PHASE == 0) enterPhase(currentPhase())
            if (elapsedSeconds % SECONDS_PER_LINE == 0) showLine(elapsedSeconds / SECONDS_PER_LINE)

            handler.postAtTime(this, startAt + (elapsedSeconds + 1) * 1000L)
        }
    }

    private fun currentPhase(): Int = (elapsedSeconds / SECONDS_PER_PHASE).coerceAtMost(2)

    private fun enterPhase(phase: Int) {
        tvPhase.setText(
            when (phase) {
                0 -> R.string.insult_phase_1
                1 -> R.string.insult_phase_2
                else -> R.string.insult_phase_3
            }
        )
    }

    /** Gelo: la scritta in alto cambia, il countdown si ferma e diventa ghiaccio, il flash si spegne. */
    private fun enterFreeze() {
        tvPhase.setText(R.string.insult_phase_4)
        tvPhase.setTextColor(ContextCompat.getColor(this, R.color.insult_ice))
        setTorch(false)
    }

    private fun isFrozen(): Boolean = TOTAL_SECONDS - elapsedSeconds <= FREEZE_FROM

    /** Battuta n. [index] (0-5): testo, voce e vibrazione partono insieme. */
    private fun showLine(index: Int) {
        if (index !in lines.indices) return
        val line = lines[index]
        showBubble(line, index)
        val (rate, pitch) = LINE_VOICE[index.coerceAtMost(LINE_VOICE.lastIndex)]
        speak(line, rate, pitch)
        vibrateForLine(index)
    }

    // --- Fumetto: la nuvoletta cambia colore seguendo la curva delle battute ---

    /**
     * Battute 1-2 bianche, 3-4 gialle, 5 (urlo) arancio grande che trema.
     * La 6 (gelo) è azzurro ghiaccio con i ghiaccioli: dritta, immobile, compare piano.
     */
    private fun showBubble(line: String, index: Int) {
        when {
            index >= 5 -> paintBubble(line, R.color.insult_ice, R.color.insult_ice_text, 21f, icicles = true)
            index == 4 -> paintBubble(line, R.color.insult_bubble_scream, null, 30f)
            index >= 2 -> paintBubble(line, R.color.insult_bubble_yellow, null, 24f)
            else -> paintBubble(line, R.color.insult_bubble_white, null, 24f)
        }
        if (index >= 5) frostIn() else popIn()
        if (index == 4) handler.postDelayed(bubbleShake, 300)
    }

    /** Colora la nuvoletta (e il beccuccio o i ghiaccioli) e ci scrive il testo in stampatello. */
    private fun paintBubble(text: String, fillRes: Int, textRes: Int?, sizeSp: Float, icicles: Boolean = false) {
        handler.removeCallbacks(bubbleShake)
        bubble.animate().cancel()
        bubble.visibility = View.VISIBLE
        bubble.translationX = 0f
        bubble.alpha = 1f

        val fill = ContextCompat.getColor(this, fillRes)
        val background = (ContextCompat.getDrawable(this, R.drawable.bg_insult_bubble) as GradientDrawable).mutate() as GradientDrawable
        background.setColor(fill)
        tvInsult.background = background
        tvInsult.setTextColor(if (textRes != null) ContextCompat.getColor(this, textRes) else 0xFF000000.toInt())
        tvInsult.textSize = sizeSp
        tvInsult.text = text.uppercase(Locale.getDefault())
        tail.visibility = if (icicles) View.GONE else View.VISIBLE
        tailFill.imageTintList = ColorStateList.valueOf(fill)
        this.icicles.visibility = if (icicles) View.VISIBLE else View.GONE
    }

    /** "Pop": salta fuori con un rimbalzo, ogni volta storta in modo diverso. */
    private fun popIn() {
        val tilt = (2f + Random.nextFloat() * 3f) * (if (Random.nextBoolean()) 1 else -1)
        bubble.rotation = tilt
        bubble.scaleX = 0.6f
        bubble.scaleY = 0.6f
        bubble.animate().scaleX(1f).scaleY(1f)
            .setDuration(280).setInterpolator(OvershootInterpolator(3f)).start()
    }

    /** Il gelo non salta: dritto, fermo, compare piano come la brina. */
    private fun frostIn() {
        bubble.rotation = 0f
        bubble.scaleX = 1f
        bubble.scaleY = 1f
        bubble.alpha = 0f
        bubble.animate().alpha(1f).setDuration(900).start()
    }

    // L'urlo trema finché non arriva la battuta successiva
    private val bubbleShake = object : Runnable {
        override fun run() {
            if (finished) return
            val d = resources.displayMetrics.density
            bubble.translationX = (Random.nextFloat() * 8f - 4f) * d
            bubble.rotation = Random.nextFloat() * 8f - 4f
            handler.postDelayed(this, 90)
        }
    }

    /** La voce legge il testo (senza emoji). QUEUE_FLUSH: due battute non si sovrappongono mai. */
    private fun speak(text: String, rate: Float, pitch: Float) {
        val engine = tts ?: return
        if (!ttsReady) return
        val clean = text.replace(Regex("[^\\p{L}\\p{N}\\p{P}\\s]"), "").trim()
        engine.setSpeechRate(rate)
        engine.setPitch(pitch)
        engine.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "insult")
    }

    // --- Countdown: numero gigante che trema, con un'ombra rossa sfasata. Nel gelo si ferma ---
    private fun showTimer(left: Int) {
        tvTimer.text = left.toString()
        val d = resources.displayMetrics.density
        if (left <= FREEZE_FROM) {
            tvTimer.translationX = 0f
            tvTimer.translationY = 0f
            tvTimer.rotation = 0f
            tvTimer.setTextColor(ContextCompat.getColor(this, R.color.insult_ice))
            tvTimer.setShadowLayer(0.01f, 4 * d, 4 * d, 0x99FFFFFF.toInt())
            return
        }
        val hard = left <= SHAKE_HARD_FROM
        val move = (if (hard) 10f else 4f) * d
        val tilt = if (hard) 7f else 3f
        tvTimer.translationX = Random.nextFloat() * 2 * move - move
        tvTimer.translationY = Random.nextFloat() * 2 * move - move
        tvTimer.rotation = Random.nextFloat() * 2 * tilt - tilt

        val glitch = (if (hard) 7f else 4f) * d
        val dx = if (Random.nextBoolean()) glitch else -glitch
        val dy = if (Random.nextBoolean()) glitch / 2 else -glitch / 2
        tvTimer.setShadowLayer(0.01f, dx, dy, 0xCCFF2D2D.toInt())
        if (hard) pulse(tvTimer)
    }

    private fun resetTimerLook() {
        tvTimer.translationX = 0f
        tvTimer.translationY = 0f
        tvTimer.rotation = 0f
        tvTimer.setShadowLayer(0f, 0f, 0f, 0)
    }

    // --- Battito del cuore: accelera e cresce con il passare del minuto ---
    private val heartbeat = object : Runnable {
        override fun run() {
            if (finished) return
            val progress = ((SystemClock.uptimeMillis() - startAt) / (TOTAL_SECONDS * 1000f)).coerceIn(0f, 1f)
            val bpm = BPM_START + (BPM_END - BPM_START) * progress
            var volume = HEART_VOLUME_START + (HEART_VOLUME_END - HEART_VOLUME_START) * progress
            if (speaking) volume *= 0.5f
            playSound(heartbeatId, volume)
            handler.postDelayed(this, (60_000f / bpm).toLong())
        }
    }

    // --- Flash: lampi sempre più frequenti, al massimo ~3 al secondo ---
    private val strobe = object : Runnable {
        override fun run() {
            if (finished || isFrozen()) return
            val (period, onTime) = when (currentPhase()) {
                0 -> 1000L to 120L
                1 -> 600L to 100L
                else -> 350L to 90L
            }
            setTorch(true)
            handler.postDelayed({ if (!finished) setTorch(false) }, onTime)
            handler.postDelayed(this, period)
        }
    }

    private fun setTorch(on: Boolean) {
        val id = torchCameraId ?: return
        if (torchOn == on) return
        try {
            cameraManager?.setTorchMode(id, on)
            torchOn = on
        } catch (e: Exception) {
            torchCameraId = null // fotocamera occupata o non disponibile: niente flash
        }
    }

    /** Un colpo di vibrazione a ogni battuta; tre colpi sull'urlo. */
    private fun vibrateForLine(index: Int) {
        val v = vibrator ?: return
        val amplitude = (110 + index * 28).coerceAtMost(255)
        val effect = if (index == 4) {
            val timings = longArrayOf(0, 140, 90, 140, 90, 140)
            if (v.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(timings, intArrayOf(0, 255, 0, 255, 0, 255), -1)
            } else {
                VibrationEffect.createWaveform(timings, -1)
            }
        } else {
            VibrationEffect.createOneShot(
                160,
                if (v.hasAmplitudeControl()) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
            )
        }
        v.vibrate(effect)
    }

    private fun playSound(id: Int, volume: Float) {
        if (id == 0) return
        soundPool?.play(id, volume, volume, 1, 0, 1f)
    }

    /** Ferma tutto: timer, voce, flash, vibrazione. Il battito già partito si esaurisce da solo. */
    private fun stopEverything() {
        handler.removeCallbacksAndMessages(null)
        soundPool?.autoPause()
        tts?.stop()
        setTorch(false)
        vibrator?.cancel()
    }

    private fun pulse(view: View) {
        ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.12f, 1f).setDuration(250).start()
        ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.12f, 1f).setDuration(250).start()
    }

    // ------------------------------------------------------------------
    // Finali
    // ------------------------------------------------------------------

    /** L'utente stacca: il cuore rallenta, la voce dice "Finalmente.", poi si chiude. */
    private fun onOkStacco() {
        if (finished) {
            close()
            return
        }
        finished = true
        stopEverything()
        soundPool?.autoResume()

        root.setBackgroundColor(ContextCompat.getColor(this, R.color.home_bg))
        tvPhase.visibility = View.INVISIBLE
        resetTimerLook()
        tvTimer.text = "✌️"
        paintBubble(getString(R.string.insult_win), R.color.home_success, null, 22f)
        popIn()
        speak(getString(R.string.insult_win_voice), 0.7f, 0.8f)

        // Battiti sempre più lenti e piano: il sollievo si sente
        var at = 0L
        listOf(450L to 0.7f, 600L to 0.55f, 800L to 0.45f, 1000L to 0.35f, 1150L to 0.3f).forEach { (gap, volume) ->
            handler.postDelayed({ playSound(heartbeatId, volume) }, at)
            at += gap
        }
        handler.postDelayed({ close() }, at + 300)
    }

    /** Il minuto è finito e l'utente è ancora lì: esplosione, lo schermo trema, poi la resa. */
    private fun onTimeUp() {
        finished = true
        stopEverything()
        soundPool?.autoResume()
        playSound(explosionId, 1f)

        // Lampo: flash acceso, schermo arancione, vibrazione lunga e scossone
        setTorch(true)
        handler.postDelayed({ setTorch(false) }, 600)
        vibrator?.vibrate(VibrationEffect.createOneShot(1500, VibrationEffect.DEFAULT_AMPLITUDE))
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.home_accent))
        handler.postDelayed({ root.setBackgroundColor(0xFF000000.toInt()) }, 250)
        val d = resources.displayMetrics.density
        ObjectAnimator.ofFloat(root, View.TRANSLATION_X, 0f, 24 * d, -24 * d, 18 * d, -18 * d, 10 * d, -10 * d, 0f)
            .setDuration(700).start()

        tvPhase.visibility = View.INVISIBLE
        resetTimerLook()
        tvTimer.text = "💥"
        pulse(tvTimer)
        paintBubble(getString(R.string.insult_lose), R.color.insult_bubble_white, null, 20f)
        popIn()
    }

    /**
     * Se l'allarme è attivo e il turno è davvero finito, "Ok, stacco" vale come "Ho staccato":
     * MainActivity registra lo stacco e ferma l'allarme. Altrimenti si chiude e basta.
     */
    private fun close() {
        stopEverything()
        val inOvertime = prefs.nextShiftEndMillis > 0 &&
            System.currentTimeMillis() >= prefs.nextShiftEndMillis
        if (prefs.isAlarmActive && inOvertime && !PreviewMode.isOn(intent)) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_HO_STACCATO, true)
            })
        }
        finish()
    }

    /** Se l'utente esce dall'app (tasto Home), lo spettacolo si ferma: niente suoni senza schermo. */
    override fun onStop() {
        super.onStop()
        if (!isFinishing) {
            stopEverything()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        tts?.stop()
        tts?.shutdown()
        soundPool?.release()
        soundPool = null
    }
}
