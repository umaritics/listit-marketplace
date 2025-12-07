package com.example.listit

import ListItDbHelper
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
import com.bumptech.glide.Glide
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
            val userId = cursor.getInt(cursor.getColumnIndexOrThrow("user_id"))
            val date = cursor.getString(cursor.getColumnIndexOrThrow("created_at"))

            // Map Coordinates
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