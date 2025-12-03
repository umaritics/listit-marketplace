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
import android.util.Base64
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

class Home : AppCompatActivity() {

    private lateinit var dbHelper: ListItDbHelper
    private lateinit var adAdapter: AdAdapter
    private val adList = ArrayList<Ad>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        dbHelper = ListItDbHelper(this)

        // 1. Setup RecyclerView with GRID LAYOUT (2 Columns)
        val recyclerView = findViewById<RecyclerView>(R.id.rv_ads)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        adAdapter = AdAdapter(adList)
        recyclerView.adapter = adAdapter

        // 2. Load Local Data Immediately (Offline First)
        loadAdsFromLocalDB()

        // 3. Sync if Online
        if (isNetworkAvailable()) {
            syncAdsFromServer()
            syncDirtyAdsToServer() // This now contains real upload logic
        }

        setupNavigation()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun loadAdsFromLocalDB() {
        adList.clear()
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
                adList.add(ad)
            } while (cursor.moveToNext())
        }
        cursor.close()
        adAdapter.notifyDataSetChanged()
    }

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
                                        put("is_synced", 1)
                                        put("created_at", obj.getString("created_at"))
                                    }
                                    db.insert(ListItDbHelper.TABLE_ADS, null, values)

                                    val imgUrl = obj.optString("image_url", "")
                                    if (imgUrl.isNotEmpty()) {
                                        val imgValues = ContentValues().apply {
                                            put("ad_id", serverAdId)
                                            put("image_url", imgUrl)
                                            put("is_primary", 1)
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

    // --- REAL UPLOAD LOGIC ---
    private fun syncDirtyAdsToServer() {
        val db = dbHelper.readableDatabase
        // Find ads that are NOT synced yet
        val cursor = db.rawQuery("SELECT * FROM ads WHERE is_synced = 0", null)

        if (cursor.moveToFirst()) {
            val queue = Volley.newRequestQueue(this)

            do {
                // Extract Data from Cursor
                val adId = cursor.getInt(cursor.getColumnIndexOrThrow("ad_id"))
                val userId = cursor.getInt(cursor.getColumnIndexOrThrow("user_id"))
                val category = cursor.getString(cursor.getColumnIndexOrThrow("category"))
                val title = cursor.getString(cursor.getColumnIndexOrThrow("title"))
                val desc = cursor.getString(cursor.getColumnIndexOrThrow("description"))
                val price = cursor.getDouble(cursor.getColumnIndexOrThrow("price"))
                val condition = cursor.getString(cursor.getColumnIndexOrThrow("condition_type"))
                val location = cursor.getString(cursor.getColumnIndexOrThrow("location_address"))

                // Fetch Images for this Ad
                val imagePaths = ArrayList<String>()
                val imgCursor = db.rawQuery("SELECT image_url FROM ad_images WHERE ad_id = ?", arrayOf(adId.toString()))
                if (imgCursor.moveToFirst()) {
                    do {
                        imagePaths.add(imgCursor.getString(0))
                    } while (imgCursor.moveToNext())
                }
                imgCursor.close()

                // Upload using Volley
                uploadSingleAd(adId, userId, category, title, desc, price, condition, location, imagePaths, queue)

            } while (cursor.moveToNext())
        }
        cursor.close()
    }

    private fun uploadSingleAd(localAdId: Int, userId: Int, category: String, title: String, desc: String, price: Double, condition: String, location: String, imagePaths: ArrayList<String>, queue: com.android.volley.RequestQueue) {

        val url = Constants.BASE_URL + "post_ad.php"

        val stringRequest = object : StringRequest(Request.Method.POST, url,
            { response ->
                // On Success: Mark as Synced locally
                if (response.contains("success")) {
                    val db = dbHelper.writableDatabase
                    val values = ContentValues().apply { put("is_synced", 1) }
                    db.update(ListItDbHelper.TABLE_ADS, values, "ad_id = ?", arrayOf(localAdId.toString()))
                    // Reload to show checkmark or synced status if UI had one
                    Log.d("Home", "Ad $localAdId synced successfully")
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

                // Encode Images
                val imagesJsonArray = JSONArray()
                for (path in imagePaths) {
                    try {
                        val file = File(path)
                        if (file.exists()) {
                            val bitmap = BitmapFactory.decodeFile(path)
                            if (bitmap != null) {
                                val baos = ByteArrayOutputStream()
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos)
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
        // Long timeout for image upload
        stringRequest.retryPolicy = DefaultRetryPolicy(30000, 0, 1f)
        queue.add(stringRequest)
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