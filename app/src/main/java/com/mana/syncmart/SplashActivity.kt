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
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            val nextActivity = AuthActivity::class.java
            startActivity(Intent(this, nextActivity))
            overridePendingTransition(R.anim.splash_fade_in, R.anim.splash_fade_out)
            finish()
        }, 1000) // 1-second delay
    }
}
