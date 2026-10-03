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
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
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

/**
 * "Insultami": un minuto di caos, pensato per chi NON guarda lo schermo.
 * - countdown arancione gigante su nero
 * - suoni a strati che si accumulano (sirena, poi antifurto e campane, poi tromba da stadio)
 * - la voce del telefono urla gli insulti (sintesi vocale di Android)
 * - vibrazione e flash della fotocamera sempre più intensi (max ~3 lampi al secondo)
 * - alla fine: esplosione e "GAME OVER!"
 * Finisce prima se l'utente preme "Ok, stacco". Gratis per tutti.
 */
class InsultamiActivity : AppCompatActivity() {

    companion object {
        private const val TOTAL_SECONDS = 60
        private const val SECONDS_PER_INSULT = 5
        private const val SECONDS_PER_PHASE = 20
        private const val FINAL_BEEPS_FROM = 5

        // Volume dei suoni di sottofondo, normale e "abbassato" mentre parla la voce
        private const val LOOP_VOLUME = 0.9f
        private const val LOOP_VOLUME_DUCKED = 0.35f
    }

    private lateinit var prefs: PreferencesManager
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var root: View
    private lateinit var tvPhase: TextView
    private lateinit var tvInsult: TextView
    private lateinit var tvTimer: TextView

    private var elapsedSeconds = 0
    private var started = false
    private var finished = false

    // Insulti ancora da usare per ogni fase (fasi 1-2 a caso, fase 3 in ordine fisso)
    private val remaining = mutableMapOf<Int, MutableList<String>>()

    // --- Suoni ---
    private var soundPool: SoundPool? = null
    private val sounds = mutableMapOf<Int, Int>()      // risorsa raw -> id nel SoundPool
    private val loopStreams = mutableMapOf<Int, Int>() // risorsa raw -> stream in loop
    private var soundsToLoad = 0

