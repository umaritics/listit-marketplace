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

        // Auto-login if user is already authenticated
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

            // 1. Authenticate with Firebase
            auth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // 2. If Auth success, fetch profile data from MySQL
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

                        // --- IMAGE DOWNLOAD LOGIC ---
                        if (serverRelativePath.isNotEmpty()) {
                            // Download in background, then save to DB
                            downloadAndSaveImage(mysqlId, fullName, email, phone, serverRelativePath)
                        } else {
                            // No image, just save text data
                            saveToLocalDb(mysqlId, fullName, email, phone, "")
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

    // Downloads the image from Server, saves to Internal Storage, then updates SQLite
    private fun downloadAndSaveImage(id: Int, name: String, email: String, phone: String, relativePath: String) {
        Thread {
            try {
                // 1. Construct Full URL (e.g., http://10.0.2.2/Listit/uploads/user.jpg)
                val fullUrl = Constants.BASE_URL + relativePath
                val url = URL(fullUrl)

                // 2. Download Bitmap
                val bitmap = BitmapFactory.decodeStream(url.openConnection().getInputStream())

                // 3. Save to Internal Storage
                val filename = "profile_$id.jpg"
                val file = File(filesDir, filename) // filesDir is private app storage
                val out = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                out.flush()
                out.close()

                // 4. Get Absolute Path
                val localPath = file.absolutePath

                // 5. Save to DB on Main Thread
                runOnUiThread {
                    saveToLocalDb(id, name, email, phone, localPath)
                    goToHome(name)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                // If download fails, still let user in, but with empty image path
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
            put("profile_image_url", imgPath) // Saving the LOCAL path (or empty)
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