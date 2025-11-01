package com.example.listit

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton

class otp_get : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_otp_get)




        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                systemBars.left + 24.dp,
                systemBars.top + 24.dp,
                systemBars.right + 24.dp,
                systemBars.bottom + 24.dp
            )
            insets
        }


        val cont = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnContinue)
        cont.setOnClickListener {
            startActivity(Intent(this, Otp_Enter::class.java))
        }

    }

    val Int.dp get() = (this * resources.displayMetrics.density).toInt()

}