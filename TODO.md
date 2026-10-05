# TODO — Stacca!
Backlog del progetto. Le regole che valgono sempre stanno in `CLAUDE.md`, qui ci sono solo le cose da fare.

## Da sistemare
- **Email di Supabase**: il mailer integrato è solo per sviluppo (limiti stretti, mittente non nostro). Da risolvere con un SMTP personalizzato, oppure rivedendo il login via email.

## Da rivedere dopo la release 2.3.0
- **Schermo rosso**: ridisegnarlo nello stile maturo dell'app (proposta "Bollettino d'emergenza": sfondo bordeaux che "respira", cronometro grande, segmenti dei livelli, niente emoji che saltano).
- **Insultami free o premium?** Domanda di marketing (da fare anche ad Andrea): Insultami completo (60 s) gratis per tutti, oppure anteprima breve (20-30 s) gratis e versione completa solo con Premium?
- **Riquadro permessi senza uscita**: chi rifiuta anche un solo permesso non vede mai la home. Valutare un'uscita guardando i primi utenti.
- **Tema chiaro**: la palette chiara è già definita, ma l'app oggi ha solo il tema scuro.
- **Giorni lavorativi**: oggi il riavvio automatico vale anche sabato e domenica.
- **Più turni al giorno** (es. pranzo + sera in automatico).

## Idee future
- Onboarding con demo del livello Apocalisse.
- Statistiche "tempo recuperato".
- Widget con countdown alla fine del turno.
- Pacchetti di insulti regionali.
- Condivisione social dello streak.

## Fatto
- Ripristino automatico del premium all'avvio (commit `a025652`), verificato su telefono il 18/9/2026.
