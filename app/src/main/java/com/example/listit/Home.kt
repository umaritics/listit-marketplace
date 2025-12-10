package com.example.listit

import ListItDbHelper
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.HorizontalScrollView
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale


class Home : AppCompatActivity() {

    private lateinit var dbHelper: ListItDbHelper
    private lateinit var adAdapter: AdAdapter
    private var callListener: ValueEventListener? = null
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var auth: FirebaseAuth

    private val displayedAdList = ArrayList<Ad>()
    private val fullAdList = ArrayList<Ad>()

    private var searchQuery: String = ""
    private var filterCategory: String? = null
    private var filterCondition: String? = null
    private lateinit var callRef: DatabaseReference
    private var filterMinPrice: Double? = null
    private var filterMaxPrice: Double? = null
    private var filterLocation: String? = null

    private val filterLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val data = result.data!!

            if (data.hasExtra("category")) filterCategory = data.getStringExtra("category")
            if (data.hasExtra("condition")) filterCondition = data.getStringExtra("condition")
            if (data.hasExtra("location")) filterLocation = data.getStringExtra("location")
            if (data.hasExtra("minPrice")) filterMinPrice = data.getDoubleExtra("minPrice", 0.0)
            if (data.hasExtra("maxPrice")) filterMaxPrice = data.getDoubleExtra("maxPrice", 0.0)

