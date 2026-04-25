package com.stacca.app.data

/**
 * Messaggi di notifica organizzati per livello di escalation.
 * Ogni livello diventa progressivamente più aggressivo e convincente.
 */
object NotificationMessages {

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

    // ---- LIVELLO 1: Gentile (0 minuti dopo l'orario) ----
    val gentleMessages = listOf(
        "Hey! È ora di staccare! Salva il lavoro e chiudi tutto 🕐",
        "Il tuo turno è finito! È ora di goderti la serata ✨",
        "Psst... l'orario di lavoro è finito. Chiudi quel portatile! 💻",
        "Promemoria gentile: la giornata lavorativa è finita! 🌅",
        "Ehi collega, è ora! Il divano ti aspetta! 🛋️"
    )

    val gentleTitles = listOf(
        "Fine turno! 🕐",
        "È l'ora! ⏰",
        "Tempo scaduto! ⌛",
        "Basta così! 👋",
        "Si stacca! 🎉"
    )

    // ---- LIVELLO 2: Amichevole con proposte seducenti (5 minuti) ----
    val friendlyMessages = listOf(
        "Dai su, chiudi tutto! Un aperitivo fresco ti aspetta fuori! 🍹",
        "Netflix ha appena aggiunto quella serie che volevi vedere... ma tu sei ancora a lavorare?! 📺",
        "Il tuo letto king-size sta piangendo perché non ci sei. Vai a consolarlo! 🛏️",
        "Fuori c'è un tramonto bellissimo. Tu te lo stai perdendo per una email. DAVVERO?! 🌅",
        "La pizza non si ordina da sola! Stacca e pensa alla cena! 🍕",
        "Il tuo partner/amico/gatto ti sta aspettando. Non farli aspettare ancora! ❤️",
        "Un gelato artigianale, una passeggiata... o un'altra riga di codice? LA SCELTA È OVVIA! 🍦"
    )

    val friendlyTitles = listOf(
        "Ehi, ancora qui?! 🍷",
        "C'è vita fuori dal PC! 🌍",
        "PROPOSTE INDECENTI 😏",
        "Hai alternative migliori! 🎭",
        "Il mondo ti chiama! 📱"
    )

    // ---- LIVELLO 3: Insistente (10 minuti) ----
    val insistentMessages = listOf(
        "SERIAMENTE?! Sono passati 10 MINUTI! Il tuo capo non ti paga gli straordinari! 💸",
        "Il tuo corpo ha bisogno di muoversi! Hai le vertebre che sembrano un pretzel! 🥨",
        "ALLERTA: Livello di workaholic pericolosamente alto! Stacca ADESSO! ⚠️",
        "I tuoi occhi stanno per sindacalizzarsi. Datti una pausa! 👀",
        "10 minuti di straordinario GRATIS. Sei un benefattore dell'azienda o cosa?! 🤡",
        "Il tuo schermo ha più luce del sole. Esci. Adesso. SUBITO. ☀️",
        "Stai regalando il tuo tempo libero. IL TUO. TEMPO. LIBERO. 🕐💢"
    )

    val insistentTitles = listOf(
        "ANCORA?! 😤",
        "MA DAI!! 🙄",
        "STACCA!!! ⚡",
        "BASTA!! 🛑",
        "NON CI CREDO! 😱"
    )

    // ---- LIVELLO 4: Aggressivo (15 minuti) ----
    val aggressiveMessages = listOf(
        "MA CHE STAI FACENDO?! 15 minuti gratis al tuo capo! Sei impazzito/a?! 🤬",
        "Il tuo work-life balance è più sbilanciato della Torre di Pisa! STACCA! 🏗️",
        "I tuoi colleghi sono già al bar. Tu sei ancora a fissare Excel. Ripensaci. 🍺",
        "BREAKING NEWS: Dipendente ostinato si rifiuta di smettere di lavorare. I familiari sono preoccupati! 📰",
        "Il burnout non è un traguardo da raggiungere! CHIUDI TUTTO! 🔥",
        "Anche le macchine hanno bisogno di fermarsi. Tu non sei nemmeno una macchina! 🤖",
        "ATTENZIONE: Ogni minuto extra è un minuto rubato alla tua vita! STACCA ORA! ⏳💀"
    )

