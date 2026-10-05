# TODO — Stacca!
Backlog del progetto. Le regole che valgono sempre stanno in `CLAUDE.md`, qui ci sono solo le cose da fare.

## Da sistemare
- **Email di Supabase**: il mailer integrato è solo per sviluppo (limiti stretti, mittente non nostro). Da risolvere con un SMTP personalizzato, oppure rivedendo il login via email.

## Prima della release 2.3.0 (mar 06/10)
- **Riquadro permessi più bello** (proposta approvata da rivedere al mattino): titolo grande "Dammi tre sì. / Al resto penso io." + riga "Senza questi non posso venirti a cercare."; barra a 3 segmenti "0 di 3" che si accende in verde; un passo alla volta (solo il prossimo tasto arancio pieno, fatti = "✓ Fatto" verde, successivi spenti); icone campanella/orologio/batteria; testi: "Per venirti a cercare quando è ora." / "Per essere puntuale. Almeno io." / "Così Android non mi addormenta mentre tu lavori."; a 3 su 3 un attimo di festa "Ci siamo. Ora decidi quando si stacca." poi home; riquadro più in alto. 3 lingue.
- **Check di sicurezza**: nessun segreto nel codice o su GitHub, permessi del manifest (solo quelli usati), componenti esportati, chiavi Supabase e regole di accesso ai dati.
- **Privacy e burocrazia**: informativa privacy aggiornata alle funzioni nuove (storico salvato sul telefono, voce, flash, vibrazione), modulo "Sicurezza dei dati" su Play, questionario di classificazione dei contenuti (gli insulti), scheda Play (testi e screenshot nuovi).

## Prezzo e paywall (da valutare insieme mer 07/10)
- Prezzo: oggi 1,19 € una tantum, proposta 2,99 €. Si cambia dalla Play Console, senza nuova versione dell'app.
- Paywall: ripassare dove compare oggi (badge "Passa a Premium", riquadro nello storico, riga nella notifica del livello 3, fine prova gratuita) e se aggiungerne altri. Le modifiche ai punti di ingresso richiedono una nuova versione: dopo la call con Andrea.

## Da rivedere dopo la release 2.3.0
- **Schermo rosso**: ridisegnarlo nello stile maturo dell'app (proposta "Bollettino d'emergenza": sfondo bordeaux che "respira", cronometro grande, segmenti dei livelli, niente emoji che saltano).
- **Insultami free o premium?** Domanda di marketing (da fare anche ad Andrea): Insultami completo (60 s) gratis per tutti, oppure anteprima breve (20-30 s) gratis e versione completa solo con Premium?
- **Riquadro permessi senza uscita**: chi rifiuta anche un solo permesso non vede mai la home. Valutare un'uscita guardando i primi utenti.
- **Tema chiaro**: la palette chiara è già definita, ma l'app oggi ha solo il tema scuro.
- **Giorni lavorativi**: oggi il riavvio automatico vale anche sabato e domenica.
- **Più turni al giorno** (es. pranzo + sera in automatico).
- **"Inizio giornata" (candidata per la 2.4)**: per chi ha orari mobili (smart working, partite IVA, commerciali). Si preme "Inizio" e si sceglie una durata (es. 8 ore): Stacca calcola da solo quando staccare, e lo storico mostra le ore lavorate. Prima di farla, chiedere ad Andrea e ai primi utenti: "il tuo orario è fisso o ti serve contare le ore?". Domande aperte: cosa succede se ci si dimentica di premere "Inizio", se i due modi convivono, come gestire pause e pranzo.

## Idee future
- Onboarding con demo del livello Apocalisse.
- Statistiche "tempo recuperato".
- Widget con countdown alla fine del turno.
- Pacchetti di insulti regionali.
- Condivisione social dello streak.

## Fatto
- Ripristino automatico del premium all'avvio (commit `a025652`), verificato su telefono il 18/9/2026.
