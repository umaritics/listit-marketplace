package com.example.listit

import ListItDbHelper
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class SignUp : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var dbHelper: ListItDbHelper
    private var imageUri: Uri? = null
    private var bitmap: Bitmap? = null

    // Using Global URL from Constants.kt
    private val REGISTER_URL = Constants.REGISTER_URL

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            imageUri = uri
            findViewById<ImageView>(R.id.signup2).setImageURI(uri)

            try {
                bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(contentResolver, uri)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    MediaStore.Images.Media.getBitmap(contentResolver, uri)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        auth = FirebaseAuth.getInstance()
        dbHelper = ListItDbHelper(this)

        val imgProfile = findViewById<ImageView>(R.id.signup2)
        val nameLayout = findViewById<TextInputLayout>(R.id.name_input)
        val emailLayout = findViewById<TextInputLayout>(R.id.email)
        val passLayout = findViewById<TextInputLayout>(R.id.password)
        val phoneLayout = findViewById<TextInputLayout>(R.id.phone)
        val btnSignUp = findViewById<MaterialButton>(R.id.btnSignUp)
        val tvLogin = findViewById<TextView>(R.id.tvLogin)

        imgProfile.setOnClickListener {
            pickImage.launch("image/*")
        }

        btnSignUp.setOnClickListener {
            val name = nameLayout.editText?.text.toString().trim()
            val email = emailLayout.editText?.text.toString().trim()
            val pass = passLayout.editText?.text.toString().trim()
            val phone = phoneLayout.editText?.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isNetworkAvailable()) {
                Toast.makeText(this, "No Internet Connection", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            btnSignUp.isEnabled = false

            // Create User in Firebase (Auth)
            auth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val firebaseUser = auth.currentUser
                        val uid = firebaseUser?.uid ?: ""

                        // Update Firebase Profile Name
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(name)
                            .build()
                        firebaseUser?.updateProfile(profileUpdates)

                        // Sync to MySQL
                        registerUserInMySQL(uid, name, email, phone)
                    } else {
                        Toast.makeText(this, "Auth Failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        btnSignUp.isEnabled = true
                    }
                }
        }

        tvLogin.setOnClickListener {
            finish()
        }
    }

    private fun registerUserInMySQL(uid: String, name: String, email: String, phone: String) {
        val btnSignUp = findViewById<MaterialButton>(R.id.btnSignUp) // Fix: Find button to access it here
        val queue = Volley.newRequestQueue(this)

        val stringRequest = object : StringRequest(Request.Method.POST, REGISTER_URL,
            { response ->
                try {
                    val jsonResponse = JSONObject(response)
                    val status = jsonResponse.getString("status")

                    if (status == "success") {
                        val mysqlId = jsonResponse.getInt("user_id")
                        // The server returns the RELATIVE path (uploads/user_x.jpg)
                        val serverRelativePath = jsonResponse.optString("profile_image_url", "")

                        // Save to SQLite
                        saveUserToLocalDb(mysqlId, uid, name, email, phone, serverRelativePath)

                        Toast.makeText(this, "Account Created! Please Login.", Toast.LENGTH_SHORT).show()

                        // Don't go to Home directly; go back to Login screen (finish current SignUp activity)
                        finish()

                    } else {
                        val msg = jsonResponse.getString("message")
                        Toast.makeText(this, "Server Error: $msg", Toast.LENGTH_LONG).show()
                        btnSignUp.isEnabled = true
                    }
                } catch (e: Exception) {
                    Log.e("SignUp", "JSON Error: ${e.message}")
                    Toast.makeText(this, "Bad Response from Server", Toast.LENGTH_SHORT).show()
                    btnSignUp.isEnabled = true
                }
            },
            { error ->
                Log.e("SignUp", "Volley Error: ${error.message}")
                Toast.makeText(this, "Network Error", Toast.LENGTH_SHORT).show()
                btnSignUp.isEnabled = true
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["full_name"] = name
                params["email"] = email
                params["phone_number"] = phone
                params["firebase_uid"] = uid

                if (bitmap != null) {
                    val imageString = bitmapToString(bitmap!!)
                    params["image_data"] = imageString
                }
                return params
            }
        }

        queue.add(stringRequest)
    }

    private fun bitmapToString(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream)
        val imgBytes = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(imgBytes, Base64.DEFAULT)
    }

    private fun saveUserToLocalDb(mysqlId: Int, uid: String, name: String, email: String, phone: String, imgPath: String) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("user_id", mysqlId)
            put("full_name", name)
            put("email", email)
            put("phone_number", phone)
            put("fcm_token", uid)
            put("profile_image_url", imgPath)
            put("created_at", System.currentTimeMillis().toString())
        }
        db.insertWithOnConflict(ListItDbHelper.TABLE_USERS, null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
}