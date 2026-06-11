package com.stacca.app.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.stacca.app.R
import com.stacca.app.data.PreferencesManager

/**
 * Schermata di celebrazione — mostrata quando l'utente stacca in orario (<=5 min di overtime).
 *
 * Riceve via Intent:
 *  - EXTRA_STREAK_COUNT  → streak corrente dopo la registrazione
 *  - EXTRA_BEST_STREAK   → miglior streak di sempre
 *  - EXTRA_IS_NEW_RECORD → true se il record è stato battuto oggi
 *
 * Tono: euforico, ironico, motivante. Stile coerente con TempoNonVissutoActivity.
 */
class CelebrationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STREAK_COUNT  = "streak_count"
        const val EXTRA_BEST_STREAK   = "best_streak"
        const val EXTRA_IS_NEW_RECORD = "is_new_record"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_celebration)

        val streakCount  = intent.getIntExtra(EXTRA_STREAK_COUNT, 1)
        val bestStreak   = intent.getIntExtra(EXTRA_BEST_STREAK, 1)
        val isNewRecord  = intent.getBooleanExtra(EXTRA_IS_NEW_RECORD, false)

        setupUI(streakCount, bestStreak, isNewRecord)
        startAnimations()
    }

    private fun setupUI(streakCount: Int, bestStreak: Int, isNewRecord: Boolean) {
        // Numero streak grande
        val tvStreakCount = findViewById<TextView>(R.id.tvCelebStreakCount)
        tvStreakCount.text = streakCount.toString()

        // Etichetta plurale "giorno/giorni"
        val tvStreakDays = findViewById<TextView>(R.id.tvCelebStreakDays)
        tvStreakDays.text = resources.getQuantityString(R.plurals.streak_days, streakCount, streakCount)

        // Badge NUOVO RECORD (visibile solo se il record è stato battuto oggi)
        val cardRecord = findViewById<MaterialCardView>(R.id.cardNewRecord)
        if (isNewRecord) {
            cardRecord.visibility = View.VISIBLE
        }

        // Messaggio ironico casuale
        val messages = resources.getStringArray(R.array.celebration_messages)
        val tvMessage = findViewById<TextView>(R.id.tvCelebMessage)
        tvMessage.text = "\"${messages.random()}\""

        // Bottone chiudi
        findViewById<MaterialButton>(R.id.btnCelebClose).setOnClickListener {
            finish()
        }
    }

    private fun startAnimations() {
        // --- 1. Emoji principale: scale + bounce ---
        val tvEmoji = findViewById<TextView>(R.id.tvCelebEmoji)
        tvEmoji.scaleX = 0f
        tvEmoji.scaleY = 0f

        val scaleX = ObjectAnimator.ofFloat(tvEmoji, View.SCALE_X, 0f, 1.4f, 1f)
        val scaleY = ObjectAnimator.ofFloat(tvEmoji, View.SCALE_Y, 0f, 1.4f, 1f)
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 700
            interpolator = BounceInterpolator()
        }.start()

        // Piccolo bounce ripetuto dopo l'entrata (si agita ogni 2s)
        tvEmoji.postDelayed(object : Runnable {
            override fun run() {
                val bounceY = ObjectAnimator.ofFloat(tvEmoji, View.TRANSLATION_Y, 0f, -18f, 0f)
                bounceY.duration = 500
                bounceY.interpolator = AccelerateDecelerateInterpolator()
                bounceY.start()
                tvEmoji.postDelayed(this, 2200)
            }
        }, 1200)

        // --- 2. Titolo: fade-in dall'alto ---
        val tvTitle = findViewById<TextView>(R.id.tvCelebTitle)
        tvTitle.alpha = 0f
        tvTitle.translationY = -30f
        tvTitle.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setStartDelay(300)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // --- 3. Card streak: fade-in con leggero scale ---
        val cardStreak = tvTitle.parent.let {
            // Prende la card cercando il parent corretto (sibling nel LinearLayout)
            null
        }
        // Usiamo l'approccio con alpha direttamente sulla card tramite id del suo container
        val tvStreakCount = findViewById<TextView>(R.id.tvCelebStreakCount)
        tvStreakCount.alpha = 0f
        tvStreakCount.scaleX = 0.5f
        tvStreakCount.scaleY = 0.5f
        tvStreakCount.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(600)
            .setStartDelay(500)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // --- 4. Badge record: pop-in se visibile ---
        val cardRecord = findViewById<MaterialCardView>(R.id.cardNewRecord)
        if (cardRecord.visibility == View.VISIBLE) {
            cardRecord.scaleX = 0f
            cardRecord.scaleY = 0f
            val recScaleX = ObjectAnimator.ofFloat(cardRecord, View.SCALE_X, 0f, 1.2f, 1f)
            val recScaleY = ObjectAnimator.ofFloat(cardRecord, View.SCALE_Y, 0f, 1.2f, 1f)
            AnimatorSet().apply {
                playTogether(recScaleX, recScaleY)
                duration = 500
                startDelay = 900
                interpolator = AccelerateDecelerateInterpolator()
            }.start()
        }

        // --- 5. Messaggio ironico: fade-in ---
        val tvMessage = findViewById<TextView>(R.id.tvCelebMessage)
        tvMessage.alpha = 0f
        tvMessage.animate()
            .alpha(1f)
            .setDuration(600)
            .setStartDelay(700)
            .start()

        // --- 6. Emoji pioggia: TranslationY da -300dp a +screenHeight ---
        startRainAnimation()
    }

    /**
     * Anima i 6 emoji "pioggia" che cadono dall'alto verso il basso.
     * Ogni emoji ha un ritardo e una durata diversa per un effetto naturale.
     * L'animazione si ripete in loop.
     */
    private fun startRainAnimation() {
        val displayHeight = resources.displayMetrics.heightPixels.toFloat()
        val startY = -resources.displayMetrics.density * 300f  // -300dp in pixel

        data class RainConfig(
            val id: Int,
            val delay: Long,
            val duration: Long,
            val loopDelay: Long
        )

        val rainConfigs = listOf(
            RainConfig(R.id.rainEmoji1, 200,  1800, 3200),
            RainConfig(R.id.rainEmoji2, 800,  2200, 3600),
            RainConfig(R.id.rainEmoji3, 400,  2000, 2800),
            RainConfig(R.id.rainEmoji4, 1100, 1900, 3400),
            RainConfig(R.id.rainEmoji5, 600,  2400, 4000),
            RainConfig(R.id.rainEmoji6, 1400, 2100, 3800)
        )

        for (cfg in rainConfigs) {
            val view = findViewById<TextView>(cfg.id)
            view.translationY = startY
            view.alpha = 0f

            fun animateDrop() {
                view.translationY = startY
                view.alpha = 0f
                val fall = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, startY, displayHeight)
                fall.duration = cfg.duration
                fall.interpolator = AccelerateInterpolator(1.2f)
                fall.startDelay = cfg.delay

                val fadeIn = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f)
                fadeIn.duration = 300
                fadeIn.startDelay = cfg.delay

                val fadeOut = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f)
                fadeOut.duration = 300
                fadeOut.startDelay = cfg.delay + cfg.duration - 300

                val set = AnimatorSet()
                set.playTogether(fall, fadeIn, fadeOut)
                set.start()

                // Loop: riparte dopo loopDelay
                view.postDelayed({ animateDrop() }, cfg.loopDelay + cfg.delay)
            }

            animateDrop()
        }
    }
}
