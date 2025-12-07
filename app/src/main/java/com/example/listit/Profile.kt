package com.example.listit

import ListItDbHelper
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri // Added Uri import
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide // Import Glide
import com.google.firebase.auth.FirebaseAuth
import java.io.File

class Profile : AppCompatActivity() {

    private lateinit var dbHelper: ListItDbHelper
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile)

        // Initialize DB and Auth
        dbHelper = ListItDbHelper(this)
        auth = FirebaseAuth.getInstance()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Load data initially
        loadUserProfile()

        // --- Navigation Buttons ---
        findViewById<LinearLayout>(R.id.settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(0, 0)
        }
        findViewById<ImageView>(R.id.notification_icon).setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
            overridePendingTransition(0, 0)
        }
        findViewById<ImageView>(R.id.home_ic).setOnClickListener {
            startActivity(Intent(this, Home::class.java))
            finish()
            overridePendingTransition(0, 0)
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_edit_profile).setOnClickListener {
            startActivity(Intent(this, EditProfile::class.java))
            overridePendingTransition(0, 0)
        }

        findViewById<ImageView>(R.id.chat_ic).setOnClickListener {
            startActivity(Intent(this, Buying::class.java))
            finish()
            overridePendingTransition(0, 0)
        }

        findViewById<ImageView>(R.id.add_ic)?.setOnClickListener {
            startActivity(Intent(this, PostAd::class.java))
            finish()
            overridePendingTransition(0, 0)
        }

        findViewById<ImageView>(R.id.fav_ic).setOnClickListener {
            startActivity(Intent(this, MyAds::class.java))
            finish()
            overridePendingTransition(0, 0)
        }

        findViewById<ImageView>(R.id.prof_ic).setOnClickListener {
            // Already on Profile
        }
    }

    // Refresh data when returning to this screen (e.g. after editing profile)
    override fun onResume() {
        super.onResume()
        loadUserProfile()
    }

    private fun loadUserProfile() {
        val user = auth.currentUser
        val email = user?.email

        if (user == null || email == null) {
            return
        }

        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT full_name, profile_image_url FROM users WHERE email = ?", arrayOf(email))

        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex("full_name")
            val imgIndex = cursor.getColumnIndex("profile_image_url")

            // 1. Set Name
            if (nameIndex != -1) {
                val fullName = cursor.getString(nameIndex)
                if (!fullName.isNullOrEmpty()) {
                    findViewById<TextView>(R.id.user_name).text = fullName
                }
            }

            // 2. Set Image (UPDATED LOGIC)
            if (imgIndex != -1) {
                val imagePath = cursor.getString(imgIndex) ?: ""
                val profileImageView = findViewById<ImageView>(R.id.profile_image)

                if (imagePath.isNotEmpty()) {
                    val imgFile = File(imagePath)

                    if (imgFile.exists()) {
                        // A. Local File Exists: Load directly
                        profileImageView.setImageURI(Uri.fromFile(imgFile))
                    } else {
                        // B. Local File Missing (likely a Server URL): Use Glide
                        var fullUrl = imagePath
                        if (!fullUrl.startsWith("http")) {
                            fullUrl = Constants.BASE_URL + imagePath
                        }

                        Glide.with(this)
                            .load(fullUrl)
                            .placeholder(R.drawable.user_pfp) // Replace with your default drawable
                            .error(R.drawable.user_pfp)       // Replace with your default drawable
                            .into(profileImageView)
                    }
                }
            }
        }
        cursor.close()
    }
}