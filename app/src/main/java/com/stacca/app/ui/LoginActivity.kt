package com.stacca.app.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.stacca.app.BuildConfig
import com.stacca.app.R
import com.stacca.app.auth.AuthManager
import com.stacca.app.data.PreferencesManager
import kotlinx.coroutines.launch

/**
 * Activity di login/registrazione con Supabase Auth.
 * Supporta login email/password e Google Sign-In.
 * L'utente può anche saltare il login e usare l'app in modalità free.
 */
class LoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LoginActivity"
    }

    private lateinit var authManager: AuthManager
    private lateinit var prefs: PreferencesManager

    private lateinit var tabLayout: TabLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var btnLogin: MaterialButton
    private lateinit var btnGoogleSignIn: MaterialButton
    private lateinit var tvError: TextView
    private lateinit var tvForgotPassword: TextView
    private lateinit var progressLogin: CircularProgressIndicator

    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authManager = AuthManager(this)
        prefs = PreferencesManager(this)

        // Se è già loggato, vai direttamente alla main
        if (prefs.isLoggedIn) {
            goToMain()
            return
        }

        setContentView(R.layout.activity_login)

        initViews()
        setupListeners()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "stacca" && data.host == "login-callback") {
            val accessToken = data.getQueryParameter("access_token")
            val refreshToken = data.getQueryParameter("refresh_token")

            if (accessToken != null) {
                lifecycleScope.launch {
                    val result = authManager.handleEmailConfirmation(accessToken, refreshToken)
                    result.onSuccess {
                        Toast.makeText(this@LoginActivity,
                            "Email confermata! Benvenuto in Stacca! 🎉",
                            Toast.LENGTH_LONG).show()
                        goToMain()
                    }.onFailure {
                        showError("Errore nella conferma email. Riprova.")
                    }
                }
            }
        }
    }

    private fun initViews() {
        tabLayout = findViewById(R.id.tabLayout)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        tilEmail = findViewById(R.id.tilEmail)
        tilPassword = findViewById(R.id.tilPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn)
        tvError = findViewById(R.id.tvError)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
        progressLogin = findViewById(R.id.progressLogin)
    }

    private fun setupListeners() {
        // Tab switch login/registrazione
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                isLoginMode = tab?.position == 0
                btnLogin.text = if (isLoginMode) {
                    getString(R.string.login_btn_login)
                } else {
                    getString(R.string.login_btn_register)
                }
                tvForgotPassword.visibility = if (isLoginMode) View.VISIBLE else View.GONE
                tvError.visibility = View.GONE
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Login/Register button
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (!validateInput(email, password)) return@setOnClickListener

            if (isLoginMode) {
                performLogin(email, password)
            } else {
                performSignUp(email, password)
            }
        }

        // Google Sign-In button
        btnGoogleSignIn.setOnClickListener {
            performGoogleSignIn()
        }

        // Forgot password
        tvForgotPassword.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                tilEmail.error = getString(R.string.login_error_email_required)
                return@setOnClickListener
            }
            performPasswordReset(email)
        }
    }

    /**
     * Avvia il flusso Google Sign-In con Credential Manager.
     */
    private fun performGoogleSignIn() {
        val googleClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (googleClientId.isEmpty()) {
            showError("Google Sign-In non configurato. Usa email e password.")
            Log.e(TAG, "GOOGLE_WEB_CLIENT_ID is empty in BuildConfig")
            return
        }

        setLoading(true)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(googleClientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credentialManager = CredentialManager.create(this)

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@LoginActivity
                )
                handleGoogleSignInResult(result)
            } catch (e: GetCredentialException) {
                setLoading(false)
                Log.e(TAG, "Google Sign-In failed", e)
                showError(getString(R.string.login_google_error, e.message ?: "Errore sconosciuto"))
            }
        }
    }

    /**
     * Gestisce il risultato del Credential Manager.
     */
    private fun handleGoogleSignInResult(result: GetCredentialResponse) {
        val credential = result.credential

        when (credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val idToken = googleIdTokenCredential.idToken

                        // Invia il token a Supabase
                        lifecycleScope.launch {
                            val authResult = authManager.signInWithGoogle(idToken)
                            setLoading(false)

                            authResult.onSuccess {
                                Toast.makeText(this@LoginActivity,
                                    getString(R.string.login_google_success),
                                    Toast.LENGTH_SHORT).show()
                                goToMain()
                            }.onFailure { error ->
                                showError(getString(R.string.login_google_error,
                                    error.localizedMessage ?: "Errore sconosciuto"))
                            }
                        }
                    } catch (e: GoogleIdTokenParsingException) {
                        setLoading(false)
                        Log.e(TAG, "Invalid Google ID token", e)
                        showError(getString(R.string.login_google_error, "Token non valido"))
                    }
                } else {
                    setLoading(false)
                    showError(getString(R.string.login_google_error, "Tipo credenziale non supportato"))
                }
            }
            else -> {
                setLoading(false)
                showError(getString(R.string.login_google_error, "Credenziale non riconosciuta"))
            }
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        tilEmail.error = null
        tilPassword.error = null
        tvError.visibility = View.GONE

        if (email.isEmpty()) {
            tilEmail.error = getString(R.string.login_error_email_required)
            return false
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.error = getString(R.string.login_error_email_invalid)
            return false
        }

        if (password.isEmpty()) {
            tilPassword.error = getString(R.string.login_error_password_required)
            return false
        }

        if (password.length < 6) {
            tilPassword.error = getString(R.string.login_error_password_short)
            return false
        }

        return true
    }

    private fun performLogin(email: String, password: String) {
        setLoading(true)

        lifecycleScope.launch {
            val result = authManager.signIn(email, password)
            setLoading(false)

            result.onSuccess {
                Toast.makeText(this@LoginActivity,
                    getString(R.string.login_success), Toast.LENGTH_SHORT).show()
                goToMain()
            }.onFailure { error ->
                showError(getString(R.string.login_error_generic, error.localizedMessage ?: ""))
            }
        }
    }

    private fun performSignUp(email: String, password: String) {
        setLoading(true)

        lifecycleScope.launch {
            val result = authManager.signUp(email, password)
            setLoading(false)

            result.onSuccess {
                Toast.makeText(this@LoginActivity,
                    getString(R.string.login_register_success), Toast.LENGTH_LONG).show()
                // Torna alla modalità login
                tabLayout.getTabAt(0)?.select()
            }.onFailure { error ->
                showError(getString(R.string.login_error_generic, error.localizedMessage ?: ""))
            }
        }
    }

    private fun performPasswordReset(email: String) {
        setLoading(true)

        lifecycleScope.launch {
            val result = authManager.resetPassword(email)
            setLoading(false)

            result.onSuccess {
                Toast.makeText(this@LoginActivity,
                    getString(R.string.login_reset_sent), Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                showError(getString(R.string.login_error_generic, error.localizedMessage ?: ""))
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        progressLogin.visibility = if (loading) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !loading
        btnLogin.alpha = if (loading) 0.5f else 1f
        btnGoogleSignIn.isEnabled = !loading
        btnGoogleSignIn.alpha = if (loading) 0.5f else 1f
    }

    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
