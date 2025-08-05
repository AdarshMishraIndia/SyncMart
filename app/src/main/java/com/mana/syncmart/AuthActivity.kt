package com.mana.syncmart

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {

    private lateinit var credentialManager: CredentialManager
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Skip auth if already signed in
        if (auth.currentUser != null) {
            goToListManagement()
            return
        }

        setContentView(R.layout.activity_auth)
        credentialManager = CredentialManager.create(this)

        findViewById<View>(R.id.google_auth_button).setOnClickListener {
        beginGoogleSignIn()
        }
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
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@AuthActivity
                )
                val googleIdTokenCredential = result.credential as? GoogleIdTokenCredential
                val idToken = googleIdTokenCredential?.idToken

                if (!idToken.isNullOrEmpty()) {
                    firebaseAuthWithGoogle(idToken)
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

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user?.email != null) {
                        checkAndRegisterFirestoreUser(user.email!!, user.displayName ?: "")
                    } else {
                        Log.e("AuthActivity", "Signed in user has no email")
                    }
                } else {
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
                            Log.d("AuthActivity", "User registered")
                            goToListManagement()
                        }
                        .addOnFailureListener {
                            Log.e("AuthActivity", "Failed to register user", it)
                        }
                } else {
                    goToListManagement()
                }
            }
            .addOnFailureListener {
                Log.e("AuthActivity", "Failed to fetch user document", it)
            }
    }

    private fun goToListManagement() {
        startActivity(Intent(this, ListManagementActivity::class.java))
        finish()
    }
}