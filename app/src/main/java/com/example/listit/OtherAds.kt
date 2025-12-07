package com.example.listit

import ListItDbHelper
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File

class OtherAds : AppCompatActivity() {

    private lateinit var dbHelper: ListItDbHelper
    private var userId: Int = -1
    private val adList = ArrayList<Ad>()
    private lateinit var adapter: OtherAdsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_other_ads)

        dbHelper = ListItDbHelper(this)

        // Get User ID from Intent
        userId = intent.getIntExtra("USER_ID", -1)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (userId != -1) {
            setupRecyclerView()
            loadUserProfile(userId)
            loadUserAds(userId)
        }

        setupNavigation()
    }

    private fun setupRecyclerView() {
        val rv = findViewById<RecyclerView>(R.id.rv_other_ads)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = OtherAdsAdapter(adList) { ad ->
            // Click listener: Go to ViewAd
            val intent = Intent(this, ViewAd::class.java)
            intent.putExtra("AD_ID", ad.id)
            startActivity(intent)
        }
        rv.adapter = adapter
    }

    private fun loadUserProfile(id: Int) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT full_name, profile_image_url FROM users WHERE user_id = ?", arrayOf(id.toString()))

        if (cursor.moveToFirst()) {
            val name = cursor.getString(0)
            val imgPath = cursor.getString(1)

            findViewById<TextView>(R.id.tvUserName).text = name

            val profileImg = findViewById<ImageView>(R.id.imgProfile)
            if (!imgPath.isNullOrEmpty()) {
                if (imgPath.startsWith("/")) {
                    val imgFile = File(imgPath)
                    if (imgFile.exists()) {
                        val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                        profileImg.setImageBitmap(bitmap)
                    }
                } else {
                    val fullUrl = if (imgPath.startsWith("http")) imgPath else Constants.BASE_URL + imgPath
                    Glide.with(this).load(fullUrl).placeholder(R.drawable.ic_profile_placeholder).into(profileImg)
                }
            }
        }
        cursor.close()
    }

    private fun loadUserAds(id: Int) {
        adList.clear()
        val db = dbHelper.readableDatabase

        // Fetch ACTIVE ads for this user
        val query = """
            SELECT a.*, i.image_url 
            FROM ads a 
            LEFT JOIN ad_images i ON a.ad_id = i.ad_id AND i.is_primary = 1 
            WHERE a.user_id = ? AND a.status = 'ACTIVE' AND a.is_deleted = 0
            ORDER BY a.created_at DESC
        """
        val cursor = db.rawQuery(query, arrayOf(id.toString()))

        if (cursor.moveToFirst()) {
            do {
                adList.add(mapCursorToAd(cursor))
            } while (cursor.moveToNext())
        }
        cursor.close()

        // Update Count
        findViewById<TextView>(R.id.tvAdCount).text = adList.size.toString()
        adapter.notifyDataSetChanged()
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

    private fun setupNavigation() {
        findViewById<ImageView>(R.id.home_ic).setOnClickListener {
            startActivity(Intent(this, Home::class.java))
            finish()
            overridePendingTransition(0, 0)
        }
        findViewById<ImageView>(R.id.chat_ic).setOnClickListener {
            startActivity(Intent(this, Buying::class.java))
            finish()
            overridePendingTransition(0, 0)
        }
        findViewById<ImageView>(R.id.add_ic)?.setOnClickListener {
            startActivity(Intent(this, PostAd::class.java))
            finish()
            overridePendingTransition(0, 0)
        }
        findViewById<ImageView>(R.id.fav_ic).setOnClickListener {
            startActivity(Intent(this, MyAds::class.java))
            finish()
            overridePendingTransition(0, 0)
        }
        findViewById<ImageView>(R.id.prof_ic).setOnClickListener {
            startActivity(Intent(this, Profile::class.java))
            finish()
            overridePendingTransition(0, 0)
        }
    }
}