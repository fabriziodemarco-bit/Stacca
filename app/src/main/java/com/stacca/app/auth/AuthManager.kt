package com.stacca.app.auth

import android.content.Context
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

    private val supabase = SupabaseConfig.client
    private val prefs = PreferencesManager(context)

    /**
     * Registra un nuovo utente con email e password.
     */
    suspend fun signUp(email: String, password: String): Result<UserInfo?> {
        return withContext(Dispatchers.IO) {
            try {
                supabase.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }
                val user = supabase.auth.currentUserOrNull()
                prefs.isLoggedIn = true
                prefs.userEmail = email
                prefs.trialExpired = false
                Result.success(user)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Login con email e password.
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
                prefs.trialExpired = false
                Result.success(user)
            } catch (e: Exception) {
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
                prefs.trialExpired = false
                Result.success(user)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

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
                Result.failure(e)
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
                Result.failure(e)
            }
        }
    }

    /**
     * Controlla se l'utente è premium (locale + server).
     */
    fun isPremium(): Boolean = prefs.isPremium

    /**
     * Gestisce la conferma email dal deep link.
     */
    suspend fun handleEmailConfirmation(accessToken: String, refreshToken: String?): Result<UserInfo?> {
        return withContext(Dispatchers.IO) {
            try {
                supabase.auth.retrieveUser(accessToken)
                supabase.auth.refreshCurrentSession()
                val user = supabase.auth.currentUserOrNull()
                if (user != null) {
                    prefs.isLoggedIn = true
                    prefs.userEmail = user.email ?: ""
                    prefs.trialExpired = false
                }
                Result.success(user)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