    val aggressiveTitles = listOf(
        "SEI ANCORA QUI?! 🤬",
        "ORA BASTA DAVVERO! 💢",
        "ULTIMO AVVISO! ⚠️",
        "EMERGENZA! 🚨",
        "NON SCHERZARE! 😡"
    )

    // ---- LIVELLO 5: Nucleare (20 minuti) ----
    val nuclearMessages = listOf(
        "STACCAAAAA! 20 MINUTI! Vuoi che venga lì a chiuderti il portatile?! 💀",
        "Il tuo computer sta per autodistruggersi in 3... 2... 1... (MAGARI!) 💣",
        "NOTIZIA FLASH: Nessuno è mai morto per aver chiuso il laptop in orario! 📢",
        "Stai diventando un caso clinico! I dottori lo chiamano 'WORKAHOLIC TERMINALE'! 🏥",
        "Il tuo gatto ha mangiato la tua cena perché non sei tornato/a. Complimenti! 🐱",
        "HAI PRESENTE QUEL CONCETTO CHIAMATO 'VITA PRIVATA'?! ECCOLO, TI ASPETTA FUORI! 🚪",
        "MISSIONE: Chiudi tutto, alzati dalla sedia, e VAI A VIVERE! Ripeto: VAI A VIVERE! 🎯"
    )

    val nuclearTitles = listOf(
        "💀 BASTA!!! 💀",
        "CODICE ROSSO! 🚨",
        "ALLARME TOTALE! 🔴",
        "GAME OVER! 💣",
        "SOS WORKAHOLIC! 🆘"
    )

    // ---- LIVELLO 6: Apocalisse (30+ minuti) ----
    val apocalypseMessages = listOf(
        "30 MINUTI. TRENTA. Ti sei trasferito in ufficio?! Vuoi che ti mandi le lenzuola?! ☠️",
        "A questo punto dormi pure lì! Tanto non hai una vita sociale! 🏢🛏️",
        "CONGRATULAZIONI: Hai vinto il premio 'Dipendente più sfruttato dell'anno'! 🏆",
        "Il tuo capo sta ridendo di te. RIDENDO. Mentre tu lavori gratis! 😂💸",
        "La tua sedia ha più impronta del tuo divano. Questo dice tutto! 🪑",
        "ULTIMO MESSAGGIO: Se non stacchi ORA, domani troverai l'app che ti insulta in 47 lingue! 🌍🤬",
        "Sei un caso perso. Ma ti vogliamo bene. Stacca per favore. STACCA! 💔"
    )

    val apocalypseTitles = listOf(
        "☠️ SEI IRRECUPERABILE ☠️",
        "CASO DISPERATO! 😱",
        "AIUTO!!! 🆘",
        "INTERVENTO NECESSARIO! 🏥",
        "ADDIO SANITÀ MENTALE! 🧠💥"
    )

    /**
     * Restituisce un titolo e messaggio casuali per il livello dato.
     */
    fun getRandomMessage(level: Level): Pair<String, String> {
        val (titles, messages) = when (level) {
            Level.GENTLE -> gentleTitles to gentleMessages
            Level.FRIENDLY -> friendlyTitles to friendlyMessages
            Level.INSISTENT -> insistentTitles to insistentMessages
            Level.AGGRESSIVE -> aggressiveTitles to aggressiveMessages
            Level.NUCLEAR -> nuclearTitles to nuclearMessages
            Level.APOCALYPSE -> apocalypseTitles to apocalypseMessages
        }
        return titles.random() to messages.random()
    }

    /**
     * Determina il livello in base ai minuti trascorsi dalla fine turno.
     */
    fun getLevelForMinutes(minutesOvertime: Int): Level {
        return when {
            minutesOvertime < 5 -> Level.GENTLE
            minutesOvertime < 10 -> Level.FRIENDLY
            minutesOvertime < 15 -> Level.INSISTENT
            minutesOvertime < 20 -> Level.AGGRESSIVE
            minutesOvertime < 30 -> Level.NUCLEAR
            else -> Level.APOCALYPSE
        }
    }
}
