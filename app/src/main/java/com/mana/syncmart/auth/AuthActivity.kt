package com.mana.syncmart.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.airbnb.lottie.LottieAnimationView
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.material.snackbar.Snackbar
import com.mana.syncmart.R
import com.mana.syncmart.dashboard.ListManagementActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {

    private val authViewModel: AuthViewModel by viewModels { AuthViewModelFactory(application) }
    private val credentialManager by lazy { CredentialManager.create(this) }
    private var progressDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        setupListeners()
        observeViewModelState()
    }

    private fun setupListeners() {
        findViewById<android.view.View>(R.id.google_auth_button).setOnClickListener {
            startGoogleSignInFlow()
        }
    }

    private fun observeViewModelState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.uiState.collectLatest { state ->
                    handleUiState(state)
                }
            }
        }
    }

    private fun handleUiState(state: AuthUiState) {
        if (state.isLoading) {
            showProgressDialog()
        } else {
            hideProgressDialog()
        }

        if (state.isAuthenticated) {
            navigateToDashboard()
        }

        state.errorMessage?.let { error ->
            Log.e("AuthActivity", "UI State Error: $error")
            showErrorMessage(error)
            authViewModel.clearError()
        }
    }

    private fun showProgressDialog() {
        if (progressDialog?.isShowing == true) return

        val dialogView = LayoutInflater.from(this).inflate(R.layout.layout_loading, null)
        dialogView.findViewById<LottieAnimationView>(R.id.loading_animation)?.playAnimation()

        progressDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create().apply {
                window?.setBackgroundDrawableResource(android.R.color.transparent)
                window?.setLayout(resources.getDimensionPixelSize(R.dimen.loading_dialog_width), -2)
                show()
            }
    }

    private fun hideProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun showErrorMessage(message: String) {
        // Use a more robust way to find the root view
        val rootView = window.decorView.findViewById<android.view.View>(android.R.id.content)
        Snackbar.make(rootView, message, Snackbar.LENGTH_LONG)
            .show()
        Log.e("AuthActivity", "Error displayed: $message")
    }

    private fun startGoogleSignInFlow() {
        val webClientId = getString(R.string.default_web_client_id)
        if (webClientId.isBlank() || webClientId == "YOUR_WEB_CLIENT_ID") {
            Log.e("AuthActivity", "Invalid web client ID. Please configure it in strings.xml")
            showErrorMessage("Authentication not configured properly.")
            return
        }

        val getCredentialRequest = createGetCredentialRequest(webClientId)
        lifecycleScope.launch {
            fetchCredentialAndAuthenticate(getCredentialRequest)
        }
    }

    private fun createGetCredentialRequest(webClientId: String): GetCredentialRequest {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .build()

        return GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
    }

    private suspend fun fetchCredentialAndAuthenticate(request: GetCredentialRequest) {
        try {
            authViewModel.setLoading(true)
            val result = credentialManager.getCredential(
                request = request,
                context = this@AuthActivity
            )
            handleSignInResult(result.credential)
        } catch (e: GetCredentialException) {
            val errorMessage = e.errorMessage ?: "Authentication failed: Unknown error"
            Log.e("AuthActivity", "Credential fetch failed: $errorMessage", e)
            authViewModel.setError(errorMessage as String?)
        } catch (e: Exception) {
            Log.e("AuthActivity", "Unexpected error during sign-in", e)
            authViewModel.setError("An unexpected error occurred. Please try again.")
        } finally {
            authViewModel.setLoading(false)
        }
    }

    private fun handleSignInResult(credential: androidx.credentials.Credential) {
        when (credential) {
            is GoogleIdTokenCredential -> {
                val idToken = credential.idToken
                if (idToken.isNotEmpty()) {
                    Log.d("AuthActivity", "Successfully received ID token. Authenticating with backend...")
                    authViewModel.signInWithGoogle(idToken)
                } else {
                    Log.e("AuthActivity", "Empty ID token received")
                    authViewModel.setError("Authentication failed: Empty token received")
                }
            }
            else -> {
                Log.e("AuthActivity", "Unexpected credential type: ${credential.javaClass.name}")
                authViewModel.setError("Authentication failed: Unsupported credential type")
            }
        }
    }

    private fun navigateToDashboard() {
        Log.d("AuthActivity", "Authentication successful. Navigating to dashboard.")
        startActivity(Intent(this, ListManagementActivity::class.java))
        finish()
    }
}