    // --- Voce ---
    private var tts: TextToSpeech? = null
    private var ttsReady = false

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
        tvTimer = findViewById(R.id.tvInsultTimer)

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
            loadSounds() // lo spettacolo parte quando i suoni sono pronti
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
        val pool = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attributes).build()
        soundPool = pool

        val all = listOf(
            R.raw.stacca_siren, R.raw.insult_caralarm, R.raw.insult_bells,
            R.raw.insult_horn, R.raw.insult_beep, R.raw.insult_explosion
        )
        soundsToLoad = all.size
        pool.setOnLoadCompleteListener { _, _, _ ->
            soundsToLoad--
            if (soundsToLoad == 0) handler.post { startShow() }
        }
        all.forEach { res -> sounds[res] = pool.load(this, res, 1) }

        // Se per qualche motivo i suoni non si caricano, si parte comunque
        handler.postDelayed({ startShow() }, 1500)
    }

    private fun setupVoice() {
        tts = TextToSpeech(this) { status ->
            val engine = tts ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val result = engine.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                return@TextToSpeech
            }
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            engine.setSpeechRate(1.05f)
            engine.setPitch(0.9f)
            // Mentre la voce parla, i suoni di sottofondo si abbassano
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { handler.post { duckLoops(true) } }
                override fun onDone(utteranceId: String?) { handler.post { duckLoops(false) } }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { handler.post { duckLoops(false) } }
            })
            ttsReady = true
        }
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
        tvTimer.text = TOTAL_SECONDS.toString()
        enterPhase(0)
        showNextInsult()
        handler.postDelayed(tick, 1000)
        handler.post(strobe)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (finished) return
            elapsedSeconds++
            val left = TOTAL_SECONDS - elapsedSeconds

            if (left <= 0) {
                onTimeUp()
                return
            }
            tvTimer.text = left.toString()

            if (elapsedSeconds % SECONDS_PER_PHASE == 0) enterPhase(currentPhase())
            if (elapsedSeconds % SECONDS_PER_INSULT == 0) showNextInsult()

            // Ultimi secondi: bip e numero che "pulsa"
            if (left <= FINAL_BEEPS_FROM) {
                playOnce(R.raw.insult_beep, 1f)
                pulse(tvTimer)
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun currentPhase(): Int = (elapsedSeconds / SECONDS_PER_PHASE).coerceAtMost(2)

    /** Ogni fase aggiunge suoni, alza la vibrazione e accelera la sirena. */
    private fun enterPhase(phase: Int) {
        tvPhase.setText(
            when (phase) {
                0 -> R.string.insult_phase_1
                1 -> R.string.insult_phase_2
                else -> R.string.insult_phase_3
            }
        )
        when (phase) {
            0 -> startLoop(R.raw.stacca_siren)
            1 -> {
                startLoop(R.raw.insult_caralarm)
                startLoop(R.raw.insult_bells)
            }
            else -> startLoop(R.raw.insult_horn)
        }
        loopStreams[R.raw.stacca_siren]?.let { soundPool?.setRate(it, 1f + phase * 0.25f) }
        vibrateForPhase(phase)
    }

    private fun showNextInsult() {
        val phase = currentPhase()
        val list = remaining.getOrPut(phase) { mutableListOf() }
        if (list.isEmpty()) {
            val arrayRes = when (phase) {
                0 -> R.array.insults_phase_1
                1 -> R.array.insults_phase_2
                else -> R.array.insults_phase_3
            }
            val insults = resources.getStringArray(arrayRes).toList()
            list.addAll(if (phase == 2) insults else insults.shuffled())
        }
        val insult = list.removeAt(0)
        tvInsult.text = insult
        speak(insult)
    }

    /** La voce legge l'insulto (senza emoji, che leggerebbe ad alta voce come parole). */
    private fun speak(text: String) {
        if (!ttsReady) return
        val clean = text.replace(Regex("[^\\p{L}\\p{N}\\p{P}\\s]"), "").trim()
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "insult")
    }

    // --- Flash: lampi sempre più frequenti, al massimo ~3 al secondo ---
    private val strobe = object : Runnable {
        override fun run() {
            if (finished) return
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

    private fun vibrateForPhase(phase: Int) {
        val v = vibrator ?: return
        // Ritmo che si ripete: [pausa, vibra, pausa, vibra...] sempre più fitto e forte
        val (timings, amplitude) = when (phase) {
            0 -> longArrayOf(0, 200, 800) to 120
            1 -> longArrayOf(0, 400, 300) to 200
            else -> longArrayOf(0, 800, 100) to 255
        }
        val effect = if (v.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, intArrayOf(0, amplitude, 0), 0)
        } else {
            VibrationEffect.createWaveform(timings, 0)
        }
        v.cancel()
        v.vibrate(effect)
    }

    // --- Suoni ---

    private fun startLoop(res: Int) {
        val pool = soundPool ?: return
        val id = sounds[res] ?: return
        if (loopStreams.containsKey(res)) return
        val stream = pool.play(id, LOOP_VOLUME, LOOP_VOLUME, 1, -1, 1f)
        if (stream != 0) loopStreams[res] = stream
    }

    private fun playOnce(res: Int, volume: Float) {
        val pool = soundPool ?: return
        val id = sounds[res] ?: return
        pool.play(id, volume, volume, 2, 0, 1f)
    }

    private fun duckLoops(ducked: Boolean) {
        if (finished) return
        val volume = if (ducked) LOOP_VOLUME_DUCKED else LOOP_VOLUME
        loopStreams.values.forEach { soundPool?.setVolume(it, volume, volume) }
    }

    private fun stopLoops() {
        loopStreams.values.forEach { soundPool?.stop(it) }
        loopStreams.clear()
    }

    /** Ferma tutto: suoni, voce, flash, vibrazione. */
    private fun stopEverything() {
        handler.removeCallbacksAndMessages(null)
        stopLoops()
        tts?.stop()
        setTorch(false)
        vibrator?.cancel()
    }

    private fun pulse(view: View) {
        ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.15f, 1f).setDuration(300).start()
        ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.15f, 1f).setDuration(300).start()
    }

    // ------------------------------------------------------------------
    // Finali
    // ------------------------------------------------------------------

    /** L'utente si arrende e stacca: silenzio, piccolo premio, poi si chiude. */
    private fun onOkStacco() {
        if (finished) {
            close()
            return
        }
        finished = true
        stopEverything()
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.home_bg))
        tvPhase.visibility = View.INVISIBLE
        tvTimer.text = "🎉"
        tvInsult.setText(R.string.insult_win)
        handler.postDelayed({ close() }, 2500)
    }

    /** Il minuto è finito e l'utente è ancora lì: esplosione e GAME OVER. */
    private fun onTimeUp() {
        finished = true
        stopEverything()
        playOnce(R.raw.insult_explosion, 1f)

        // Lampo: flash acceso, schermo arancione e vibrazione lunga
        setTorch(true)
        handler.postDelayed({ setTorch(false) }, 600)
        vibrator?.vibrate(VibrationEffect.createOneShot(1500, VibrationEffect.DEFAULT_AMPLITUDE))
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.home_accent))
        handler.postDelayed({ root.setBackgroundColor(0xFF000000.toInt()) }, 250)

        tvPhase.visibility = View.INVISIBLE
        tvTimer.textSize = 64f
        tvTimer.setText(R.string.game_over)
        pulse(tvTimer)
        tvInsult.setText(R.string.insult_lose)
        handler.postDelayed({ speak(getString(R.string.game_over)) }, 1200)
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

    /** Se l'utente esce dall'app (tasto Home), il caos si ferma: niente suoni senza schermo. */
    override fun onStop() {
        super.onStop()
        if (!isFinishing) {
            stopEverything()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopEverything()
        tts?.shutdown()
        soundPool?.release()
        soundPool = null
    }
}
