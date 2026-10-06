# TODO — Stacca!
Backlog del progetto. Le regole che valgono sempre stanno in `CLAUDE.md`, qui ci sono solo le cose da fare.

## Da sistemare
- **Supabase in pausa**: il piano gratuito mette in pausa il progetto dopo 7 giorni senza richieste (trovato in pausa il 06/10). Intanto il login non funziona. Soluzione gratuita: un "ping" automatico ogni pochi giorni con GitHub Actions; in alternativa piano Pro (25 $/mese).
- **Email di Supabase**: il mailer integrato è solo per sviluppo (limiti stretti, mittente non nostro). Da risolvere con un SMTP personalizzato, oppure rivedendo il login via email.

## Release 2.3.0 (mar 06/10): si pubblica solo quando i punti 1-7 sono tutti fatti
Check di sicurezza già fatto: nessun segreto su GitHub, componenti esportati ok, chiavi Supabase da `local.properties`.
- [x] 1. Versione 2.3.0 (versionCode 21) e via il permesso `FOREGROUND_SERVICE` (non usato).
- [ ] 2. RLS su Supabase: nessuna tabella "RLS disabled" o "Unrestricted" (controllo manuale di Fabrizio).
- [x] 3. Informativa privacy aggiornata: storico salvato sul telefono (e nel backup Google), voce, flash, vibrazione.
- [ ] 4. Modulo "Sicurezza dei dati" su Play.
- [ ] 5. Questionario di classificazione dei contenuti (gli insulti di Insultami).
- [ ] 6. Scheda Play: testi e screenshot nuovi (Storico nuovo compreso).
- [ ] 7. Prova finale sul telefono con la versione di release: Storico, permessi, allarme, Insultami, Premium.
- [ ] 8. Pacchetto firmato (`.aab`) e caricamento su Play.

## Prezzo e paywall (dopo la release, se avanza tempo oggi; altrimenti mer 07/10, poi call Andrea ven 09/10)
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
