package com.example.listit

import ListItDbHelper
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

class Home : AppCompatActivity() {

    private lateinit var dbHelper: ListItDbHelper
    private lateinit var adAdapter: AdAdapter

    // Two lists: one for display, one for backup (filtering)
    private val displayedAdList = ArrayList<Ad>()
    private val fullAdList = ArrayList<Ad>()

    // VOICE SEARCH LAUNCHER
    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val spokenText: ArrayList<String> = result.data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) ?: arrayListOf()
            if (spokenText.isNotEmpty()) {
                val query = spokenText[0]
                // Set text in search bar (Note: setText triggers TextWatcher)
                findViewById<TextInputEditText>(R.id.search_input).setText(query)
                // filterAds(query) // Removed direct search call as requested
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        dbHelper = ListItDbHelper(this)

        val recyclerView = findViewById<RecyclerView>(R.id.rv_ads)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        adAdapter = AdAdapter(displayedAdList)
        recyclerView.adapter = adAdapter

        // Load Data
        loadAdsFromLocalDB()

        if (isNetworkAvailable()) {
            syncAdsFromServer()
            syncDirtyAdsToServer()
            syncAllUsers()
        }

        // --- SEARCH LOGIC ---
        val searchLayout = findViewById<TextInputLayout>(R.id.search_bar)
        val searchInput = findViewById<TextInputEditText>(R.id.search_input)

        // 1. Voice Icon Click
        searchLayout.setEndIconOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say something (e.g. 'Toyota')")
            try {
                speechLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Voice search not supported", Toast.LENGTH_SHORT).show()
            }
        }

        // 2. Text Typing
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                // filterAds(s.toString()) // Commented out: Search logic to be implemented later
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        setupNavigation()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun filterAds(query: String) {
        val filteredList = ArrayList<Ad>()

        if (query.isEmpty()) {
            filteredList.addAll(fullAdList)
        } else {
            val lowercaseQuery = query.lowercase(Locale.getDefault())
            for (ad in fullAdList) {
                // Filter by Title, Category, or Location
                if (ad.title.lowercase(Locale.getDefault()).contains(lowercaseQuery) ||
                    ad.location.lowercase(Locale.getDefault()).contains(lowercaseQuery)) {
                    filteredList.add(ad)
                }
            }
        }

        // Update Adapter
        adAdapter.updateList(filteredList)
    }

    private fun loadAdsFromLocalDB() {
        fullAdList.clear()
        val db = dbHelper.readableDatabase
        val query = """
            SELECT a.ad_id, a.title, a.price, a.location_address, a.created_at, a.is_synced, i.image_url
            FROM ${ListItDbHelper.TABLE_ADS} a
            LEFT JOIN ${ListItDbHelper.TABLE_AD_IMAGES} i ON a.ad_id = i.ad_id AND i.is_primary = 1
            ORDER BY a.created_at DESC
        """
        val cursor = db.rawQuery(query, null)

        if (cursor.moveToFirst()) {
            do {
                val ad = Ad(
                    id = cursor.getInt(0),
                    title = cursor.getString(1),
                    price = cursor.getDouble(2),
                    location = cursor.getString(3),
                    date = cursor.getString(4),
                    isSynced = cursor.getInt(5),
                    imagePath = cursor.getString(6)
                )
                fullAdList.add(ad)
            } while (cursor.moveToNext())
        }
        cursor.close()

        // Initial state: Display everything
        displayedAdList.clear()
        displayedAdList.addAll(fullAdList)
        adAdapter.notifyDataSetChanged()
    }

    // ... (Keep syncAdsFromServer, syncDirtyAdsToServer, syncAllUsers, uploadSingleAd exactly as they were) ...

    private fun syncAdsFromServer() {
        val queue = Volley.newRequestQueue(this)
        val url = Constants.BASE_URL + "get_ads.php"

        val request = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val jsonStartIndex = response.indexOf("{")
                    if (jsonStartIndex != -1) {
                        val cleanResponse = response.substring(jsonStartIndex)
                        val json = JSONObject(cleanResponse)
                        if (json.getString("status") == "success") {
                            val adsArray = json.getJSONArray("data")
                            val db = dbHelper.writableDatabase

                            for (i in 0 until adsArray.length()) {
                                val obj = adsArray.getJSONObject(i)
                                val serverAdId = obj.getInt("ad_id")

                                val check = db.rawQuery("SELECT ad_id FROM ads WHERE ad_id = ?", arrayOf(serverAdId.toString()))
                                if (!check.moveToFirst()) {
                                    val values = ContentValues().apply {
                                        put("ad_id", serverAdId)
                                        put("user_id", obj.getInt("user_id"))
                                        put("category", obj.getString("category"))
                                        put("title", obj.getString("title"))
                                        put("description", obj.getString("description"))
                                        put("price", obj.getDouble("price"))
                                        put("condition_type", obj.getString("condition_type"))
                                        put("location_address", obj.getString("location_address"))
                                        put("status", obj.getString("status"))
                                        put("lat", obj.optDouble("lat", 0.0))
                                        put("lng", obj.optDouble("lng", 0.0))
                                        put("is_synced", 1)
                                        put("created_at", obj.getString("created_at"))
                                    }
                                    db.insert(ListItDbHelper.TABLE_ADS, null, values)

                                    val imagesArray = obj.getJSONArray("images")
                                    for (j in 0 until imagesArray.length()) {
                                        val imgPath = imagesArray.getString(j)
                                        val imgValues = ContentValues().apply {
                                            put("ad_id", serverAdId)
                                            put("image_url", imgPath)
                                            put("is_primary", if (j == 0) 1 else 0)
                                        }
                                        db.insert(ListItDbHelper.TABLE_AD_IMAGES, null, imgValues)
                                    }
                                }
                                check.close()
                            }
                            loadAdsFromLocalDB()
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            },
            { error -> Log.e("Home", "Sync Error: ${error.message}") }
        )
        queue.add(request)
    }

    private fun syncDirtyAdsToServer() {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM ads WHERE is_synced = 0", null)

        if (cursor.moveToFirst()) {
            val queue = Volley.newRequestQueue(this)
            do {
                val adId = cursor.getInt(cursor.getColumnIndexOrThrow("ad_id"))
                val userId = cursor.getInt(cursor.getColumnIndexOrThrow("user_id"))
                val category = cursor.getString(cursor.getColumnIndexOrThrow("category"))
                val title = cursor.getString(cursor.getColumnIndexOrThrow("title"))
                val desc = cursor.getString(cursor.getColumnIndexOrThrow("description"))
                val price = cursor.getDouble(cursor.getColumnIndexOrThrow("price"))
                val condition = cursor.getString(cursor.getColumnIndexOrThrow("condition_type"))
                val location = cursor.getString(cursor.getColumnIndexOrThrow("location_address"))

                val lat = cursor.getDouble(cursor.getColumnIndexOrThrow("lat"))
                val lng = cursor.getDouble(cursor.getColumnIndexOrThrow("lng"))

                val imagePaths = ArrayList<String>()
                val imgCursor = db.rawQuery("SELECT image_url FROM ad_images WHERE ad_id = ?", arrayOf(adId.toString()))
                if (imgCursor.moveToFirst()) {
                    do { imagePaths.add(imgCursor.getString(0)) } while (imgCursor.moveToNext())
                }
                imgCursor.close()

                uploadSingleAd(adId, userId, category, title, desc, price, condition, location, lat, lng, imagePaths, queue)

            } while (cursor.moveToNext())
        }
        cursor.close()
    }

    private fun uploadSingleAd(localAdId: Int, userId: Int, category: String, title: String, desc: String, price: Double, condition: String, location: String, lat: Double, lng: Double, imagePaths: ArrayList<String>, queue: com.android.volley.RequestQueue) {
        val url = Constants.BASE_URL + "post_ad.php"
        val stringRequest = object : StringRequest(Request.Method.POST, url,
            { response ->
                if (response.contains("success")) {
                    val db = dbHelper.writableDatabase
                    val values = ContentValues().apply { put("is_synced", 1) }
                    db.update(ListItDbHelper.TABLE_ADS, values, "ad_id = ?", arrayOf(localAdId.toString()))
                }
            },
            { error -> Log.e("Home", "Failed to sync ad $localAdId") }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["user_id"] = userId.toString()
                params["category"] = category
                params["title"] = title
                params["description"] = desc
                params["price"] = price.toString()
                params["condition"] = condition
                params["location"] = location
                params["lat"] = lat.toString()
                params["lng"] = lng.toString()

                val imagesJsonArray = JSONArray()
                for (path in imagePaths) {
                    try {
                        val file = File(path)
                        if (file.exists()) {
                            val bitmap = BitmapFactory.decodeFile(path)
                            if (bitmap != null) {
                                val baos = ByteArrayOutputStream()
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, baos)
                                val base64 = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
                                imagesJsonArray.put(base64)
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
                params["images_json"] = imagesJsonArray.toString()
                return params
            }
        }
        stringRequest.retryPolicy = DefaultRetryPolicy(30000, 0, 1f)
        queue.add(stringRequest)
    }

    private fun syncAllUsers() {
        val queue = Volley.newRequestQueue(this)
        val url = Constants.BASE_URL + "get_users.php"

        val request = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val jsonStartIndex = response.indexOf("{")
                    if (jsonStartIndex != -1) {
                        val cleanResponse = response.substring(jsonStartIndex)
                        val json = JSONObject(cleanResponse)
                        if (json.getString("status") == "success") {
                            val usersArray = json.getJSONArray("data")
                            val db = dbHelper.writableDatabase

                            for (i in 0 until usersArray.length()) {
                                val obj = usersArray.getJSONObject(i)
                                val serverUserId = obj.getInt("user_id")

                                val values = ContentValues().apply {
                                    put("user_id", serverUserId)
                                    put("full_name", obj.getString("full_name"))
                                    put("email", obj.getString("email"))
                                    put("phone_number", obj.getString("phone_number"))
                                    put("profile_image_url", obj.getString("profile_image_url"))
                                }
                                db.insertWithOnConflict(ListItDbHelper.TABLE_USERS, null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
                            }
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            },
            { error -> Log.e("Home", "User Sync Error: ${error.message}") }
        )
        queue.add(request)
    }

    private fun setupNavigation() {
        findViewById<ImageView>(R.id.cars).setOnClickListener { startActivity(Intent(this, Category_Car::class.java)) }
        findViewById<LinearLayout>(R.id.category_bar).setOnClickListener { startActivity(Intent(this, Filters::class.java)) }
        findViewById<ImageView>(R.id.home_ic).setOnClickListener { }
        findViewById<ImageView>(R.id.chat_ic).setOnClickListener { startActivity(Intent(this, Buying::class.java)) }
        findViewById<ImageView>(R.id.add_ic)?.setOnClickListener { startActivity(Intent(this, PostAd::class.java)) }
        findViewById<ImageView>(R.id.fav_ic).setOnClickListener { startActivity(Intent(this, MyAds::class.java)) }
        findViewById<ImageView>(R.id.prof_ic).setOnClickListener { startActivity(Intent(this, Profile::class.java)) }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
}