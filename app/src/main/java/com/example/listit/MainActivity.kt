package com.example.listit

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Delay in milliseconds (e.g., 2000 = 2 seconds)
        Handler(Looper.getMainLooper()).postDelayed({

            // Check if user is logged in using Firebase
            val currentUser = FirebaseAuth.getInstance().currentUser

            val targetActivity = if (currentUser != null) {
                // User is logged in -> Go to Home
                Home::class.java
            } else {
                // User is NOT logged in -> Go to Onboarding
                OB_screen1::class.java
            }

            val intent = Intent(this, targetActivity)
            startActivity(intent)
            finish() // finish this activity so user can't return to it

        }, 2000)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}