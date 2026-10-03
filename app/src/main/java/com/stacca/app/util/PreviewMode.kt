package com.stacca.app.util

import android.content.Intent
import com.stacca.app.BuildConfig

/**
 * Anteprima delle schermate, SOLO nella versione di prova (debug).
 * Nella versione pubblicata su Play BuildConfig.DEBUG è false e l'anteprima non esiste.
 * In anteprima le schermate si mostrano anche se l'utente è Premium e non modificano dati.
 */
object PreviewMode {
    const val EXTRA = "extra_preview"

    fun isOn(intent: Intent?): Boolean =
        BuildConfig.DEBUG && intent?.getBooleanExtra(EXTRA, false) == true
}
