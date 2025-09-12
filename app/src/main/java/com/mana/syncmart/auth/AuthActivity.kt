package com.mana.syncmart.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.WindowManager
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
import com.mana.syncmart.R
import com.mana.syncmart.dashboard.ListManagementActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {

    private val viewModel: AuthViewModel by viewModels { AuthViewModelFactory(application) }
    private val credentialManager by lazy { CredentialManager.create(this) }
    private var loadingDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        // Set up UI listeners
        findViewById<android.view.View>(R.id.google_auth_button).setOnClickListener {
            beginGoogleSignIn()
        }

        // Observe ViewModel state
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    updateUi(state)
                }
            }
        }
    }

    private fun updateUi(state: AuthUiState) {
        // Handle loading state
        if (state.isLoading) {
            showLoadingDialog()
        } else {
            hideLoadingDialog()
        }

        // Handle authentication state
        if (state.isAuthenticated) {
            goToListManagement()
        }

        // Show error message if any
        state.errorMessage?.let { error ->
            Log.e("AuthActivity", error)
            viewModel.clearError()
        }
    }

    private fun showLoadingDialog() {
        if (loadingDialog?.isShowing == true) return

        val dialogView = LayoutInflater.from(this).inflate(R.layout.layout_loading, null)
        val animationView = dialogView.findViewById<LottieAnimationView>(R.id.loading_animation)
        animationView.playAnimation()

        loadingDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        loadingDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        loadingDialog?.show()

        val widthPx = resources.getDimensionPixelSize(R.dimen.loading_dialog_width)
        loadingDialog?.window?.setLayout(widthPx, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun hideLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    private fun beginGoogleSignIn() {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(getString(R.string.default_web_client_id))
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                showLoadingDialog()
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@AuthActivity
                )
                
                // Hide loader for account picker
                hideLoadingDialog()
                
                val googleIdTokenCredential = result.credential as? GoogleIdTokenCredential
                val idToken = googleIdTokenCredential?.idToken

                if (!idToken.isNullOrEmpty()) {
                    viewModel.signInWithGoogle(idToken)
                } else {
                    Log.e("AuthActivity", "Missing ID token in GoogleIdTokenCredential")
                }
            } catch (e: GetCredentialException) {
                Log.e("AuthActivity", "Credential fetch failed", e)
            } catch (e: Exception) {
                Log.e("AuthActivity", "Unexpected error during sign-in", e)
            }
        }
    }

    private fun goToListManagement() {
        startActivity(Intent(this, ListManagementActivity::class.java))
        finish()
    }
}