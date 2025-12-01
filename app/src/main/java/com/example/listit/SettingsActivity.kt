package com.example.listit

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth

class SettingsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Handle Window Insets (Edge to Edge)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // --- NAVIGATION LOGIC ---

        // 1. Notification Settings
        // Using 'rowNotification' so the user can click anywhere on the row, not just the arrow
        findViewById<RelativeLayout>(R.id.rowNotification).setOnClickListener {
            // Ensure NotificationsSettings_Activity is created in your project
            val intent = Intent(this, NotificationsSettings_Activity::class.java)
            startActivity(intent)
        }

        // 2. Logout Functionality
        findViewById<RelativeLayout>(R.id.rowLogout).setOnClickListener {
            performLogout()
        }

        // 3. Back Button logic
        findViewById<RelativeLayout>(R.id.topbar).setOnClickListener {
            finish()
        }
    }

    private fun performLogout() {
        // 1. Sign out from Firebase
        auth.signOut()

        // 2. Create Intent to go back to Login
        // We use Login_email::class.java as the entry point
        val intent = Intent(this, Login_email::class.java)

        // 3. CRITICAL: Clear the Activity Stack
        // This prevents the user from pressing "Back" and re-entering the app without logging in
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)
        finish()

        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
    }
}