            updateFilterUI()
            refilter()
        }
    }

    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val spokenText: ArrayList<String> = result.data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) ?: arrayListOf()
            if (spokenText.isNotEmpty()) {
                val query = spokenText[0]
                findViewById<TextInputEditText>(R.id.search_input).setText(query)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        dbHelper = ListItDbHelper(this)
        auth = FirebaseAuth.getInstance()

        val recyclerView = findViewById<RecyclerView>(R.id.rv_ads)
        recyclerView.layoutManager = GridLayoutManager(this, 2)

        adAdapter = AdAdapter(displayedAdList) { ad, position ->
            toggleSaveAd(ad, position)
        }
        recyclerView.adapter = adAdapter

        swipeRefresh = findViewById(R.id.swipe_refresh)
        swipeRefresh.setColorSchemeColors(Color.parseColor("#FF6F00"))

        swipeRefresh.setOnRefreshListener {
            loadAdsFromLocalDB()
            if (isNetworkAvailable()) {
                syncAdsFromServer()
                syncDirtyAdsToServer()
                syncSavedAdsToServer()
                syncAllUsers()
                setupCallListener()
            } else {
                swipeRefresh.isRefreshing = false
                Toast.makeText(this, "Showing local data (Offline)", Toast.LENGTH_SHORT).show()
            }
        }

        loadAdsFromLocalDB()

        if (isNetworkAvailable()) {
            syncAdsFromServer()
            syncDirtyAdsToServer()
            syncSavedAdsToServer()
            syncAllUsers()
        }

        val searchLayout = findViewById<TextInputLayout>(R.id.search_bar)
        val searchInput = findViewById<TextInputEditText>(R.id.search_input)

        searchLayout.setEndIconOnClickListener {
            val intent = Intent(this, Filters::class.java)
            filterLauncher.launch(intent)
        }

        searchLayout.setStartIconOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            try { speechLauncher.launch(intent) } catch (e: Exception) {}
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s.toString().trim()
                refilter()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        setupNavigation()
        setupCategoryBar()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        loadAdsFromLocalDB()
    }

    private fun toggleSaveAd(ad: Ad, position: Int) {
        val currentUserEmail = auth.currentUser?.email
        if (currentUserEmail == null) {
            Toast.makeText(this, "Login to save ads", Toast.LENGTH_SHORT).show()
            return
        }
        val userId = getUserIdByEmail(currentUserEmail)
        val db = dbHelper.writableDatabase

        val newStatus = !ad.isSaved
        ad.isSaved = newStatus
        adAdapter.notifyItemChanged(position)

        if (newStatus) {
            val values = ContentValues().apply {
                put("user_id", userId)
                put("ad_id", ad.id)
                put("is_deleted", 0)
                put("is_synced", 0)
                put("created_at", System.currentTimeMillis().toString())
            }
            db.insert(ListItDbHelper.TABLE_SAVED_ADS, null, values)
        } else {
            db.execSQL("UPDATE saved_ads SET is_deleted=1, is_synced=0 WHERE user_id=$userId AND ad_id=${ad.id}")
        }

        if (isNetworkAvailable()) {
            syncSavedAdsToServer()
        }
    }

    private fun syncSavedAdsToServer() {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM saved_ads WHERE is_synced = 0", null)
        val queue = Volley.newRequestQueue(this)

        if (cursor.moveToFirst()) {
            do {
                val adId = cursor.getInt(cursor.getColumnIndexOrThrow("ad_id"))
                val userId = cursor.getInt(cursor.getColumnIndexOrThrow("user_id"))
                val isDeleted = cursor.getInt(cursor.getColumnIndexOrThrow("is_deleted"))

                val action = if (isDeleted == 1) "unsave" else "save"

                val request = object : StringRequest(Request.Method.POST, Constants.BASE_URL + "save_ad.php",
                    { response ->
                        if (response.contains("success")) {
                            val wDb = dbHelper.writableDatabase
                            if (action == "unsave") {
                                wDb.execSQL("DELETE FROM saved_ads WHERE user_id=$userId AND ad_id=$adId")
                            } else {
                                wDb.execSQL("UPDATE saved_ads SET is_synced=1 WHERE user_id=$userId AND ad_id=$adId")
                            }
                        }
                    },
                    { error -> Log.e("Home", "Save Sync Error") }
                ) {
                    override fun getParams(): MutableMap<String, String> {
                        val params = HashMap<String, String>()
                        params["user_id"] = userId.toString()
                        params["ad_id"] = adId.toString()
                        params["action"] = action
                        return params
                    }
                }
                queue.add(request)
            } while (cursor.moveToNext())
        }
        cursor.close()
    }

    private fun setupCategoryBar() {
        findViewById<LinearLayout>(R.id.cat_cars).setOnClickListener { setCategoryFilter("Car") }
        findViewById<LinearLayout>(R.id.cat_mobile).setOnClickListener { setCategoryFilter("Mobile") }
        findViewById<LinearLayout>(R.id.cat_property).setOnClickListener { setCategoryFilter("Property") }
        findViewById<LinearLayout>(R.id.cat_bikes).setOnClickListener { setCategoryFilter("Bike") }
    }

    private fun setCategoryFilter(cat: String) {
        filterCategory = cat
        updateFilterUI()
        refilter()
    }

    private fun updateFilterUI() {
        val chipGroup = findViewById<ChipGroup>(R.id.chipGroupFilters)
        val filterScroll = findViewById<HorizontalScrollView>(R.id.filter_scroll_view)
        val categoryBar = findViewById<LinearLayout>(R.id.category_bar)

        chipGroup.removeAllViews()

        fun addChip(text: String, onRemove: () -> Unit) {
            val chip = Chip(this)
            chip.text = text
            chip.isCloseIconVisible = true
            chip.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#F0CDBC"))
            chip.setTextColor(Color.BLACK)
            chip.setOnCloseIconClickListener {
                onRemove()
                updateFilterUI()
                refilter()
            }
            chipGroup.addView(chip)
        }

        filterCategory?.let { addChip(it) { filterCategory = null } }
        filterCondition?.let { addChip(it) { filterCondition = null } }
        filterLocation?.let { addChip(it) { filterLocation = null } }

        if (filterMinPrice != null || filterMaxPrice != null) {
            val priceText = "Price: ${filterMinPrice?.toInt() ?: 0} - ${filterMaxPrice?.toInt() ?: "Max"}"
            addChip(priceText) {
                filterMinPrice = null
                filterMaxPrice = null
            }
        }

        if (chipGroup.childCount > 0) {
            filterScroll.visibility = View.VISIBLE
            categoryBar.visibility = View.GONE
        } else {
            filterScroll.visibility = View.GONE
            categoryBar.visibility = View.VISIBLE
        }
    }

    private fun refilter() {
        val filtered = ArrayList<Ad>()
        val queryLower = searchQuery.lowercase(Locale.getDefault())

        for (ad in fullAdList) {
            var matches = true
            if (searchQuery.isNotEmpty()) {
                if (!ad.title.lowercase().contains(queryLower) &&
                    !ad.location.lowercase().contains(queryLower)) {
                    matches = false
                }
            }
            if (filterCategory != null && !ad.category.contains(filterCategory!!, true)) matches = false
            if (filterCondition != null && !ad.condition.equals(filterCondition, true)) matches = false
            if (filterMinPrice != null && ad.price < filterMinPrice!!) matches = false
            if (filterMaxPrice != null && ad.price > filterMaxPrice!!) matches = false
            if (filterLocation != null && !ad.location.contains(filterLocation!!, true)) matches = false

            if (matches) filtered.add(ad)
        }
        adAdapter.updateList(filtered)
    }

    private fun loadAdsFromLocalDB() {
        fullAdList.clear()
        val currentUserEmail = auth.currentUser?.email ?: ""
        val userId = getUserIdByEmail(currentUserEmail)

        val db = dbHelper.readableDatabase
        val query = """
            SELECT a.ad_id, a.title, a.price, a.location_address, a.created_at, a.is_synced, i.image_url, a.category, a.condition_type,
            (SELECT count(*) FROM saved_ads s WHERE s.ad_id = a.ad_id AND s.user_id = $userId AND s.is_deleted = 0) as is_saved
            FROM ${ListItDbHelper.TABLE_ADS} a
            LEFT JOIN ${ListItDbHelper.TABLE_AD_IMAGES} i ON a.ad_id = i.ad_id AND i.is_primary = 1
            WHERE a.is_deleted = 0 
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
                    imagePath = cursor.getString(6),
                    category = cursor.getString(7),
                    condition = cursor.getString(8),
                    isSaved = cursor.getInt(9) > 0
                )
                fullAdList.add(ad)
            } while (cursor.moveToNext())
        }
        cursor.close()

        displayedAdList.clear()
        displayedAdList.addAll(fullAdList)
        adAdapter.notifyDataSetChanged()
    }

    private fun getUserIdByEmail(email: String): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT user_id FROM users WHERE email = ?", arrayOf(email))
        var id = -1
        if (cursor.moveToFirst()) {
            id = cursor.getInt(0)
        }
        cursor.close()
        return id
    }

    // --- UPDATED SYNC LOGIC FOR EDITS ---
    private fun syncAdsFromServer() {
        val queue = Volley.newRequestQueue(this)
        val url = Constants.BASE_URL + "get_ads.php"

        val request = object : StringRequest(Request.Method.GET, url,
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

                                // FIX: Always REPLACE data (handles updates)
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
                                    put("is_deleted", 0)
                                    put("created_at", obj.getString("created_at"))
                                }

                                // Replace row in ADS table
                                db.insertWithOnConflict(ListItDbHelper.TABLE_ADS, null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)

                                // FIX: Delete OLD images for this ad, then Insert NEW ones
                                db.delete(ListItDbHelper.TABLE_AD_IMAGES, "ad_id = ?", arrayOf(serverAdId.toString()))

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
                            loadAdsFromLocalDB()
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
                swipeRefresh.isRefreshing = false
            },
            { error -> swipeRefresh.isRefreshing = false }
        ) { }
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
                try {
                    val jsonStartIndex = response.indexOf("{")
                    if (jsonStartIndex != -1) {
                        val cleanResponse = response.substring(jsonStartIndex)
                        val json = JSONObject(cleanResponse)
                        if (json.getString("status") == "success") {
                            val serverAdId = json.getInt("ad_id")
                            val db = dbHelper.writableDatabase
                            db.execSQL("UPDATE ad_images SET ad_id = $serverAdId WHERE ad_id = $localAdId")
                            db.execSQL("UPDATE ads SET ad_id = $serverAdId, is_synced = 1 WHERE ad_id = $localAdId")
                            loadAdsFromLocalDB()
                        }
                    }
                } catch (e: Exception) { Log.e("Home", "Sync Response Error: ${e.message}") }
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
                            val bitmap = android.graphics.BitmapFactory.decodeFile(path)
                            if (bitmap != null) {
                                val baos = ByteArrayOutputStream()
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, baos)
                                val base64 = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.DEFAULT)
                                imagesJsonArray.put(base64)
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
                params["images_json"] = imagesJsonArray.toString()
                return params
            }
        }
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
                                    put("fcm_token", obj.optString("fcm_token", ""))
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
    // Add this variable at the top of Home class
    private var lastProcessedTimestamp: Long = 0L

    private fun setupCallListener() {
        val currentUserEmail = auth.currentUser?.email ?: return
        val currentUserId = getUserIdByEmail(currentUserEmail)

        val db = FirebaseDatabase.getInstance("https://listit-749b1-default-rtdb.firebaseio.com/")
        callRef = db.getReference("calls").child(currentUserId.toString())

        callListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val status = snapshot.child("status").getValue(String::class.java)
                    val type = snapshot.child("type").getValue(String::class.java)
                    val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L

                    // 1. DUPLICATE CHECK: If we already handled this specific call time, STOP.
                    if (timestamp == lastProcessedTimestamp) {
                        return
                    }

                    // 2. CHECK: Is it ringing?
                    if (status == "ringing" && type == "video") {
                        // Mark this timestamp as processed so we don't launch again for the same call
                        lastProcessedTimestamp = timestamp

                        val callerName = snapshot.child("callerName").getValue(String::class.java) ?: "Unknown"
                        val callerId = snapshot.child("callerId").getValue(String::class.java) ?: ""
                        val channelName = snapshot.child("channelName").getValue(String::class.java) ?: ""

                        Log.d("CallListener", "Call detected! Launching IncomingCallActivity")

                        val intent = Intent(this@Home, IncomingCallActivity::class.java)
                        intent.putExtra("CALLER_NAME", callerName)
                        intent.putExtra("CALLER_ID", callerId)
                        intent.putExtra("CHANNEL_NAME", channelName)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(intent)

                        // 3. Update status immediately to prevent loop
                        snapshot.ref.child("status").setValue("ringing_received")
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("CallListener", "Error listening for calls: ${error.message}")
            }
        }
        callRef.addValueEventListener(callListener!!)
    }
}