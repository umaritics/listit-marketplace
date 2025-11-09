package com.example.listit

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class Login_email : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login_email)
        var phone_button = findViewById<android.widget.Button>(R.id.login_phone_btn)
        phone_button.setOnClickListener {
            startActivity(Intent(this, Login_phone::class.java))
        }

        findViewById<TextView>(R.id.login_google_signup).setOnClickListener {
            startActivity(Intent(this, SignUp::class.java))
        }


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        var login=findViewById<com.google.android.material.button.MaterialButton>(R.id.login_button)
        login.setOnClickListener {
            startActivity(Intent(this, Home::class.java))
        }
    }
}