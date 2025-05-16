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

class LoginActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var loginButton: Button // Declare at class level


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
        loginButton = findViewById(R.id.loginButton)
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
        val safeEmail = email.trim()
        val safePassword = password.trim()

        loginButton.isEnabled = false  // Disable to prevent multiple taps

        auth.signInWithEmailAndPassword(safeEmail, safePassword)
            .addOnCompleteListener(this) { task ->
                loginButton.isEnabled = true // Re-enable after result

                if (task.isSuccessful) {
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