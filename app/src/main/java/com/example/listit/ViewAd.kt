package com.example.listit

import ListItDbHelper
import android.content.ContentValues
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

class ViewAd : AppCompatActivity() {

    private lateinit var dbHelper: ListItDbHelper

    private lateinit var auth: FirebaseAuth
    private var adId: Int = -1
    private var sellerPhoneNumber: String = ""

    // NEW: Variable to hold the seller's token
    private var sellerFcmToken: String = ""

    private lateinit var mapView: MapView
    private var isSaved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize OpenStreetMap
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))

        enableEdgeToEdge()
        setContentView(R.layout.activity_view_ad)

        dbHelper = ListItDbHelper(this)
        auth = FirebaseAuth.getInstance()
        adId = intent.getIntExtra("AD_ID", -1)

        if (adId != -1) {
            loadAdDetails(adId)
            loadAdImages(adId)
            checkIfSaved()
        } else {
            Toast.makeText(this, "Error loading ad", Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<ImageView>(R.id.back_icon).setOnClickListener { finish() }

        // Heart Click Listener
        findViewById<ImageView>(R.id.fav_icon).setOnClickListener {
            toggleSaveState()
        }

        // Call Button
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_call).setOnClickListener {
            if (sellerPhoneNumber.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_DIAL)
                intent.data = Uri.parse("tel:$sellerPhoneNumber")
                startActivity(intent)
            } else {
                Toast.makeText(this, "Phone number not available", Toast.LENGTH_SHORT).show()
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun checkIfSaved() {
        val currentUserEmail = auth.currentUser?.email ?: return
        val userId = getUserIdByEmail(currentUserEmail)

        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT count(*) FROM saved_ads WHERE user_id = ? AND ad_id = ? AND is_deleted = 0", arrayOf(userId.toString(), adId.toString()))

        if (cursor.moveToFirst()) {
            isSaved = cursor.getInt(0) > 0
        }
        cursor.close()
        updateFavIcon()
    }

    private fun updateFavIcon() {
        val favIcon = findViewById<ImageView>(R.id.fav_icon)
        favIcon.clearColorFilter()

        if (isSaved) {
            favIcon.setImageResource(R.drawable.ic_favourite) // Filled
        } else {
            favIcon.setImageResource(R.drawable.ic_favorite) // Outline
        }
    }

    private fun toggleSaveState() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Login to save ads", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = getUserIdByEmail(currentUser.email!!)
        val db = dbHelper.writableDatabase

        isSaved = !isSaved
        updateFavIcon()

        if (isSaved) {
            // 1. Save Locally
            val values = ContentValues().apply {
                put("user_id", userId)
                put("ad_id", adId)
                put("is_deleted", 0)
                put("is_synced", 0)
                put("created_at", System.currentTimeMillis().toString())
            }
            db.insert(ListItDbHelper.TABLE_SAVED_ADS, null, values)

            // 2. Sync to Server
            syncSaveToServer(userId, adId, "save")

            // 3. SEND NOTIFICATION (Logic Added Here)
            if (sellerFcmToken.isNotEmpty()) {
                val saverName = currentUser.displayName ?: "Someone"
                // Using lifecycleScope to run the suspend function
                lifecycleScope.launch {
                    try {
                        PushNotificationSender.sendAdSavedNotification(sellerFcmToken, saverName)
                        Log.d("ViewAd", "Notification sent to seller")
                    } catch (e: Exception) {
                        Log.e("ViewAd", "Failed to send notification: ${e.message}")
                    }
                }
            } else {
                Log.d("ViewAd", "Seller token not found, cannot send notification")
            }

        } else {
            // Unsave Logic
            db.execSQL("UPDATE saved_ads SET is_deleted=1, is_synced=0 WHERE user_id=$userId AND ad_id=$adId")
            syncSaveToServer(userId, adId, "unsave")
        }
    }

    private fun syncSaveToServer(userId: Int, adId: Int, action: String) {
        val queue = Volley.newRequestQueue(this)
        val url = Constants.BASE_URL + "save_ad.php"

        val request = object : StringRequest(Request.Method.POST, url,
            { response ->
                if (response.contains("success")) {
                    val db = dbHelper.writableDatabase
                    if (action == "unsave") {
                        db.execSQL("DELETE FROM saved_ads WHERE user_id=$userId AND ad_id=$adId")
                    } else {
                        db.execSQL("UPDATE saved_ads SET is_synced=1 WHERE user_id=$userId AND ad_id=$adId")
                    }
                }
            },
            { error -> Log.e("ViewAd", "Sync failed, saved locally") }
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
    }

    private fun getUserIdByEmail(email: String): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT user_id FROM users WHERE email = ?", arrayOf(email))
        var id = -1
        if (cursor.moveToFirst()) id = cursor.getInt(0)
        cursor.close()
        return id
    }

    private fun loadAdDetails(id: Int) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM ads WHERE ad_id = ?", arrayOf(id.toString()))

        if (cursor.moveToFirst()) {
            val title = cursor.getString(cursor.getColumnIndexOrThrow("title"))
            val price = cursor.getDouble(cursor.getColumnIndexOrThrow("price"))
            val desc = cursor.getString(cursor.getColumnIndexOrThrow("description"))
            val loc = cursor.getString(cursor.getColumnIndexOrThrow("location_address"))
            val condition = cursor.getString(cursor.getColumnIndexOrThrow("condition_type"))
            val category = cursor.getString(cursor.getColumnIndexOrThrow("category"))
            val userId = cursor.getInt(cursor.getColumnIndexOrThrow("user_id"))
            val date = cursor.getString(cursor.getColumnIndexOrThrow("created_at"))

            val lat = cursor.getDouble(cursor.getColumnIndexOrThrow("lat"))
            val lng = cursor.getDouble(cursor.getColumnIndexOrThrow("lng"))

            findViewById<TextView>(R.id.property_title).text = title
            findViewById<TextView>(R.id.property_price).text = "Rs $price"
            findViewById<TextView>(R.id.property_loc).text = loc
            findViewById<TextView>(R.id.tv_description).text = desc
            findViewById<TextView>(R.id.condition_value).text = condition
            findViewById<TextView>(R.id.category_value).text = category
            findViewById<TextView>(R.id.date).text = date.take(10)

            setupMap(lat, lng, loc)
            loadSellerDetails(userId)
        }
        cursor.close()
    }

    private fun setupMap(lat: Double, lng: Double, locationName: String) {
        mapView = findViewById(R.id.map_view)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(false)

        val point = GeoPoint(lat, lng)
        mapView.controller.setZoom(15.0)
        mapView.controller.setCenter(point)

        val marker = Marker(mapView)
        marker.position = point
        marker.title = locationName
        mapView.overlays.add(marker)

        findViewById<android.view.View>(R.id.map_overlay).setOnClickListener {
            val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($locationName)")
            try {
                val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                mapIntent.setPackage("com.google.android.apps.maps")
                startActivity(mapIntent)
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
        }
    }

    private fun loadSellerDetails(userId: Int) {
        val db = dbHelper.readableDatabase
        // UPDATED QUERY: Fetching fcm_token
        val cursor = db.rawQuery("SELECT full_name, phone_number, profile_image_url, fcm_token FROM users WHERE user_id = ?", arrayOf(userId.toString()))

        if (cursor.moveToFirst()) {
            val name = cursor.getString(0)
            sellerPhoneNumber = cursor.getString(1)
            val imgPath = cursor.getString(2)
            // Save Token for Notification
            sellerFcmToken = cursor.getString(3) ?: ""

            findViewById<TextView>(R.id.seller_name).text = name

            val sellerImgView = findViewById<ImageView>(R.id.seller_image)
            if (!imgPath.isNullOrEmpty()) {
                if (imgPath.startsWith("/")) {
                    val imgFile = File(imgPath)
                    if (imgFile.exists()) {
                        val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                        sellerImgView.setImageBitmap(bitmap)
                    }
                } else {
                    val fullUrl = if (imgPath.startsWith("http")) imgPath else Constants.BASE_URL + imgPath
                    Glide.with(this).load(fullUrl).placeholder(R.drawable.ic_profile_placeholder).into(sellerImgView)
                }
            }

            // Go to Profile
            val openProfile = {
                val intent = Intent(this, OtherAds::class.java)
                intent.putExtra("USER_ID", userId)
                startActivity(intent)
            }
            sellerImgView.setOnClickListener { openProfile() }
            findViewById<TextView>(R.id.seller_name).setOnClickListener { openProfile() }

        } else {
            findViewById<TextView>(R.id.seller_name).text = "Unknown User ($userId)"
        }
        cursor.close()
    }

    private fun loadAdImages(id: Int) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT image_url FROM ad_images WHERE ad_id = ?", arrayOf(id.toString()))

        val imagePaths = ArrayList<String>()
        if (cursor.moveToFirst()) {
            do {
                imagePaths.add(cursor.getString(0))
            } while (cursor.moveToNext())
        }
        cursor.close()

        val viewPager = findViewById<ViewPager2>(R.id.viewPagerImageSlider)
        viewPager.adapter = ImageSliderAdapter(imagePaths)

        val countText = findViewById<TextView>(R.id.photo_count_text)
        countText.text = "1/${imagePaths.size}"

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                countText.text = "${position + 1}/${imagePaths.size}"
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (::mapView.isInitialized) mapView.onPause()
    }
}