# TODO — Stacca!
Backlog del progetto. Le regole che valgono sempre stanno in `CLAUDE.md`, qui ci sono solo le cose da fare.

## Da sistemare
- ~~**Supabase in pausa**~~ RISOLTO il 06/10: sveglia automatica ogni 3 giorni (GitHub Actions "Supabase sveglia" legge la tabella `keepalive`). Se GitHub manda una mail perché il repository è fermo da 60 giorni, riattivarla dalla scheda Actions.
- ~~**Email di Supabase**~~ RISOLTO il 07/10: SMTP personalizzato con Gmail (mittente staccalapp@gmail.com, nome "Stacca!", password per le app salvata solo in Supabase). Registrazione via email provata: la mail arriva. Da fare: testo della mail di conferma in italiano nello stile di Stacca.

## Release 2.3.0 (mar 06/10): INVIATA IN REVISIONE, in attesa di Google
Check di sicurezza già fatto: nessun segreto su GitHub, componenti esportati ok, chiavi Supabase da `local.properties`.
- [x] 1. Versione 2.3.0 (versionCode 21) e via il permesso `FOREGROUND_SERVICE` (non usato).
- [x] 2. RLS su Supabase: nessuna tabella "RLS disabled" o "Unrestricted" (controllo manuale di Fabrizio).
- [x] 3. Informativa privacy aggiornata: storico salvato sul telefono (e nel backup Google), voce, flash, vibrazione.
- [x] 4. Modulo "Sicurezza dei dati" su Play (salvato, parte in revisione con la release).
- [x] 5. Questionario di classificazione dei contenuti: 3+ ovunque (insulti scherzosi, nessuna parolaccia vera).
- [x] 6. Scheda Play: testi nuovi (scheda_play.md), 5 screenshot, icona 512 e immagine in primo piano nuove. Inviata in revisione con la release.
- [x] 7. Prova finale sulla release (06/10): permessi e festa, allarme e livello 2, Ho staccato, Storico, Insultami, accesso Google, Elimina account, Premium ripristinato da solo. Tutto ok.
- [x] 8. Release 21 (2.3.0) caricata in PRODUZIONE e inviata in revisione il 06/10 sera (risolve anche l'avviso "target API 36").

## Prezzo e paywall (06/10 sera)
- Decisione: 7 giorni di prova completa e visibile, poi gratis per sempre (livelli 1-3 + Insultami), Premium 2,99 € una tantum. Niente abbonamento per ora.
- [x] Prezzo 2,99 € in Play Console (Fabrizio).
- [ ] Screenshot paywall con 4 voci e 2,99 €: `screenshot_play/play/05_paywall_2-99.png` (da caricare sulla scheda Play al posto del vecchio). In corso il 07/10.
- [ ] Fabrizio reinstalla Stacca dallo Store sul Samsung (oggi c'è la build firmata dal PC, che non riceve aggiornamenti). In corso il 07/10.
- [x] Release 22 (2.3.1) APPROVATA e online (07/10): prova visibile in home, Impostazioni sbloccate in prova, riepilogo a fine prova, Storico completo tra le voci Premium, titolo home che non va più a capo. Testata sul Samsung il 06/10.
- [ ] Dopo l'approvazione: controllare insieme la scheda Play in tutte le lingue (immagini in primo piano IT/EN/ZH, screenshot nelle schede EN e ZH ancora in italiano), poi caricare lo screenshot del paywall nuovo.
- [x] Festa "Ci siamo.": si vedeva solo un attimo; ora dura 4 secondi e un tocco la salta (07/10, da provare sul telefono).
- [x] Premium e prova più chiari (07/10, provati sul Samsung con "Simula piano"): badge PREMIUM in home, "Sblocca tutto" arancione, sezione Piano ed etichette PREMIUM nelle Impostazioni, paywall diretto senza finestra, lucchetto a linee nello Storico, sottotitolo paywall neutro.
- [x] Errori di accesso e registrazione: messaggi comprensibili IT/EN/ZH al posto del testo tecnico, attesa 30 s (07/10, da provare sul telefono).
- [x] **Bug serie doppia** (07/10, corretto e provato sul Samsung): dopo "Ho staccato", se si disattiva l'allarme la stessa sera lo stacco viene registrato una seconda volta (serie 1 → 2 giorni, "Nuovo record!" falso). Da correggere: in disattivazione registrare solo se oggi non si è già staccato.
- [x] Immagini Store EN/IT/ZH fatte (07/10): `screenshot_play/play_en`, `play_it`, `play_zh` (8 screenshot + immagine in primo piano ciascuna). Da caricare su Play come bozza.

## Da rivedere dopo la release 2.3.0
- **Contatore "promemoria inviati"**: nel test del 06/10 segnava 3/6 dopo 9 minuti con avvisi ogni 10 min. Probabilmente colpa delle chiusure forzate e reinstallazioni durante il test: verificare con un turno pulito, contando le notifiche.
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
