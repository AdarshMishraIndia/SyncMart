package com.mana.syncmart.auth

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    init {
        checkCurrentUser()
    }

    private fun checkCurrentUser() {
        _uiState.value = _uiState.value.copy(
            isAuthenticated = auth.currentUser != null
        )
    }

    fun signInWithGoogle(idToken: String) = viewModelScope.launch {
        try {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            
            authResult.user?.let { user ->
                if (user.email != null) {
                    checkAndRegisterFirestoreUser(user.email!!, user.displayName ?: "")
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "User email is null"
                    )
                    Log.e("AuthViewModel", "Signed in user has no email")
                }
            } ?: throw Exception("Authentication failed: No user returned")
            
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Authentication failed: ${e.message}"
            )
            Log.e("AuthViewModel", "Google sign in failed", e)
        }
    }

    private suspend fun checkAndRegisterFirestoreUser(email: String, name: String) {
        try {
            val userDocRef = firestore.collection("Users").document(email)
            val document = userDocRef.get().await()
            
            if (!document.exists()) {
                val newUser = hashMapOf(
                    "name" to name,
                    "friendsMap" to emptyList<String>()
                )
                userDocRef.set(newUser).await()
            }
            
            _uiState.value = _uiState.value.copy(
                isAuthenticated = true,
                isLoading = false
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Failed to register user: ${e.message}"
            )
            Log.e("AuthViewModel", "Firestore operation failed", e)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
