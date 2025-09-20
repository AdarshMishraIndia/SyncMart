package com.mana.syncmart.auth

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.mana.syncmart.fcm.SyncMartMessagingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    init {
        checkCurrentUser()
    }

    private fun checkCurrentUser() {
        val user = auth.currentUser
        _uiState.update { it.copy(isAuthenticated = user != null) }
        if (user != null) {
            Log.d("AuthViewModel", "User already authenticated: ${user.email}")
        }
    }

    fun setLoading(isLoading: Boolean) {
        _uiState.update { it.copy(isLoading = isLoading) }
    }

    fun setError(message: String?) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun signInWithGoogle(idToken: String) = viewModelScope.launch {
        setLoading(true)
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user ?: throw Exception("Authentication failed: No user returned")

            user.email?.let { email ->
                checkAndRegisterFirestoreUser(email, user.displayName ?: "")
            } ?: run {
                setError("User email is null after successful authentication.")
                Log.e("AuthViewModel", "Signed in user has no email")
            }
        } catch (e: Exception) {
            setError("Google sign in failed: ${e.message}")
            Log.e("AuthViewModel", "Google sign in failed", e)
        } finally {
            setLoading(false)
        }
    }

    private suspend fun checkAndRegisterFirestoreUser(email: String, name: String) {
        try {
            val userDocRef = firestore.collection("Users").document(email)
            val document = userDocRef.get().await()

            if (!document.exists()) {
                val newUser = hashMapOf(
                    "name" to name,
                    "friendsMap" to emptyList<String>(),
                    "fcmTokens" to emptyList<String>()
                )
                userDocRef.set(newUser).await()
                Log.d("AuthViewModel", "New user registered in Firestore: $email")
            }

            // Get FCM token and update it in Firestore
            val token = FirebaseMessaging.getInstance().token.await()
            SyncMartMessagingService().updateTokenInFirestore(email, token)
            Log.d("AuthViewModel", "FCM token updated for user: $email")

            _uiState.update { it.copy(isAuthenticated = true) }
        } catch (e: Exception) {
            setError("Failed to register user in Firestore: ${e.message}")
            Log.e("AuthViewModel", "Firestore operation failed", e)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}