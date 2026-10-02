package com.stacca.app.util

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Da Android 15 (targetSdk 35+) le app vengono disegnate a tutto schermo,
 * anche sotto la barra di stato e i tasti di navigazione.
 * Questo helper aggiunge al contenuto lo spazio occupato dalle barre di sistema,
 * così niente finisce coperto. Va chiamato subito dopo setContentView().
 */
object SystemBarsHelper {

    fun applyInsets(activity: Activity) {
        val content = activity.findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }
}
