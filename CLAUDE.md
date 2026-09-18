# CLAUDE.md — Stacca!

## Cos'è
App Android per chi lavora in smart working e non riesce a staccare. Imposti l'orario di fine turno e, da quel momento, l'app manda notifiche **sempre più aggressive e ironiche** (6 livelli: Gentile → Amichevole → Insistente → Aggressivo → Nucleare → Apocalisse) finché non stacchi.
Tagline: *"Basta lavorare. Vivi."*

## Stato attuale
- **Pubblicata in produzione** su Google Play: ci sono utenti reali, quindi ogni rilascio va testato con cura.
- Versione **2.2.0 (versionCode 20)**, inviata a Google Play il 7/9/2026.
- Repo: GitHub `fabriziodemarco-bit/Stacca`, branch `master`.
- Privacy policy e pagina eliminazione account: `privacy_policy.html` e `delete_account.html` (pubblicate su GitHub Pages).

## Stack
- Kotlin, **Activity classiche + layout XML + ViewBinding** (niente Jetpack Compose).
- Material Design 3, tema scuro. minSdk 26, targetSdk/compileSdk 36.
- Storage: SharedPreferences, in **due file**: `stacca_prefs` (dati normali) e `stacca_license` (premium/trial).
- Allarmi: AlarmManager. Notifiche: NotificationCompat con canali.
- Auth: Supabase (login Google ed email), **opzionale**.
- Pagamenti: Google Play Billing 8.x, prodotto one-time `stacca_premium`.
- Lingue: italiano (base, `values/`), inglese (`values-en/`), cinese semplificato (`values-zh-rCN/`).

## File principali (`app/src/main/java/com/stacca/app/`)
- `billing/BillingManager.kt`: acquisti e ripristino premium
- `auth/AuthManager.kt`, `data/SupabaseConfig.kt`: login Supabase
- `data/PreferencesManager.kt`: preferenze, licenza, trial, streak, migrazione
- `data/NotificationMessages.kt`: i testi di ogni livello di escalation
- `notifications/`: `NotificationHelper`, `AlarmSoundManager` (suono allarme centralizzato)
- `receivers/`: `AlarmReceiver`, `BootReceiver`, `NotificationActionReceiver`
- `ui/`: `SplashActivity`, `MainActivity`, `SettingsActivity`, `LoginActivity`, `PaywallActivity`, `PremiumActivity`, `TrialExpiredActivity`, `FullScreenAlertActivity`
- `util/PermissionHelper.kt`: permessi notifiche, allarmi esatti, batteria

## Decisioni da NON rompere
- **Il premium dipende solo da Google Play**, mai dal login. Il logout non deve togliere il premium.
- **La licenza sta in `stacca_license`**: "Cancella dati" azzera solo `stacca_prefs`, così il trial non si aggira.
- **Freemium**: i livelli 1-3 sono gratis; i livelli 4-6 e l'allarme a schermo intero sono premium.
- **Permessi rimossi per le policy di Google Play**: `USE_EXACT_ALARM`, `SYSTEM_ALERT_WINDOW`, `USE_FULL_SCREEN_INTENT`. Non reintrodurli senza parlarne prima.
- **Ripristino automatico del premium** all'avvio (commit `a025652`), verificato sul telefono il 18/9/2026 con disinstallazione e reinstallazione da Play. Se modifichi `BillingManager` o `MainActivity`, ripeti questo test.
- Gli allarmi esatti funzionano con il permesso runtime `SCHEDULE_EXACT_ALARM` più l'esenzione dall'ottimizzazione batteria (senza, sui telefoni reali le notifiche non partono).

## Regole specifiche del progetto
- **Tono sempre ironico**, diretto, sfottò affettuoso. Mai serio, mai corporate.
- **Ogni nuovo testo UI va in tutte e 3 le lingue** (`values`, `values-en`, `values-zh-rCN`), mai scritto a mano nel codice.
- Non cambiare nomi di classi o file senza richiesta esplicita.
- Nessuna dipendenza nuova senza chiedere.
- Colori: sfondo blu scuro (~`#1a1a2e`), accento arancione (~`#FF6B35`), rosso pieno per l'allarme attivo.

## Segreti (mai nel codice, mai su git)
`local.properties` contiene le chiavi Supabase, il Google Web Client ID e la password del keystore. `stacca-upload.jks` e `stacca-upload.pem` sono esclusi dal `.gitignore`. Non leggere né stampare questi valori.

## Build, test, rilascio
- Debug: `.\gradlew.bat assembleDebug`. Release: `.\gradlew.bat bundleRelease` (AAB firmato).
- Test sul telefono: Samsung Galaxy A55.
- **Il billing si testa solo con l'app scaricata da Play** (test chiuso), non con una build installata via cavo.
- Per un nuovo rilascio: aumentare `versionCode` e `versionName` in `app/build.gradle.kts`, generare l'AAB e caricarlo in Play Console.

## Da fare
- **Email di Supabase**: il mailer integrato è solo per sviluppo. Da sistemare (SMTP personalizzato, oppure rivedere il login via email).
- Idee future: onboarding con demo dell'Apocalisse, statistiche "tempo recuperato", widget con countdown, pacchetti di insulti regionali, condivisione social dello streak.

> Nota: `PROGETTO.md` è la vecchia versione di queste regole (ferma alla v1). Il riferimento aggiornato è questo file.
