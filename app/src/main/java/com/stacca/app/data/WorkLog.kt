package com.stacca.app.data

/**
 * Rappresenta un registro di lavoro giornaliero.
 */
data class WorkLog(
    val date: String,          // Data in formato "yyyy-MM-dd"
    val time: String,          // Ora della registrazione "HH:mm"
    val completed: String,     // Lavoro completato
    val todo: String,          // Lavoro da fare domani
    val mood: String,          // Emoji dell'umore
    val overtimeMinutes: Int   // Minuti di straordinario
)
