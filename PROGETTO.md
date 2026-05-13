Stacca! — Regole del progetto per l'AI
Identità dell'app

Nome: Stacca!
Tagline: "Basta lavorare. Vivi."
Scopo: App Android per chi lavora in smart working e non riesce a staccare. Imposti un orario di fine turno e l'app ti bombarda di notifiche sempre più aggressive finché non smetti.
Tono: Ironico, diretto, sfottò affettuoso. Mai serio. Mai corporate. Esempi: "SOS Workaholic 💀", "Il tuo gatto ha mangiato la tua cena 🐱", "Vuoi che venga a chiuderti il portatile?!"


Tech Stack

Linguaggio: Kotlin
UI: Material Design 3, tema scuro
Min SDK: 26 (Android 8.0) — Target SDK: 35
Storage: SharedPreferences con JSON
Allarmi: AlarmManager
Notifiche: NotificationCompat con canali
IDE originale: Google Antigravity (fork VS Code, agenti Gemini)


Colori principali

Sfondo: blu scuro (#1a1a2e circa)
Accent principale: arancione (#FF6B35 circa)
Schermata allarme attivo / SOS: rosso pieno
Testi: bianco su scuro


Architettura — file principali
com.stacca.app/
├── data/
│   ├── NotificationMessages.kt    ← messaggi per ogni livello escalation
│   ├── PreferencesManager.kt      ← storage preferenze e log
│   └── WorkLog.kt                 ← modello dati
├── notifications/
│   └── NotificationHelper.kt      ← gestione notifiche
├── receivers/
│   ├── AlarmReceiver.kt           ← trigger allarmi
│   ├── BootReceiver.kt            ← ripristino dopo riavvio
│   └── NotificationActionReceiver.kt
├── ui/
│   ├── MainActivity.kt            ← schermata principale
│   ├── ProgressActivity.kt        ← registrazione progressi
│   ├── FullScreenAlertActivity.kt ← allarme a schermo intero (schermata rossa)
│   ├── HistoryActivity.kt         ← storico + statistiche
│   ├── SettingsActivity.kt        ← impostazioni
│   └── adapter/WorkLogAdapter.kt
└── StaccaApplication.kt

Schermate completate (v1)

MainActivity — orologio in tempo reale, card "Fine Turno", bottone Attiva/Disattiva allarme, link a Storico e Registra Progressi
FullScreenAlertActivity — schermata rossa a schermo intero, escalation messaggi, contatore straordinario, bottone "OK, STACCO! ✋"
ProgressActivity — registra cosa hai fatto oggi, cosa fai domani, umore con emoji
HistoryActivity — storico registrazioni + statistiche (giorni totali, straordinario medio, umore frequente)
SettingsActivity — suono, vibrazione, allarme schermo intero, velocità escalation, cancellazione dati


Sistema di escalation (6 livelli)
LivelloRitardoStile🟢 Gentile0 minNotifica normale🟡 Amichevole+5 minProposta seducente🟠 Insistente+10 minPressione🔴 Aggressivo+15 minRimprovero💀 Nucleare+20 minSchermo intero☠️ Apocalisse+30 minSchermo intero totale

Impostazioni disponibili

Suono allarme on/off
Vibrazione on/off
Allarme schermo intero on/off
Velocità escalation: Rilassato / Normale / Aggressivo
Riavvio automatico giornaliero
Cancellazione dati


Regole per l'AI — LEGGERE SEMPRE

Prima di toccare qualsiasi cosa, di' in 3 punti cosa intendi modificare e aspetta conferma.
Tocca solo il file strettamente necessario — non riscrivere file che non c'entrano.
Mantieni sempre il tono ironico nei messaggi all'utente — niente frasi serie o motivazionali generiche.
Non cambiare i nomi delle classi o dei file senza esplicita richiesta.
Non aggiungere dipendenze nuove senza chiedere prima.
Se qualcosa non funziona, spiega il problema prima di proporre una soluzione.
L'app è in italiano — tutti i testi UI restano in italiano.