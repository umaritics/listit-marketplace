package com.example.listit

import ListItDbHelper
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging // Added
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class Login_email : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var dbHelper: ListItDbHelper
    private val LOGIN_URL = Constants.LOGIN_URL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_email)

        auth = FirebaseAuth.getInstance()
        dbHelper = ListItDbHelper(this)

        if (auth.currentUser != null) {
            startActivity(Intent(this, Home::class.java))
            finish()
        }

        val emailInput = findViewById<TextInputLayout>(R.id.email_input)
        val passwordInput = findViewById<TextInputLayout>(R.id.password_input)
        val loginButton = findViewById<MaterialButton>(R.id.login_button)
        val signupText = findViewById<TextView>(R.id.login_google_signup)

        loginButton.setOnClickListener {
            val email = emailInput.editText?.text.toString().trim()
            val pass = passwordInput.editText?.text.toString().trim()

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isNetworkAvailable()) {
                Toast.makeText(this, "Please connect to the internet to login", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        fetchUserDataFromMySQL(email)
                    } else {
                        Toast.makeText(this, "Login Failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        signupText.setOnClickListener {
            startActivity(Intent(this, SignUp::class.java))
        }

        findViewById<MaterialButton>(R.id.login_phone_btn).setOnClickListener {
            startActivity(Intent(this, Login_phone::class.java))
        }
    }

    private fun fetchUserDataFromMySQL(email: String) {
        val queue = Volley.newRequestQueue(this)

        val stringRequest = object : StringRequest(Request.Method.POST, LOGIN_URL,
            { response ->
                try {
                    val jsonResponse = JSONObject(response)
                    val status = jsonResponse.getString("status")

                    if (status == "success") {
                        val data = jsonResponse.getJSONObject("data")

                        val mysqlId = data.getInt("user_id")
                        val fullName = data.getString("full_name")
                        val phone = data.getString("phone_number")
                        val serverRelativePath = data.optString("profile_image_url", "")

                        // NEW: Update FCM Token for this user
                        updateFCMToken(mysqlId)

                        // 1. Save User Profile Logic
                        if (serverRelativePath.isNotEmpty()) {
                            downloadAndSaveImage(mysqlId, fullName, email, phone, serverRelativePath)
                        } else {
                            saveToLocalDb(mysqlId, fullName, email, phone, "")
                        }

                        // 2. SAVE FETCHED HEARTS (Favorites)
                        if (jsonResponse.has("saved_ads")) {
                            val savedArray = jsonResponse.getJSONArray("saved_ads")
                            val db = dbHelper.writableDatabase
                            db.execSQL("DELETE FROM saved_ads WHERE user_id = $mysqlId AND is_synced = 1")

                            for (i in 0 until savedArray.length()) {
                                val adId = savedArray.getInt(i)
                                val values = ContentValues().apply {
                                    put("user_id", mysqlId)
                                    put("ad_id", adId)
                                    put("is_deleted", 0)
                                    put("is_synced", 1)
                                    put("created_at", System.currentTimeMillis().toString())
                                }
                                db.insert(ListItDbHelper.TABLE_SAVED_ADS, null, values)
                            }
                        }

                        if (serverRelativePath.isEmpty()) {
                            goToHome(fullName)
                        }

                    } else {
                        Toast.makeText(this, "User not found in database.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e("Login", "JSON Error: ${e.message}")
                    Toast.makeText(this, "Error parsing server data", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Log.e("Login", "Volley Error: ${error.message}")
                Toast.makeText(this, "Network Error. Could not fetch profile.", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["email"] = email
                return params
            }
        }

        queue.add(stringRequest)
    }

    private fun updateFCMToken(userId: Int) {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            val queue = Volley.newRequestQueue(this)
            val url = Constants.BASE_URL + "update_token.php"

            val request = object : StringRequest(Request.Method.POST, url,
                { Log.d("FCM", "Token Updated on Server") },
                { Log.e("FCM", "Failed to update token") }
            ) {
                override fun getParams(): MutableMap<String, String> {
                    val params = HashMap<String, String>()
                    params["user_id"] = userId.toString()
                    params["fcm_token"] = token
                    return params
                }
            }
            queue.add(request)
        }
    }

    private fun downloadAndSaveImage(id: Int, name: String, email: String, phone: String, relativePath: String) {
        Thread {
            try {
                val fullUrl = Constants.BASE_URL + relativePath
                val url = URL(fullUrl)
                val bitmap = BitmapFactory.decodeStream(url.openConnection().getInputStream())

                val filename = "profile_$id.jpg"
                val file = File(filesDir, filename)
                val out = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                out.flush()
                out.close()

                val localPath = file.absolutePath

                runOnUiThread {
                    saveToLocalDb(id, name, email, phone, localPath)
                    goToHome(name)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    saveToLocalDb(id, name, email, phone, "")
                    goToHome(name)
                }
            }
        }.start()
    }

    private fun saveToLocalDb(id: Int, name: String, email: String, phone: String, imgPath: String) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("user_id", id)
            put("full_name", name)
            put("email", email)
            put("phone_number", phone)
            put("profile_image_url", imgPath)
            put("created_at", System.currentTimeMillis().toString())
        }
        db.insertWithOnConflict(ListItDbHelper.TABLE_USERS, null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun goToHome(name: String) {
        Toast.makeText(this, "Welcome Back, $name!", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, Home::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
}