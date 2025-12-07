package com.example.listit

import ListItDbHelper
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide // Import Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EditProfile : AppCompatActivity() {

    // Database & Auth
    private lateinit var dbHelper: ListItDbHelper
    private lateinit var auth: FirebaseAuth
    private var currentUserId: Int = -1

    // UI Components
    private lateinit var nameInputLayout: TextInputLayout
    private lateinit var phoneInputLayout: TextInputLayout
    private lateinit var profileImageView: ImageView
    private lateinit var saveButton: MaterialButton
    private lateinit var backBtn: ImageView

    // Data
    private var selectedImageUri: Uri? = null
    private var currentLocalImagePath: String = "" // Holds the path currently in DB

    // API URL
    private val EDIT_PROFILE_URL = Constants.BASE_URL + "edit_profile.php"

    // Image Picker
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val uri: Uri? = data?.data
            if (uri != null) {
                selectedImageUri = uri
                // Clear any Glide loading to show the fresh local selection
                Glide.with(this).clear(profileImageView)
                profileImageView.setImageURI(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_edit_profile)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        dbHelper = ListItDbHelper(this)
        auth = FirebaseAuth.getInstance()

        initViews()
        loadCurrentUser()
        setupListeners()
    }

    private fun initViews() {
        nameInputLayout = findViewById(R.id.name_input)
        phoneInputLayout = findViewById(R.id.phone)
        profileImageView = findViewById(R.id.pfp)
        backBtn = findViewById(R.id.backbtn)

        // Find Save Button
        val rootView = findViewById<android.view.ViewGroup>(R.id.main)
        val buttons = rootView.touchables
        for (view in buttons) {
            if (view is MaterialButton) {
                saveButton = view
                break
            }
        }
    }

    private fun setupListeners() {
        backBtn.setOnClickListener { finish() }

        profileImageView.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImageLauncher.launch(intent)
        }

        saveButton.setOnClickListener {
            saveProfileData()
        }
    }

    private fun loadCurrentUser() {
        val email = auth.currentUser?.email
        if (email != null) {
            val db = dbHelper.readableDatabase
            val cursor = db.rawQuery("SELECT * FROM ${ListItDbHelper.TABLE_USERS} WHERE email = ?", arrayOf(email))

            if (cursor.moveToFirst()) {
                currentUserId = cursor.getInt(cursor.getColumnIndexOrThrow("user_id"))
                val name = cursor.getString(cursor.getColumnIndexOrThrow("full_name"))
                val phone = cursor.getString(cursor.getColumnIndexOrThrow("phone_number"))
                currentLocalImagePath = cursor.getString(cursor.getColumnIndexOrThrow("profile_image_url")) ?: ""

                // Set UI
                nameInputLayout.editText?.setText(name)
                phoneInputLayout.editText?.setText(phone)

                // --- UPDATED IMAGE LOADING LOGIC ---
                if (currentLocalImagePath.isNotEmpty()) {
                    val imgFile = File(currentLocalImagePath)

                    if (imgFile.exists()) {
                        // 1. If it exists locally on the phone, load it directly
                        profileImageView.setImageURI(Uri.fromFile(imgFile))
                    } else {
                        // 2. If local file is missing, it might be a Server URL (e.g. "uploads/user_1.jpg")
                        // Construct the full URL
                        var fullUrl = currentLocalImagePath
                        if (!fullUrl.startsWith("http")) {
                            fullUrl = Constants.BASE_URL + currentLocalImagePath
                        }

                        // Use Glide to load from the internet
                        Glide.with(this)
                            .load(fullUrl)
                            .placeholder(R.drawable.signup2) // Show this while loading
                            .error(R.drawable.signup2)       // Show this if loading fails
                            .into(profileImageView)
                    }
                }
            }
            cursor.close()
        }
    }

    private fun saveProfileData() {
        val fullName = nameInputLayout.editText?.text.toString().trim()
        val phone = phoneInputLayout.editText?.text.toString().trim()

        if (fullName.isEmpty()) {
            nameInputLayout.error = "Name is required"
            return
        }

        saveButton.isEnabled = false
        saveButton.text = "Saving..."

        // 1. IMAGE HANDLING (Local First)
        var finalImagePath = currentLocalImagePath

        // If a NEW image was selected, save it to internal storage first
        if (selectedImageUri != null) {
            val savedPath = saveImageToInternalStorage(selectedImageUri!!)
            if (savedPath != null) {
                finalImagePath = savedPath
            }
        }

        // 2. UPDATE SQLITE (Local First)
        updateLocalDatabase(fullName, phone, finalImagePath)

        // 3. NETWORK SYNC
        if (isNetworkAvailable()) {
            syncProfileWithServer(fullName, phone, finalImagePath)
        } else {
            Toast.makeText(this, "Profile saved locally. Will sync when online.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun updateLocalDatabase(name: String, phone: String, imagePath: String) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("full_name", name)
            put("phone_number", phone)
            put("profile_image_url", imagePath)

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            put("updated_at", sdf.format(Date()))
        }

        db.update(ListItDbHelper.TABLE_USERS, values, "user_id = ?", arrayOf(currentUserId.toString()))
    }

    private fun syncProfileWithServer(name: String, phone: String, imagePath: String) {
        val queue = Volley.newRequestQueue(this)

        val stringRequest = object : StringRequest(Request.Method.POST, EDIT_PROFILE_URL,
            { response ->
                try {
                    val json = JSONObject(response)
                    val status = json.getString("status")

                    if (status == "success") {
                        Toast.makeText(this, "Profile Updated Successfully!", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        val msg = json.getString("message")
                        Toast.makeText(this, "Server Error: $msg", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    finish()
                }
            },
            { error ->
                Toast.makeText(this, "Network failed. Changes saved locally.", Toast.LENGTH_SHORT).show()
                finish()
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["user_id"] = currentUserId.toString()
                params["full_name"] = name
                params["phone_number"] = phone

                // Convert Image to Base64 only if it's a local file
                if (imagePath.isNotEmpty()) {
                    val file = File(imagePath)
                    if (file.exists()) {
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        // Resize to avoid OOM
                        val resized = getResizedBitmap(bitmap, 800)
                        params["image_data"] = bitmapToString(resized)
                    }
                }
                return params
            }
        }
        queue.add(stringRequest)
    }

    // --- HELPER FUNCTIONS ---

    private fun saveImageToInternalStorage(uri: Uri): String? {
        return try {
            val folder = File(filesDir, "profile_images")
            if (!folder.exists()) folder.mkdir()

            val bitmap = getResizedBitmapFromUri(uri) ?: return null

            val filename = "user_${currentUserId}_${System.currentTimeMillis()}.jpg"
            val file = File(folder, filename)

            val fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, fos)
            fos.flush()
            fos.close()

            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getResizedBitmapFromUri(uri: Uri): Bitmap? {
        var inputStream: InputStream? = contentResolver.openInputStream(uri)
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream?.close()

        var inSampleSize = 1
        val reqWidth = 800
        val reqHeight = 800

        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        val finalOptions = BitmapFactory.Options()
        finalOptions.inSampleSize = inSampleSize
        inputStream = contentResolver.openInputStream(uri)
        val resizedBitmap = BitmapFactory.decodeStream(inputStream, null, finalOptions)
        inputStream?.close()
        return resizedBitmap
    }

    private fun getResizedBitmap(image: Bitmap, maxSize: Int): Bitmap {
        var width = image.width
        var height = image.height
        val bitmapRatio = width.toFloat() / height.toFloat()
        if (bitmapRatio > 1) {
            width = maxSize
            height = (width / bitmapRatio).toInt()
        } else {
            height = maxSize
            width = (height * bitmapRatio).toInt()
        }
        return Bitmap.createScaledBitmap(image, width, height, true)
    }

    private fun bitmapToString(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream)
        return Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.DEFAULT)
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
}