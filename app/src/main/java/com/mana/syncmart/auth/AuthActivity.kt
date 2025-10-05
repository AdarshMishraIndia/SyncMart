package com.mana.syncmart.auth

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.CustomCredential
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.airbnb.lottie.LottieAnimationView
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.material.snackbar.Snackbar
import com.mana.syncmart.R
import com.mana.syncmart.dashboard.ListManagementActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

private fun android.content.Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

class AuthActivity : AppCompatActivity() {

    companion object {
        private const val RC_GOOGLE_SIGN_IN = 1001
    }

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
        if (state.isLoading) showProgressDialog() else hideProgressDialog()

        if (state.isAuthenticated) navigateToDashboard()

        state.errorMessage?.let { error ->
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
        val rootView = window.decorView.findViewById<android.view.View>(android.R.id.content)
        Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show()
    }

    private fun startGoogleSignInFlow() {
        try {
            val webClientId = getString(R.string.default_web_client_id)
            if (webClientId.isBlank() || webClientId == "YOUR_WEB_CLIENT_ID") {
                throw IllegalStateException("Invalid web client ID. Please configure it in strings.xml")
            }

            val getCredentialRequest = createGetCredentialRequest(webClientId)

            lifecycleScope.launch {
                try {
                    fetchCredentialAndAuthenticate(getCredentialRequest)
                } catch (_: Exception) {
                    showToast("Using legacy sign-in method")
                    fallbackToLegacyGoogleSignIn()
                }
            }
        } catch (_: Exception) {
            showToast("Authentication service unavailable. Please try again later.")
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
        authViewModel.setLoading(true)
        try {
            val result = credentialManager.getCredential(
                request = request,
                context = this@AuthActivity
            )
            handleSignInResult(result.credential)
        } catch (e: GetCredentialException) {
            val message = e.errorMessage ?: "Authentication failed"
            showToast("Sign-in failed: $message")
            authViewModel.setError(message as String?)
        } catch (e: Exception) {
            showToast("Unexpected error during sign-in: ${e.message}")
            authViewModel.setError("An unexpected error occurred. Please try again.")
        } finally {
            authViewModel.setLoading(false)
        }
    }

    private fun handleSignInResult(credential: androidx.credentials.Credential) {
        when (credential) {
            is GoogleIdTokenCredential -> handleGoogleIdToken(credential)

            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    handleGoogleIdToken(googleCredential)
                } else {
                    authViewModel.setError("Unsupported custom credential type: ${credential.type}")
                }
            }

            is androidx.credentials.PasswordCredential -> handlePasswordCredential()
            is androidx.credentials.PublicKeyCredential -> handlePublicKeyCredential()
            else -> {
                // Ignore unknown types silently for Android 14+
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    authViewModel.setError("Unsupported credential type")
                }
            }
        }
    }

    private fun handleGoogleIdToken(credential: GoogleIdTokenCredential) {
        val idToken = credential.idToken
        if (idToken.isNotEmpty()) {
            showToast("Signing in...")
            authViewModel.signInWithGoogle(idToken)
        } else {
            showToast("Invalid credentials received")
            authViewModel.setError("Empty token received")
        }
    }

    private fun handlePasswordCredential() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            showToast("Password sign-in not yet available")
        }
    }

    private fun handlePublicKeyCredential() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            showToast("Passkey sign-in coming soon")
        }
    }

    private fun navigateToDashboard() {
        startActivity(Intent(this, ListManagementActivity::class.java))
        finish()
    }

    private fun fallbackToLegacyGoogleSignIn() {
        try {
            val webClientId = getString(R.string.default_web_client_id)
            val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
            )
                .requestIdToken(webClientId)
                .requestEmail()
                .build()

            val googleSignInClient =
                com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this, gso)
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN)
        } catch (_: Exception) {
            showErrorMessage("Failed to initialize legacy sign-in. Check your internet connection.")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_GOOGLE_SIGN_IN) {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                account.idToken?.let { token ->
                    authViewModel.signInWithGoogle(token)
                } ?: run {
                    showErrorMessage("Authentication failed: No token received")
                }
            } catch (e: Exception) {
                showErrorMessage("Authentication failed: ${e.message ?: "Unknown error"}")
            }
        }
    }
}
