package com.mana.syncmart

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    @SuppressLint("SetTextI18n", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        val isEditingProfile = intent.getBooleanExtra("isEditingProfile", false)

        val emailEditText = findViewById<EditText>(R.id.emailEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val registerButton = findViewById<Button>(R.id.registerButton)
        val loginTextView = findViewById<TextView>(R.id.textView4)
        val nameEditText = findViewById<EditText>(R.id.nameEditText)
        val titleText = findViewById<TextView>(R.id.textView)
        val alreadyAccountText = findViewById<TextView>(R.id.textView2)

        if (isEditingProfile) {
            // Adjust UI
            emailEditText.visibility = View.GONE
            loginTextView.visibility = View.GONE
            alreadyAccountText.visibility = View.GONE
            titleText.text = "Edit Profile"
            registerButton.text = "Update Profile"

            val user = auth.currentUser
            val email = user?.email

            if (email != null) {
                val docRef = FirebaseFirestore.getInstance().collection("Users").document(email)
                docRef.get().addOnSuccessListener { doc ->
                    nameEditText.setText(doc.getString("name") ?: "")
                    passwordEditText.setText(doc.getString("shadowPassword") ?: "")
                }
            }

            var isPasswordVisible = false

            passwordEditText.setOnTouchListener { v, event ->
                val drawableEnd = 2
                if (event.action == android.view.MotionEvent.ACTION_UP) {
                    val extraTapArea = (24 * resources.displayMetrics.density).toInt() // convert 24dp to pixels
                    val drawableWidth = passwordEditText.compoundDrawables[drawableEnd]?.bounds?.width() ?: 0
                    if (event.rawX >= (passwordEditText.right - drawableWidth - extraTapArea)) {
                        // Toggle password visibility
                        isPasswordVisible = !isPasswordVisible
                        if (isPasswordVisible) {
                            passwordEditText.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                            passwordEditText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_visibility_off, 0)
                        } else {
                            passwordEditText.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                            passwordEditText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_visibility_on, 0)
                        }
                        // Move cursor to the end
                        passwordEditText.setSelection(passwordEditText.text.length)
                        return@setOnTouchListener true
                    }
                }
                false
            }

            registerButton.setOnClickListener {
                val newName = nameEditText.text.toString().trim()
                val newPassword = passwordEditText.text.toString().trim()

                if (newName.isEmpty()) {
                    showCustomToast("⚠️ Name cannot be empty")
                    return@setOnClickListener
                }
                if (newPassword.isEmpty()) {
                    showCustomToast("⚠️ Password cannot be empty")
                    return@setOnClickListener
                }

                val user = auth.currentUser
                val email = user?.email ?: return@setOnClickListener

                val userDoc = FirebaseFirestore.getInstance().collection("Users").document(email)
                userDoc.get().addOnSuccessListener { doc ->
                    val currentName = doc.getString("name") ?: ""
                    val currentPassword = doc.getString("shadowPassword") ?: ""

                    val updates = mutableMapOf<String, Any>()
                    var needsUpdate = false

                    if (newName != currentName) {
                        updates["name"] = newName
                        needsUpdate = true
                    }

                    if (newPassword != currentPassword) {
                        user.updatePassword(newPassword).addOnFailureListener {
                            showCustomToast("❌ Failed to update password: ${it.message}")
                            return@addOnFailureListener
                        }
                        updates["shadowPassword"] = newPassword
                        needsUpdate = true
                    }

                    if (needsUpdate) {
                        userDoc.update(updates).addOnSuccessListener {
                            showCustomToast("✅ Profile updated")
                            startActivity(Intent(this, ListManagementActivity::class.java))
                            finish()
                        }.addOnFailureListener {
                            showCustomToast("❌ Update failed: ${it.message}")
                        }
                    } else {
                        showCustomToast("No changes made")
                        startActivity(Intent(this, ListManagementActivity::class.java))
                        finish()
                    }
                }
            }
        }
        else {
            // Normal registration flow
            if (auth.currentUser != null) {
                startActivity(Intent(this, ListManagementActivity::class.java))
                finish()
                return
            }

            registerButton.setOnClickListener {
                val email = emailEditText.text.toString().trim()
                val password = passwordEditText.text.toString().trim()
                val name = nameEditText.text.toString().trim()

                when {
                    name.isEmpty() -> showCustomToast("⚠️ Name cannot be empty")
                    email.isEmpty() && password.isEmpty() -> showCustomToast("⚠️ Email & Password cannot be empty")
                    email.isEmpty() -> showCustomToast("⚠️ Email cannot be empty")
                    password.isEmpty() -> showCustomToast("⚠️ Password cannot be empty")
                    password.length < 6 -> showCustomToast("⚠️ Password must be at least 6 characters long")
                    else -> registerUser(email, password, name)
                }
            }

            loginTextView.setOnClickListener {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }
    }

    private fun registerUser(email: String, password: String, name: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val userDocRef = FirebaseFirestore.getInstance().collection("Users").document(email)

                    val userData = hashMapOf(
                        "name" to name,
                        "shadowPassword" to password,
                        "friendsMap" to hashMapOf<String, String>()
                    )

                    userDocRef.set(userData)
                        .addOnSuccessListener {
                            showCustomToast("✅ Registration Successful")
                            startActivity(Intent(this, ListManagementActivity::class.java))
                            finish()
                        }
                        .addOnFailureListener { e ->
                            showCustomToast("❌ Firestore Error: ${e.message}")
                        }
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

    @Suppress("DEPRECATION")
    private fun showCustomToast(message: String) {
        val inflater = LayoutInflater.from(this)
        val layout = inflater.inflate(R.layout.custom_toast_layout, findViewById(android.R.id.content), false)
        val toastText = layout.findViewById<TextView>(R.id.toast_text)
        toastText.text = message

        val toast = Toast(this)
        toast.duration = Toast.LENGTH_LONG
        toast.setGravity(Gravity.BOTTOM, 0, 200)
        toast.view = layout
        toast.show()
    }
}
