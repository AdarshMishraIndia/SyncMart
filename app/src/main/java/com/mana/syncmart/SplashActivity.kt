package com.mana.syncmart

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("CustomSplashScreen")
@Suppress("DEPRECATION")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash) // Set the splash screen layout

        // Delay the transition to LoginActivity or ListManagementActivity
        Handler(Looper.getMainLooper()).postDelayed({
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val nextActivity = if (auth.currentUser != null) {
                ListManagementActivity::class.java // User logged in
            } else {
                RegisterActivity::class.java // User not logged in
            }
            startActivity(Intent(this, nextActivity))
            overridePendingTransition(R.anim.splash_fade_in, R.anim.splash_fade_out)
            finish()
        }, 1000) // 2-second delay
    }
}
