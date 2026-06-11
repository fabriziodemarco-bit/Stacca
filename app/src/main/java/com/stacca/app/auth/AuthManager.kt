package com.stacca.app.auth

import android.content.Context
import android.util.Log
import com.stacca.app.data.PreferencesManager
import com.stacca.app.data.SupabaseConfig
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gestisce autenticazione e stato premium dell'utente.
 * Supporta login con email/password e Google Sign-In.
 */
class AuthManager(private val context: Context) {

    companion object {
        private const val TAG = "AuthManager"
    }

    private val supabase = SupabaseConfig.client
    private val prefs = PreferencesManager(context)

    /**
     * Registra un nuovo utente con email e password.
     * ⚠️ NON imposta isLoggedIn — l'utente deve prima confermare l'email
     * cliccando il link nella mail di conferma.
     */
    suspend fun signUp(email: String, password: String): Result<UserInfo?> {
        return withContext(Dispatchers.IO) {
            try {
                supabase.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }
                // NON impostare isLoggedIn = true qui!
                // L'utente deve confermare l'email prima di poter accedere.
                Log.d(TAG, "Registrazione avviata per $email — in attesa conferma email")
                Result.success(null)
            } catch (e: Exception) {
                Log.e(TAG, "Errore registrazione", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Login con email e password.
     * Funziona solo se l'utente ha già confermato la propria email.
     */
    suspend fun signIn(email: String, password: String): Result<UserInfo?> {
        return withContext(Dispatchers.IO) {
            try {
                supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                val user = supabase.auth.currentUserOrNull()
                prefs.isLoggedIn = true
                prefs.userEmail = email
                Result.success(user)
            } catch (e: Exception) {
                Log.e(TAG, "Errore login", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Login con Google ID Token (da Credential Manager).
     * Invia il token a Supabase che lo valida con il provider Google.
     */
    suspend fun signInWithGoogle(idToken: String): Result<UserInfo?> {
        return withContext(Dispatchers.IO) {
            try {
                supabase.auth.signInWith(IDToken) {
                    this.provider = Google
                    this.idToken = idToken
                }
                val user = supabase.auth.currentUserOrNull()
                prefs.isLoggedIn = true
                prefs.userEmail = user?.email ?: ""
                Result.success(user)
            } catch (e: Exception) {
                Log.e(TAG, "Errore Google Sign-In", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Chiamato dopo che il SDK ha gestito il deep link con successo.
     * Aggiorna le preferenze locali con i dati della sessione appena creata.
     */
    fun onDeepLinkSessionSuccess() {
        val user = supabase.auth.currentUserOrNull()
        if (user != null) {
            prefs.isLoggedIn = true
            prefs.userEmail = user.email ?: ""
            Log.d(TAG, "Email confermata per ${user.email}")
        }
    }

    /**
     * Restituisce il client Supabase per operazioni dirette come handleDeeplinks.
     */
    fun getSupabaseClient() = supabase

    /**
     * Logout dell'utente.
     */
    suspend fun signOut(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                supabase.auth.signOut()
                prefs.isLoggedIn = false
                prefs.userEmail = ""
                prefs.isPremium = false
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Errore logout", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Verifica e ripristina la sessione Supabase all'avvio dell'app.
     * Se la sessione locale (SharedPreferences) dice "loggato" ma Supabase
     * non ha una sessione valida, resetta lo stato.
     *
     * @return true se la sessione è valida, false altrimenti
     */
    suspend fun restoreSession(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val session = supabase.auth.currentSessionOrNull()
                if (session != null) {
                    // Sessione trovata, prova a refresharla
                    supabase.auth.refreshCurrentSession()
                    val user = supabase.auth.currentUserOrNull()
                    if (user != null) {
                        prefs.userEmail = user.email ?: prefs.userEmail
                        Log.d(TAG, "Sessione ripristinata per ${user.email}")
                        true
                    } else {
                        Log.w(TAG, "Sessione presente ma utente null — resetto")
                        prefs.isLoggedIn = false
                        false
                    }
                } else {
                    Log.d(TAG, "Nessuna sessione Supabase trovata — resetto stato locale")
                    prefs.isLoggedIn = false
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore ripristino sessione", e)
                // In caso di errore di rete, mantieni lo stato locale
                // per non bloccare l'utente offline
                prefs.isLoggedIn
            }
        }
    }

    /**
     * Verifica se l'utente ha una sessione attiva.
     */
    suspend fun isSessionActive(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                supabase.auth.currentSessionOrNull() != null
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Recupera l'utente corrente.
     */
    fun getCurrentUser(): UserInfo? {
        return try {
            supabase.auth.currentUserOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Invia email di reset password.
     */
    suspend fun resetPassword(email: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                supabase.auth.resetPasswordForEmail(email)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Errore reset password", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Controlla se l'utente è premium (locale + server).
     */
    fun isPremium(): Boolean = prefs.isPremium
}
