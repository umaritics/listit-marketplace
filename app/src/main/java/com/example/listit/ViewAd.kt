package com.example.listit

import ListItDbHelper
import android.content.ContentValues
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

class ViewAd : AppCompatActivity() {

    private lateinit var dbHelper: ListItDbHelper
    private var adId: Int = -1
    private var sellerPhoneNumber: String = ""
    private lateinit var mapView: MapView

    private var sellerId: Int = -1 // Add this at top of class

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Init OSM
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))

        enableEdgeToEdge()
        setContentView(R.layout.activity_view_ad)

        dbHelper = ListItDbHelper(this)
        adId = intent.getIntExtra("AD_ID", -1)

        if (adId != -1) {
            loadAdDetails(adId)
            loadAdImages(adId)
        } else {
            Toast.makeText(this, "Error loading ad", Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<ImageView>(R.id.back_icon).setOnClickListener { finish() }

        // Call Logic
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_call).setOnClickListener {
            if (sellerPhoneNumber.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_DIAL)
                intent.data = Uri.parse("tel:$sellerPhoneNumber")
                startActivity(intent)
            } else {
                Toast.makeText(this, "Phone number not available", Toast.LENGTH_SHORT).show()
            }
        }


        // Inside ViewAd.kt onCreate...

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_chat).setOnClickListener {
            val currentUserEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email
            if (currentUserEmail == null) {
                Toast.makeText(this, "Please login to chat", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val currentUserId = getUserIdByEmail(currentUserEmail)
            // Seller ID was loaded in loadAdDetails, ensure it's accessible (make it a class property)
            // assuming 'userId' from 'loadAdDetails' is stored in a class var 'sellerId'

            // Note: You need to promote the local userId variable in loadAdDetails to a class property: private var sellerId = -1

            if (currentUserId == sellerId) {
                Toast.makeText(this, "You cannot chat with yourself", Toast.LENGTH_SHORT).show()
            } else {
                initiateChat(currentUserId, sellerId)
            }
        }

// Add these functions to ViewAd class:



// Update loadAdDetails to save sellerId
// ... inside loadAdDetails ...
// sellerId = cursor.getInt(cursor.getColumnIndexOrThrow("user_id"))



        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
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

            // --- FIX IS HERE: Assign to the class-level variable 'sellerId' ---
            sellerId = cursor.getInt(cursor.getColumnIndexOrThrow("user_id"))

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
            loadSellerDetails(sellerId) // Use the correct variable here too
        }
        cursor.close()
    }


    private fun getUserIdByEmail(email: String): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT user_id FROM users WHERE email = ?", arrayOf(email))
        var id = -1
        if (cursor.moveToFirst()) id = cursor.getInt(0)
        cursor.close()
        return id
    }

    private fun initiateChat(buyerId: Int, sellerId: Int) {
        val dbRead = dbHelper.readableDatabase
        // Check if chat exists
        val cursor = dbRead.rawQuery("SELECT chat_id FROM chat_rooms WHERE ad_id = ? AND buyer_id = ? AND seller_id = ?",
            arrayOf(adId.toString(), buyerId.toString(), sellerId.toString()))

        var chatId = -1
        if (cursor.moveToFirst()) {
            chatId = cursor.getInt(0)
        }
        cursor.close()

        if (chatId == -1) {
            // Create new chat room
            val dbWrite = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("ad_id", adId)
                put("buyer_id", buyerId)
                put("seller_id", sellerId)
                put("created_at", System.currentTimeMillis().toString())
            }
            chatId = dbWrite.insert(ListItDbHelper.TABLE_CHAT_ROOMS, null, values).toInt()
        }


        // ADD THIS NETWORK REQUEST TO CREATE CHAT ON SERVER:
        val queue = Volley.newRequestQueue(this)
        val url = Constants.BASE_URL + "create_chat.php"

        val request = object : StringRequest(
            Request.Method.POST, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getString("status") == "success") {
                        val serverChatId = json.getInt("chat_id")

                        // Navigate to Chat Screen
                        val intent = Intent(this, Chat_message::class.java)
                        intent.putExtra("CHAT_ID", serverChatId) // Use Server ID to ensure syncing works
                        intent.putExtra("OTHER_NAME", findViewById<TextView>(R.id.seller_name).text.toString())
                        startActivity(intent)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            },
            { error -> Toast.makeText(this, "Network Error", Toast.LENGTH_SHORT).show() }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["ad_id"] = adId.toString()
                params["buyer_id"] = buyerId.toString()
                params["seller_id"] = sellerId.toString()
                return params
            }
        }
        queue.add(request)


        // Open Chat Screen
        val intent = Intent(this, Chat_message::class.java)
        intent.putExtra("CHAT_ID", chatId)
        intent.putExtra("OTHER_NAME", findViewById<TextView>(R.id.seller_name).text.toString())
        startActivity(intent)
    }


    private fun setupMap(lat: Double, lng: Double, locationName: String) {
        mapView = findViewById(R.id.map_view)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(false) // Disable zoom interaction inside ScrollView

        // Set Point
        val point = GeoPoint(lat, lng)
        mapView.controller.setZoom(15.0)
        mapView.controller.setCenter(point)

        // Add Marker
        val marker = Marker(mapView)
        marker.position = point
        marker.title = locationName
        // Default marker icon is used
        mapView.overlays.add(marker)

        // Open Google Maps on Click using the Overlay View
        findViewById<android.view.View>(R.id.map_overlay).setOnClickListener {
            val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($locationName)")
            val mapIntent = Intent(Intent.ACTION_VIEW, uri)
            // Try to open Google Maps, fall back to browser/other
            try {
                mapIntent.setPackage("com.google.android.apps.maps")
                startActivity(mapIntent)
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
        }
    }

    private fun loadSellerDetails(userId: Int) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT full_name, phone_number, profile_image_url FROM users WHERE user_id = ?", arrayOf(userId.toString()))

        if (cursor.moveToFirst()) {
            val name = cursor.getString(0)
            sellerPhoneNumber = cursor.getString(1)
            val imgPath = cursor.getString(2)

            findViewById<TextView>(R.id.seller_name).text = name

            // Load Seller Image
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

        // Setup ViewPager
        val viewPager = findViewById<ViewPager2>(R.id.viewPagerImageSlider)
        viewPager.adapter = ImageSliderAdapter(imagePaths)

        // Setup Counter Text
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