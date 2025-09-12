package com.mana.syncmart.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import android.view.WindowManager
import com.mana.syncmart.R
import com.mana.syncmart.dashboard.ListManagementActivity

class AuthActivity : AppCompatActivity() {

    private lateinit var credentialManager: CredentialManager
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private var loadingDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Skip auth if already signed in
        if (auth.currentUser != null) {
            goToListManagement()
            return
        }

        setContentView(R.layout.activity_auth)
        credentialManager = CredentialManager.create(this)

        findViewById<android.view.View>(R.id.google_auth_button).setOnClickListener {
            beginGoogleSignIn()
        }
    }

    /** Show loader as a centered modal dialog **/
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

        // ✅ Set fixed width from dimens
        val widthPx = resources.getDimensionPixelSize(R.dimen.loading_dialog_width)
        loadingDialog?.window?.setLayout(widthPx, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    /** Hide loader dialog **/
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
                // Show loader until Google menu appears
                showLoadingDialog()

                val result = credentialManager.getCredential(
                    request = request,
                    context = this@AuthActivity
                )

                // Hide loader so Google chooser UI is visible
                hideLoadingDialog()

                // After account chosen, show loader again for Firebase sign-in
                showLoadingDialog()

                val googleIdTokenCredential = result.credential as? GoogleIdTokenCredential
                val idToken = googleIdTokenCredential?.idToken

                if (!idToken.isNullOrEmpty()) {
                    firebaseAuthWithGoogle(idToken)
                } else {
                    hideLoadingDialog()
                    Log.e("AuthActivity", "Missing ID token in GoogleIdTokenCredential")
                }
            } catch (e: GetCredentialException) {
                hideLoadingDialog()
                Log.e("AuthActivity", "Credential fetch failed", e)
            } catch (e: Exception) {
                hideLoadingDialog()
                Log.e("AuthActivity", "Unexpected error during sign-in", e)
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user?.email != null) {
                        checkAndRegisterFirestoreUser(user.email!!, user.displayName ?: "")
                    } else {
                        hideLoadingDialog()
                        Log.e("AuthActivity", "Signed in user has no email")
                    }
                } else {
                    hideLoadingDialog()
                    Log.e("AuthActivity", "Firebase sign-in failed", task.exception)
                }
            }
    }

    private fun checkAndRegisterFirestoreUser(email: String, name: String) {
        val userDocRef = firestore.collection("Users").document(email)

        userDocRef.get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    val newUser = mapOf(
                        "name" to name,
                        "friendsMap" to emptyList<String>()
                    )
                    userDocRef.set(newUser)
                        .addOnSuccessListener {
                            goToListManagement()
                        }
                        .addOnFailureListener {
                            hideLoadingDialog()
                            Log.e("AuthActivity", "Failed to register user", it)
                        }
                } else {
                    goToListManagement()
                }
            }
            .addOnFailureListener {
                hideLoadingDialog()
                Log.e("AuthActivity", "Failed to fetch user document", it)
            }
    }

    private fun goToListManagement() {
        // Keep loader visible until activity switch
        startActivity(Intent(this, ListManagementActivity::class.java))
        finish()
    }
}