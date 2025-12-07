package com.example.listit

import ListItDbHelper
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.firebase.auth.FirebaseAuth

class MyAds : AppCompatActivity() {

    private lateinit var dbHelper: ListItDbHelper
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: MyAdsAdapter
    private val adList = ArrayList<Ad>()
    private var isMyAdsTab = true // Default tab

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_my_ads)

        dbHelper = ListItDbHelper(this)
        auth = FirebaseAuth.getInstance()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initUI()
    }

    private fun initUI() {
        val rv = findViewById<RecyclerView>(R.id.rv_my_ads)
        rv.layoutManager = LinearLayoutManager(this)

        loadMyAds() // Initial Load

        // Tab Clicks
        findViewById<TextView>(R.id.tabAds).setOnClickListener {
            isMyAdsTab = true
            updateTabsUI()
            loadMyAds()
        }

        findViewById<TextView>(R.id.tabFav).setOnClickListener {
            isMyAdsTab = false
            updateTabsUI()
            loadFavourites()
        }

        setupNavigation()
    }

    private fun updateTabsUI() {
        val tabAds = findViewById<TextView>(R.id.tabAds)
        val tabFav = findViewById<TextView>(R.id.tabFav)
        val indicator = findViewById<View>(R.id.indicator)

        if (isMyAdsTab) {
            tabAds.setTextColor(Color.parseColor("#FF6F00"))
            tabFav.setTextColor(Color.GRAY)
            indicator.animate().translationX(0f).setDuration(200).start()
        } else {
            tabAds.setTextColor(Color.GRAY)
            tabFav.setTextColor(Color.parseColor("#FF6F00"))
            indicator.animate().translationX(tabAds.width.toFloat()).setDuration(200).start()
        }
    }

    private fun loadMyAds() {
        adList.clear()
        val userId = getUserId()
        val db = dbHelper.readableDatabase

        // Fetch ads created by this user
        val cursor = db.rawQuery("""
            SELECT a.*, i.image_url 
            FROM ads a 
            LEFT JOIN ad_images i ON a.ad_id = i.ad_id AND i.is_primary = 1 
            WHERE a.user_id = ? 
            ORDER BY a.created_at DESC
        """, arrayOf(userId.toString()))

        if (cursor.moveToFirst()) {
            do {
                adList.add(mapCursorToAd(cursor))
            } while (cursor.moveToNext())
        }
        cursor.close()

        // Setup Adapter
        setupAdapter(isFav = false)
    }

    private fun loadFavourites() {
        adList.clear()
        val userId = getUserId()
        val db = dbHelper.readableDatabase

        // Fetch ads saved by this user
        val query = """
            SELECT a.*, i.image_url 
            FROM ads a 
            JOIN saved_ads s ON a.ad_id = s.ad_id 
            LEFT JOIN ad_images i ON a.ad_id = i.ad_id AND i.is_primary = 1 
            WHERE s.user_id = ? AND s.is_deleted = 0
        """
        val cursor = db.rawQuery(query, arrayOf(userId.toString()))

        if (cursor.moveToFirst()) {
            do {
                adList.add(mapCursorToAd(cursor))
            } while (cursor.moveToNext())
        }
        cursor.close()

        // Setup Adapter
        setupAdapter(isFav = true)
    }

    private fun setupAdapter(isFav: Boolean) {
        adapter = MyAdsAdapter(
            adList = adList,
            isFavTab = isFav,
            onActionClick = { ad ->
                if (isFav) removeFavorite(ad) else handleMyAdAction(ad)
            },
            onEditClick = { ad ->
                // OPEN POST AD IN EDIT MODE
                val intent = Intent(this, PostAd::class.java)
                intent.putExtra("AD_ID", ad.id)
                startActivity(intent)
            },
            onItemClick = { ad ->
                val intent = Intent(this, ViewAd::class.java)
                intent.putExtra("AD_ID", ad.id)
                startActivity(intent)
            }
        )
        findViewById<RecyclerView>(R.id.rv_my_ads).adapter = adapter
    }

    private fun handleMyAdAction(ad: Ad) {
        val db = dbHelper.writableDatabase

        if (ad.isDeleted == 0) {
            // ACTION: REMOVE (Soft Delete)
            db.execSQL("UPDATE ads SET is_deleted = 1, is_synced = 0 WHERE ad_id = ${ad.id}")
            syncStatusToServer(ad.id, 1) // 1 = deleted
            Toast.makeText(this, "Ad Removed", Toast.LENGTH_SHORT).show()
        } else {
            // ACTION: REPUBLISH
            db.execSQL("UPDATE ads SET is_deleted = 0, is_synced = 0 WHERE ad_id = ${ad.id}")
            syncStatusToServer(ad.id, 0) // 0 = active
            Toast.makeText(this, "Ad Republished", Toast.LENGTH_SHORT).show()
        }
        loadMyAds() // Refresh list
    }

    private fun removeFavorite(ad: Ad) {
        val userId = getUserId()
        val db = dbHelper.writableDatabase

        // Soft delete locally first (Sync logic support)
        db.execSQL("UPDATE saved_ads SET is_deleted = 1, is_synced = 0 WHERE user_id = $userId AND ad_id = ${ad.id}")

        // Sync
        val queue = Volley.newRequestQueue(this)
        val url = Constants.BASE_URL + "save_ad.php"
        val request = object : StringRequest(Request.Method.POST, url,
            { response ->
                if (response.contains("success")) {
                    dbHelper.writableDatabase.execSQL("DELETE FROM saved_ads WHERE user_id=$userId AND ad_id=${ad.id}")
                }
            },
            { }
        ) {
            override fun getParams(): MutableMap<String, String> {
                return hashMapOf("user_id" to userId.toString(), "ad_id" to ad.id.toString(), "action" to "unsave")
            }
        }
        queue.add(request)

        loadFavourites() // Refresh list
    }

    private fun syncStatusToServer(adId: Int, isDeleted: Int) {
        val queue = Volley.newRequestQueue(this)
        val url = Constants.BASE_URL + "update_ad_status.php"

        val request = object : StringRequest(Request.Method.POST, url,
            { response ->
                if(response.contains("success")) {
                    // Mark local as synced
                    dbHelper.writableDatabase.execSQL("UPDATE ads SET is_synced = 1 WHERE ad_id = $adId")
                }
            },
            { }
        ) {
            override fun getParams(): MutableMap<String, String> {
                return hashMapOf("ad_id" to adId.toString(), "is_deleted" to isDeleted.toString())
            }
        }
        queue.add(request)
    }

    private fun mapCursorToAd(cursor: android.database.Cursor): Ad {
        return Ad(
            id = cursor.getInt(cursor.getColumnIndexOrThrow("ad_id")),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            price = cursor.getDouble(cursor.getColumnIndexOrThrow("price")),
            location = cursor.getString(cursor.getColumnIndexOrThrow("location_address")),
            category = cursor.getString(cursor.getColumnIndexOrThrow("category")),
            condition = cursor.getString(cursor.getColumnIndexOrThrow("condition_type")),
            imagePath = cursor.getString(cursor.getColumnIndexOrThrow("image_url")),
            date = cursor.getString(cursor.getColumnIndexOrThrow("created_at")),
            isSynced = cursor.getInt(cursor.getColumnIndexOrThrow("is_synced")),
            isDeleted = cursor.getInt(cursor.getColumnIndexOrThrow("is_deleted"))
        )
    }

    private fun getUserId(): Int {
        val email = auth.currentUser?.email ?: return -1
        val cursor = dbHelper.readableDatabase.rawQuery("SELECT user_id FROM users WHERE email=?", arrayOf(email))
        var id = -1
        if(cursor.moveToFirst()) id = cursor.getInt(0)
        cursor.close()
        return id
    }

    private fun setupNavigation() {
        findViewById<ImageView>(R.id.home_ic).setOnClickListener {
            startActivity(Intent(this, Home::class.java))
            overridePendingTransition(0, 0)
        }
        findViewById<ImageView>(R.id.chat_ic).setOnClickListener {
            startActivity(Intent(this, Buying::class.java))
            overridePendingTransition(0, 0)
        }
        findViewById<ImageView>(R.id.add_ic)?.setOnClickListener {
            startActivity(Intent(this, PostAd::class.java))
            overridePendingTransition(0, 0)
        }
        // fav_ic is current activity
        findViewById<ImageView>(R.id.prof_ic).setOnClickListener {
            startActivity(Intent(this, Profile::class.java))
            overridePendingTransition(0, 0)
        }
    }

    // REFRESH LIST when returning from Edit (PostAd)
    override fun onResume() {
        super.onResume()
        if (isMyAdsTab) loadMyAds() else loadFavourites()
    }
}