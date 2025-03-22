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
import com.google.firebase.auth.FirebaseAuthUserCollisionException

class RegisterActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        // ✅ Redirect if already signed in
        if (auth.currentUser != null) {
            startActivity(Intent(this, ListManagementActivity::class.java))
            finish()
            return
        }

        val emailEditText = findViewById<EditText>(R.id.emailEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val registerButton = findViewById<Button>(R.id.registerButton)
        val loginTextView = findViewById<TextView>(R.id.textView4)

        registerButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            when {
                email.isEmpty() && password.isEmpty() -> showCustomToast("⚠\uFE0F Email & Password cannot be empty")
                email.isEmpty() -> showCustomToast("⚠\uFE0F Email cannot be empty")
                password.isEmpty() -> showCustomToast("⚠\uFE0F Password cannot be empty")
                password.length < 6 -> showCustomToast("⚠\uFE0F Password must be at least 6 characters long")
                else -> registerUser(email, password)
            }
        }

        loginTextView.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun registerUser(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    showCustomToast("✅ Registration Successful")
                    startActivity(Intent(this, ListManagementActivity::class.java))
                    finish()
                } else {
                    handleRegisterError(task.exception)
                }
            }
    }

    private fun handleRegisterError(exception: Exception?) {
        val errorMessage = when (exception) {
            is FirebaseAuthUserCollisionException -> "❌ This E-mail is already in use. Please login."
            else -> "⚠️ Registration failed: ${exception?.message ?: "❓ Unknown error"}"
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
