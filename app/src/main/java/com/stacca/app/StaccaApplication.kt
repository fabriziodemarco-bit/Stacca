package com.stacca.app

import android.app.Application

/**
 * Classe Application per l'inizializzazione globale dell'app.
 */
class StaccaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Inizializzazione globale se necessario
    }
}
