# 🚨 Stacca! - L'app che ti obbliga a smettere di lavorare

<p align="center">
  <strong>Basta lavorare. Vivi.</strong>
</p>

---

## 📱 Cos'è Stacca!?

**Stacca!** è un'app Android pensata per chi lavora in smart working e ha il brutto vizio di non staccare mai dal lavoro. 

Imposti un orario di fine turno e, quando arriva quel momento, l'app comincia a bombardarti di notifiche **sempre più aggressive** finché non smetti di lavorare!

## 🎯 Funzionalità

### ⏰ Sistema di Allarme Intelligente
- Imposta l'orario di fine turno con un picker elegante
- Countdown in tempo reale verso la fine della giornata
- L'allarme si riprogramma automaticamente ogni giorno

### 📢 Escalation delle Notifiche (6 livelli!)

| Livello | Tempo | Stile | Esempio |
|---------|-------|-------|---------|
| 🟢 Gentile | 0 min | Promemoria carino | *"Hey! È ora di staccare!"* |
| 🟡 Amichevole | +5 min | Proposte seducenti | *"Un aperitivo ti aspetta fuori! 🍹"* |
| 🟠 Insistente | +10 min | Pressione | *"I tuoi occhi stanno per sindacalizzarsi!"* |
| 🔴 Aggressivo | +15 min | Rimprovero | *"Il burnout non è un traguardo!"* |
| 💀 Nucleare | +20 min | Schermo intero! | *"Vuoi che venga a chiuderti il portatile?!"* |
| ☠️ Apocalisse | +30 min | TOTALE | *"Hai vinto il premio Dipendente più sfruttato!"* |

### 📝 Registro Progressi
- Registra cosa hai completato oggi
- Annota cosa devi fare domani
- Seleziona il tuo umore con emoji
- Storico completo consultabile

### 📊 Statistiche
- Giorni totali registrati
- Straordinario totale e media giornaliera
- Umore più frequente
- Badge straordinario su ogni registro

### ⚙️ Impostazioni
- Attiva/disattiva suono allarme
- Attiva/disattiva vibrazione
- Attiva/disattiva allarme a schermo intero
- Velocità escalation configurabile (Rilassato / Normale / Aggressivo)
- Riavvio automatico giornaliero
- Cancellazione dati

### 🎨 Design Premium
- Tema scuro elegante con Material Design 3
- Animazioni fluide e micro-interazioni
- UI interamente in italiano

## 🏗️ Architettura

```
com.stacca.app/
├── data/
│   ├── NotificationMessages.kt    # Messaggi per ogni livello
│   ├── PreferencesManager.kt      # Storage preferenze e log
│   └── WorkLog.kt                 # Modello dati
├── notifications/
│   └── NotificationHelper.kt      # Gestione notifiche
├── receivers/
│   ├── AlarmReceiver.kt           # Trigger allarmi
│   ├── BootReceiver.kt            # Ripristino dopo riavvio
│   └── NotificationActionReceiver.kt
├── ui/
│   ├── MainActivity.kt            # Schermata principale
│   ├── ProgressActivity.kt        # Registrazione progressi
│   ├── FullScreenAlertActivity.kt  # Allarme a schermo intero
│   ├── HistoryActivity.kt         # Storico lavoro + statistiche
│   ├── SettingsActivity.kt        # Impostazioni
│   └── adapter/
│       └── WorkLogAdapter.kt
└── StaccaApplication.kt
```

## 🛠️ Tech Stack

- **Linguaggio**: Kotlin
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 35
- **UI**: Material Design 3
- **Storage**: SharedPreferences con JSON
- **Allarmi**: AlarmManager
- **Notifiche**: NotificationCompat con canali

## 🚀 Come iniziare

1. Clona il repository
2. Apri il progetto in Android Studio
3. Compila e installa sul tuo dispositivo
4. Imposta l'orario di fine turno
5. Attiva l'allarme
6. **STACCA!** 🎉

## 📄 Licenza

MIT License - Usa pure l'app, ma ricordati di staccare! 😉

---

<p align="center">
  <em>Fatto con ❤️ e un po' di cattiveria da chi lavora troppo</em>
</p>
