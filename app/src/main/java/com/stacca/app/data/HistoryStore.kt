package com.stacca.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Storico degli stacchi: un diario giorno per giorno, salvato nelle preferenze
 * (in "stacca_prefs", quindi "Cancella dati" lo azzera insieme al resto).
 * Tiene al massimo [MAX_ENTRIES] stacchi.
 */
class HistoryStore(context: Context) {

    /** Uno stacco: quando, a che ora finiva il turno, quanti minuti di ritardo, livello raggiunto (0-6). */
    data class Entry(
        val timestampMillis: Long,
        val endHour: Int,
        val endMinute: Int,
        val overtimeMinutes: Int,
        val level: Int
    ) {
        val isOnTime: Boolean
            get() = overtimeMinutes <= PreferencesManager.ON_TIME_THRESHOLD_MINUTES
    }

    companion object {
        private const val KEY = "history_json"
        private const val MAX_ENTRIES = 365
    }

    private val prefs = context.getSharedPreferences("stacca_prefs", Context.MODE_PRIVATE)

    /** Tutti gli stacchi, dal più recente al più vecchio. */
    fun all(): List<Entry> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Entry(
                    timestampMillis = o.getLong("t"),
                    endHour = o.getInt("h"),
                    endMinute = o.getInt("m"),
                    overtimeMinutes = o.getInt("o"),
                    level = o.getInt("l")
                )
            }.sortedByDescending { it.timestampMillis }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(entry: Entry) {
        val entries = (listOf(entry) + all()).take(MAX_ENTRIES)
        val array = JSONArray()
        entries.forEach {
            array.put(
                JSONObject()
                    .put("t", it.timestampMillis)
                    .put("h", it.endHour)
                    .put("m", it.endMinute)
                    .put("o", it.overtimeMinutes)
                    .put("l", it.level)
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }
}
