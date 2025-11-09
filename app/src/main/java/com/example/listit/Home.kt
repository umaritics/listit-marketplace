package com.example.listit

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class Home : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        findViewById<ImageView>(R.id.cars).setOnClickListener {
            val intent = Intent(this, Category_Car::class.java)
            startActivity(intent)
        }
        findViewById<LinearLayout>(R.id.ad).setOnClickListener {
            val intent = Intent(this, ViewAd::class.java)
            startActivity(intent)
        }


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        findViewById<LinearLayout>(R.id.category_bar).setOnClickListener {
            startActivity(Intent(this, Filters::class.java))
            finish()
            overridePendingTransition(0, 0)
        }
        // Put inside onCreate() of each Activity
        findViewById<ImageView>(R.id.home_ic).setOnClickListener {
            startActivity(Intent(this, Home::class.java))
            finish()
            overridePendingTransition(0, 0)
        }

        findViewById<ImageView>(R.id.chat_ic).setOnClickListener {
            startActivity(Intent(this, Buying::class.java))
            finish()
            overridePendingTransition(0, 0)
        }

        findViewById<ImageView>(R.id.add_ic)?.setOnClickListener {
            startActivity(Intent(this, PostAd::class.java))
            overridePendingTransition(0, 0)
        }

        findViewById<ImageView>(R.id.fav_ic).setOnClickListener {
            startActivity(Intent(this, MyAds::class.java))
            finish()
            overridePendingTransition(0, 0)
        }

        findViewById<ImageView>(R.id.prof_ic).setOnClickListener {
            startActivity(Intent(this, Profile::class.java))
            finish()
            overridePendingTransition(0, 0)
        }


    }
}