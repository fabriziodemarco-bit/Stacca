package com.stacca.app.util

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import android.Manifest

/**
 * Helper per la verifica e la richiesta dei permessi necessari al corretto
 * funzionamento degli allarmi esatti su Android 12+ e dispositivi con
 * battery optimization aggressiva (Xiaomi/MIUI, Samsung, Oppo, ecc.).
 */
object PermissionHelper {

    /**
     * Controlla se l'app ha il permesso POST_NOTIFICATIONS.
     * Richiesto da API 33 (Android 13+); sotto quella versione ritorna sempre true.
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Controlla se l'app può programmare allarmi esatti tramite AlarmManager.
     * Richiesto da API 31 (Android 12+); sotto quella versione ritorna sempre true.
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * Controlla se l'app è esclusa dall'ottimizzazione batteria del sistema.
     * Fondamentale su MIUI, One UI e altri skin aggressivi.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Ritorna l'intent per aprire la schermata di impostazione allarmi esatti.
     * Solo API 31+; ritorna null sulle versioni precedenti.
     */
    fun exactAlarmSettingsIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        } else null
    }

    /**
     * Ritorna l'intent per richiedere di ignorare l'ottimizzazione batteria.
     * Usa l'intent diretto se risolvibile, altrimenti fallback alla schermata generica.
     */
    fun batteryOptimizationIntent(context: Context): Intent {
        val direct = Intent(
            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        ).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        return if (context.packageManager.resolveActivity(direct, 0) != null) {
            direct
        } else {
            // Fallback: apre la lista generale delle app con ottimizzazione batteria
            Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
    }
}
