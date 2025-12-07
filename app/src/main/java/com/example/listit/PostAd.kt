package com.example.listit

import ListItDbHelper
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PostAd : AppCompatActivity() {

    private lateinit var dbHelper: ListItDbHelper
    private lateinit var auth: FirebaseAuth

    private val selectedImages = ArrayList<Uri>()
    private var selectedCondition = "Used"

    // Coordinates
    private var finalLat: Double = 0.0
    private var finalLng: Double = 0.0

    private lateinit var mainPreview: ImageView
    private lateinit var btnOpenUpload: MaterialButton
    private lateinit var btnNew: Button
    private lateinit var btnUsed: Button
    private lateinit var etLocation: EditText

    private lateinit var imageAdapter: ImageAdapter
    private var bottomSheetDialog: BottomSheetDialog? = null
    private lateinit var tvCount: TextView

    private val POST_AD_URL = Constants.BASE_URL + "post_ad.php"

    // 1. Map Activity Launcher
    private val pickLocationLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                finalLat = data.getDoubleExtra("lat", 0.0)
                finalLng = data.getDoubleExtra("lng", 0.0)
                val address = data.getStringExtra("address") ?: ""

                // Update UI with selected address
                etLocation.setText(address)
            }
        }
    }

    private val pickImages = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            if (selectedImages.size + uris.size > 10) {
                Toast.makeText(this, "You can select up to 10 images max", Toast.LENGTH_SHORT).show()
            } else {
                selectedImages.addAll(uris)
                updateBottomSheetUI()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post_ad)

        dbHelper = ListItDbHelper(this)
        auth = FirebaseAuth.getInstance()

        mainPreview = findViewById(R.id.upload_preview)
        btnOpenUpload = findViewById(R.id.btn_upload)
        btnNew = findViewById(R.id.btn_new)
        btnUsed = findViewById(R.id.btn_used)
        val btnPost = findViewById<MaterialButton>(R.id.btn_post)
        etLocation = findViewById(R.id.et_location)

        // 2. Click Listener for Map
        etLocation.isFocusable = false
        etLocation.isClickable = true
        etLocation.setOnClickListener {
            val intent = Intent(this, PickLocationActivity::class.java)
            pickLocationLauncher.launch(intent)
        }

        btnOpenUpload.setOnClickListener { showImageBottomSheet() }
        findViewById<ImageView>(R.id.btn_close).setOnClickListener { finish() }

        btnNew.setOnClickListener { setCondition("New") }
        btnUsed.setOnClickListener { setCondition("Used") }

        btnPost.setOnClickListener {
            if (validateInputs()) {
                saveAndUploadAd()
            }
        }
    }

    private fun setCondition(condition: String) {
        selectedCondition = condition
        if (condition == "New") {
            btnNew.background.setTint(Color.parseColor("#FF913C"))
            btnNew.setTextColor(Color.WHITE)
            btnUsed.background.setTint(Color.WHITE)
            btnUsed.setTextColor(Color.BLACK)
        } else {
            btnUsed.background.setTint(Color.parseColor("#FF913C"))
            btnUsed.setTextColor(Color.WHITE)
            btnNew.background.setTint(Color.WHITE)
            btnNew.setTextColor(Color.BLACK)
        }
    }

    private fun validateInputs(): Boolean {
        if (selectedImages.isEmpty()) {
            Toast.makeText(this, "Please select at least 1 image", Toast.LENGTH_SHORT).show()
            return false
        }
        val title = findViewById<EditText>(R.id.et_title).text.toString()
        val price = findViewById<EditText>(R.id.et_price).text.toString()
        val spinner = findViewById<Spinner>(R.id.spinner_category)
        val location = etLocation.text.toString()

        if (title.isEmpty() || price.isEmpty() || spinner.selectedItemPosition == 0 || location.isEmpty()) {
            Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun saveAndUploadAd() {
        val btnPost = findViewById<MaterialButton>(R.id.btn_post)
        btnPost.isEnabled = false
        btnPost.text = "Processing..."

        val currentUserEmail = auth.currentUser?.email
        if (currentUserEmail == null) {
            Toast.makeText(this, "Please Login first", Toast.LENGTH_SHORT).show()
            return
        }
        val userId = getUserIdByEmail(currentUserEmail)
        if (userId == -1) {
            Toast.makeText(this, "User profile not found locally", Toast.LENGTH_SHORT).show()
            return
        }

        val category = findViewById<Spinner>(R.id.spinner_category).selectedItem.toString()
        val title = findViewById<EditText>(R.id.et_title).text.toString()
        val desc = findViewById<EditText>(R.id.et_description).text.toString()
        val price = findViewById<EditText>(R.id.et_price).text.toString().toDoubleOrNull() ?: 0.0
        val location = etLocation.text.toString()

        val localImagePaths = saveImagesToInternalStorage(selectedImages)

        // 3. Save to SQLite with Lat/Lng
        val localAdId = saveAdToSQLite(userId, category, title, desc, price, selectedCondition, location, localImagePaths)

        if (isNetworkAvailable()) {
            uploadAdToMySQL(localAdId, userId, category, title, desc, price, selectedCondition, location, localImagePaths)
        } else {
            Toast.makeText(this, "Ad saved locally! Will sync when online.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun saveAdToSQLite(userId: Int, category: String, title: String, desc: String, price: Double, condition: String, loc: String, paths: ArrayList<String>): Long {
        val db = dbHelper.writableDatabase
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentDate = sdf.format(Date())

        val values = ContentValues().apply {
            put("user_id", userId)
            put("category", category)
            put("title", title)
            put("description", desc)
            put("price", price)
            put("condition_type", condition)
            put("location_address", loc)
            put("status", "ACTIVE")
            put("is_synced", 0)
            put("created_at", currentDate)
            // Save Coordinates
            put("lat", finalLat)
            put("lng", finalLng)
        }

        val adId = db.insert(ListItDbHelper.TABLE_ADS, null, values)

        for ((index, path) in paths.withIndex()) {
            val imgValues = ContentValues().apply {
                put("ad_id", adId)
                put("image_url", path)
                put("is_primary", if (index == 0) 1 else 0)
            }
            db.insert(ListItDbHelper.TABLE_AD_IMAGES, null, imgValues)
        }
        return adId
    }

    private fun uploadAdToMySQL(localAdId: Long, userId: Int, category: String, title: String, desc: String, price: Double, condition: String, loc: String, paths: ArrayList<String>) {
        val queue = Volley.newRequestQueue(this)
        val btnPost = findViewById<MaterialButton>(R.id.btn_post)

        val stringRequest = object : StringRequest(Request.Method.POST, POST_AD_URL,
            StringRequest@{ response ->
                try {
                    if (response.contains("Fatal error") || response.contains("mysqli_sql_exception")) {
                        Log.e("PostAd", "Server Error: $response")
                        Toast.makeText(this, "Server Error: Database Issue.", Toast.LENGTH_LONG).show()
                        btnPost.isEnabled = true
                        btnPost.text = "Post Now"
                        return@StringRequest
                    }

                    val jsonStartIndex = response.indexOf("{")
                    if (jsonStartIndex != -1 && !response.substring(jsonStartIndex).startsWith("{main}")) {
                        val cleanResponse = response.substring(jsonStartIndex)
                        val json = JSONObject(cleanResponse)

                        if (json.getString("status") == "success") {
                            markAdAsSynced(localAdId)
                            Toast.makeText(this, "Ad Posted Successfully!", Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            Toast.makeText(this, "Server Error: ${json.getString("message")}", Toast.LENGTH_LONG).show()
                            btnPost.isEnabled = true
                            btnPost.text = "Post Now"
                        }
                    } else {
                        Log.e("PostAd", "Invalid Server Response: $response")
                        Toast.makeText(this, "Server Error: Invalid Response", Toast.LENGTH_SHORT).show()
                        btnPost.isEnabled = true
                    }
                } catch (e: Exception) {
                    Log.e("PostAd", "JSON Parsing Error: ${e.message} | Raw: $response")
                    Toast.makeText(this, "Response Error", Toast.LENGTH_SHORT).show()
                    btnPost.isEnabled = true
                }
            },
            { error ->
                Toast.makeText(this, "Network Fail. Saved locally.", Toast.LENGTH_SHORT).show()
                finish()
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["user_id"] = userId.toString()
                params["category"] = category
                params["title"] = title
                params["description"] = desc
                params["price"] = price.toString()
                params["condition"] = condition
                params["location"] = loc

                // 4. Send Lat/Lng to Server
                params["lat"] = finalLat.toString()
                params["lng"] = finalLng.toString()

                val imagesJsonArray = JSONArray()
                for (path in paths) {
                    val bitmap = BitmapFactory.decodeFile(path)
                    val base64 = bitmapToString(bitmap)
                    imagesJsonArray.put(base64)
                }
                params["images_json"] = imagesJsonArray.toString()

                return params
            }
        }

        stringRequest.retryPolicy = DefaultRetryPolicy(30000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        queue.add(stringRequest)
    }

    private fun markAdAsSynced(localAdId: Long) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply { put("is_synced", 1) }
        db.update(ListItDbHelper.TABLE_ADS, values, "ad_id = ?", arrayOf(localAdId.toString()))
    }

    private fun getUserIdByEmail(email: String): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT user_id FROM users WHERE email = ?", arrayOf(email))
        var id = -1
        if (cursor.moveToFirst()) id = cursor.getInt(0)
        cursor.close()
        return id
    }

    private fun saveImagesToInternalStorage(uris: ArrayList<Uri>): ArrayList<String> {
        val paths = ArrayList<String>()
        val folder = File(filesDir, "ad_images")
        if (!folder.exists()) folder.mkdir()

        for (uri in uris) {
            try {
                val bitmap = getResizedBitmap(uri) ?: continue
                val filename = "ad_${System.currentTimeMillis()}_${(0..1000).random()}.jpg"
                val file = File(folder, filename)
                val fos = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 60, fos)
                fos.flush()
                fos.close()
                paths.add(file.absolutePath)
            } catch (e: Exception) { e.printStackTrace() }
        }
        return paths
    }

    private fun getResizedBitmap(uri: Uri): Bitmap? {
        var inputStream: InputStream? = contentResolver.openInputStream(uri)
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream?.close()

        var inSampleSize = 1
        val reqWidth = 1000
        val reqHeight = 1000
        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
            val halfHeight: Int = options.outHeight / 2
            val halfWidth: Int = options.outWidth / 2
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

    private fun bitmapToString(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, byteArrayOutputStream)
        return Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.DEFAULT)
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    private fun showImageBottomSheet() {
        val view = layoutInflater.inflate(R.layout.bottom_sheet_images, null)
        bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog?.setContentView(view)

        val rvImages = view.findViewById<RecyclerView>(R.id.rv_images)
        val btnPick = view.findViewById<MaterialButton>(R.id.btn_pick_gallery)
        val btnDone = view.findViewById<MaterialButton>(R.id.btn_done)
        tvCount = view.findViewById(R.id.tv_image_count)

        imageAdapter = ImageAdapter(selectedImages) { position ->
            selectedImages.removeAt(position)
            updateBottomSheetUI()
        }
        rvImages.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvImages.adapter = imageAdapter
        updateBottomSheetUI()

        btnPick.setOnClickListener { pickImages.launch("image/*") }
        btnDone.setOnClickListener {
            bottomSheetDialog?.dismiss()
            updateMainScreenPreview()
        }
        bottomSheetDialog?.setOnDismissListener { updateMainScreenPreview() }
        bottomSheetDialog?.show()
    }

    private fun updateBottomSheetUI() {
        if (::imageAdapter.isInitialized) imageAdapter.notifyDataSetChanged()
        if (::tvCount.isInitialized) tvCount.text = "${selectedImages.size} images selected"
    }

    private fun updateMainScreenPreview() {
        if (selectedImages.isNotEmpty()) {
            mainPreview.setImageURI(selectedImages[0])
            btnOpenUpload.text = "Manage Photos (${selectedImages.size})"
        } else {
            mainPreview.setImageResource(R.drawable.sample_image)
            btnOpenUpload.text = "Upload Image"
        }
    }
}