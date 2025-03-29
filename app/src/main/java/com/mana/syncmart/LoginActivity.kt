package com.mana.syncmart

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.firestore.FieldValue

class LoginActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        // ✅ Step 1: Check if user is already signed in
        if (auth.currentUser != null) {
            startActivity(Intent(this, ListManagementActivity::class.java))
            finish()
            return
        }

        val emailEditText = findViewById<EditText>(R.id.emailEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val registerTextView = findViewById<TextView>(R.id.textView4)

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            when {
                email.isEmpty() && password.isEmpty() -> showCustomToast("⚠\uFE0F Email & Password cannot be empty")
                email.isEmpty() -> showCustomToast("⚠\uFE0F Email cannot be empty")
                password.isEmpty() -> showCustomToast("⚠\uFE0F Password cannot be empty")
                password.length < 6 -> showCustomToast("⚠\uFE0F Password must be at least 6 characters long")
                else -> loginUser(email, password)
            }
        }

        registerTextView.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }
    }

    private fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val userId = email
                    val userDocRef = FirebaseFirestore.getInstance().collection("Users").document(userId)

                    // Get and update FCM Token
                    FirebaseMessaging.getInstance().token
                        .addOnCompleteListener { tokenTask ->
                            if (tokenTask.isSuccessful) {
                                val token = tokenTask.result
                                userDocRef.update("tokens", FieldValue.arrayUnion(token))
                            }
                        }

                    showCustomToast("✅ Login Successful")
                    startActivity(Intent(this, ListManagementActivity::class.java))
                    finish()
                } else {
                    handleLoginError(task.exception)
                }
            }
    }

    private fun handleLoginError(exception: Exception?) {
        val errorMessage = when (exception) {
            is FirebaseAuthInvalidUserException -> "❌ Email not found. Please register."
            is FirebaseAuthInvalidCredentialsException -> "❌ Incorrect E-Mail/Password."
            else -> "⚠️ Authentication Failed: ${exception?.message ?: "Unknown error"}"
        }
        showCustomToast(errorMessage)
    }

    // ✅ Custom toast method with suppressed deprecation warning
    @Suppress("DEPRECATION")
    private fun showCustomToast(message: String) {
        val inflater = LayoutInflater.from(this)
        val layout = inflater.inflate(R.layout.custom_toast_layout, findViewById(android.R.id.content), false)

        val toastText = layout.findViewById<TextView>(R.id.toast_text)
        toastText.text = message

        val toast = Toast(this)
        toast.duration = Toast.LENGTH_LONG
        toast.setGravity(Gravity.BOTTOM, 0, 200) // Positioned at bottom with 200dp offset
        toast.view = layout // Deprecated but suppressed for compatibility

        toast.show()
    }
}