package com.stacca.app.data
import com.stacca.app.BuildConfig

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Configurazione del client Supabase.
 *
 * ⚠️ IMPORTANTE: Sostituisci SUPABASE_URL e SUPABASE_ANON_KEY
 * con i valori dal tuo progetto Supabase (https://supabase.com/dashboard).
 *
 * Per maggiore sicurezza in produzione, considera di usare BuildConfig fields
 * definiti in build.gradle.kts per non committare le chiavi nel repository.
 */
object SupabaseConfig {

    // URL del progetto Supabase
    private const val SUPABASE_URL = BuildConfig.SUPABASE_URL

    // Chiave pubblica (anon key) — sicura per uso client-side
    private const val SUPABASE_ANON_KEY = BuildConfig.SUPABASE_ANON_KEY

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Auth) {
            scheme = "stacca"
            host = "login-callback"
        }
        install(Postgrest)
    }
}
