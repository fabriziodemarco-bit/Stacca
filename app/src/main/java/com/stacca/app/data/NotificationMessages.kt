package com.stacca.app.data

import android.content.Context
import com.stacca.app.R

/**
 * Messaggi di notifica organizzati per livello di escalation.
 * Ogni livello diventa progressivamente più aggressivo e convincente.
 *
 * I messaggi sono caricati dalle risorse XML (res/values/notification_messages.xml
 * e relative cartelle per lingua) in modo che Android serva automaticamente
 * la lingua corretta del dispositivo.
 *
 * I messaggi sono selezionati in modo SEQUENZIALE (round-robin) per evitare
 * ripetizioni consecutive. L'indice è gestito da PreferencesManager.
 */
class NotificationMessages(private val context: Context) {

    /**
     * Livello di escalation delle notifiche.
     */
    enum class Level(val delayMinutes: Int, val emoji: String) {
        GENTLE(0, "🕐"),
        FRIENDLY(5, "🍷"),
        INSISTENT(10, "😤"),
        AGGRESSIVE(15, "🤬"),
        NUCLEAR(20, "💀"),
        APOCALYPSE(30, "☠️")
    }

    // ---- Caricamento lazy delle liste dalle risorse XML ----

    private val gentleTitles: List<String> by lazy {
        context.resources.getStringArray(R.array.notif_gentle_titles).toList()
    }
    private val gentleMessages: List<String> by lazy {
        context.resources.getStringArray(R.array.notif_gentle_messages).toList()
    }

    private val friendlyTitles: List<String> by lazy {
        context.resources.getStringArray(R.array.notif_friendly_titles).toList()
    }
    private val friendlyMessages: List<String> by lazy {
        context.resources.getStringArray(R.array.notif_friendly_messages).toList()
    }

    private val insistentTitles: List<String> by lazy {
        context.resources.getStringArray(R.array.notif_insistent_titles).toList()
    }
    private val insistentMessages: List<String> by lazy {
        context.resources.getStringArray(R.array.notif_insistent_messages).toList()
    }

    private val aggressiveTitles: List<String> by lazy {
        context.resources.getStringArray(R.array.notif_aggressive_titles).toList()
    }
    private val aggressiveMessages: List<String> by lazy {
        context.resources.getStringArray(R.array.notif_aggressive_messages).toList()
    }

    private val nuclearTitles: List<String> by lazy {
        context.resources.getStringArray(R.array.notif_nuclear_titles).toList()
    }
    private val nuclearMessages: List<String> by lazy {
        context.resources.getStringArray(R.array.notif_nuclear_messages).toList()
    }

    private val apocalypseTitles: List<String> by lazy {
        context.resources.getStringArray(R.array.notif_apocalypse_titles).toList()
    }
    private val apocalypseMessages: List<String> by lazy {
        context.resources.getStringArray(R.array.notif_apocalypse_messages).toList()
    }

    // ---- API pubblica ----

    /**
     * Restituisce un titolo e messaggio casuali per il livello dato.
     * NOTA: Usato per retrocompatibilità (FullScreenAlertActivity).
     * Per le notifiche, usare getSequentialMessage().
     */
    fun getRandomMessage(level: Level): Pair<String, String> {
        val (titles, messages) = getTitlesAndMessages(level)
        return titles.random() to messages.random()
    }

    /**
     * Restituisce titolo e messaggio in modo SEQUENZIALE (round-robin) per il livello dato.
     * L'indice avanza di 1 ad ogni chiamata, garantendo che non si ripeta lo stesso
     * messaggio finché tutti quelli del livello non sono stati usati.
     *
     * @param level    Livello di escalation corrente.
     * @param index    Indice corrente (da PreferencesManager.currentMessageIndex).
     * @return Triple(titolo, messaggio, nuovo indice da salvare).
     */
    fun getSequentialMessage(level: Level, index: Int): Triple<String, String, Int> {
        val (titles, messages) = getTitlesAndMessages(level)
        val titleIndex = index % titles.size
        val messageIndex = index % messages.size
        val nextIndex = index + 1
        return Triple(titles[titleIndex], messages[messageIndex], nextIndex)
    }

    private fun getTitlesAndMessages(level: Level): Pair<List<String>, List<String>> {
        return when (level) {
            Level.GENTLE     -> gentleTitles to gentleMessages
            Level.FRIENDLY   -> friendlyTitles to friendlyMessages
            Level.INSISTENT  -> insistentTitles to insistentMessages
            Level.AGGRESSIVE -> aggressiveTitles to aggressiveMessages
            Level.NUCLEAR    -> nuclearTitles to nuclearMessages
            Level.APOCALYPSE -> apocalypseTitles to apocalypseMessages
        }
    }

    companion object {
        /**
         * Determina il livello in base allo step di escalation.
         * Ogni notifica avanza di 1 step, indipendentemente dal tempo reale.
         * Metodo statico — non richiede Context.
         */
        fun getLevelForStep(step: Int): Level {
            return when {
                step <= 0 -> Level.GENTLE
                step == 1 -> Level.FRIENDLY
                step == 2 -> Level.INSISTENT
                step == 3 -> Level.AGGRESSIVE
                step == 4 -> Level.NUCLEAR
                else      -> Level.APOCALYPSE
            }
        }

        /**
         * Determina il livello in base ai minuti trascorsi dalla fine turno.
         * NOTA: Mantenuto per retrocompatibilità. La logica principale ora usa getLevelForStep().
         */
        fun getLevelForMinutes(minutesOvertime: Int): Level {
            return when {
                minutesOvertime < 5  -> Level.GENTLE
                minutesOvertime < 10 -> Level.FRIENDLY
                minutesOvertime < 15 -> Level.INSISTENT
                minutesOvertime < 20 -> Level.AGGRESSIVE
                minutesOvertime < 30 -> Level.NUCLEAR
                else                 -> Level.APOCALYPSE
            }
        }
    }
}
