package com.example.listit

import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.util.Locale

class PickLocationActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var tvAddress: TextView
    private var selectedLat: Double = 0.0
    private var selectedLng: Double = 0.0
    private var selectedAddress: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initialize OSM Configuration (REQUIRED for the map to load tiles)
        // This handles caching and user agent settings automatically
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))

        setContentView(R.layout.activity_pick_location)

        tvAddress = findViewById(R.id.tv_address)
        map = findViewById(R.id.map)

        // 2. Setup Map Visuals
        map.setTileSource(TileSourceFactory.MAPNIK) // The standard free OSM style
        map.setMultiTouchControls(true) // Enable pinch-to-zoom

        // Set Default Start Point (e.g., Islamabad)
        // You can change these coordinates to your preferred default city
        val startPoint = GeoPoint(33.6844, 73.0479)
        map.controller.setZoom(15.0)
        map.controller.setCenter(startPoint)

        // 3. Add Listener for Dragging
        // This updates the variables whenever the user moves the map
        map.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                updateCenterLocation()
                return true
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                updateCenterLocation()
                return true
            }
        })

        // Initial update to show address of start point
        updateCenterLocation()

        // 4. Confirm Button
        findViewById<android.view.View>(R.id.btn_confirm_location).setOnClickListener {
            // Pack the data to send back to PostAd
            val resultIntent = Intent()
            resultIntent.putExtra("lat", selectedLat)
            resultIntent.putExtra("lng", selectedLng)
            resultIntent.putExtra("address", selectedAddress)

            // Send success result
            setResult(RESULT_OK, resultIntent)
            finish() // Close this screen
        }
    }

    private fun updateCenterLocation() {
        val center = map.mapCenter
        selectedLat = center.latitude
        selectedLng = center.longitude

        // Fetch address in background so UI doesn't freeze
        getAddressFromLocation(selectedLat, selectedLng)
    }

    private fun getAddressFromLocation(lat: Double, lng: Double) {
        Thread {
            try {
                // Uses Android's native Geocoder (Free & Built-in)
                val geocoder = Geocoder(this, Locale.getDefault())
                // Get 1 result
                val addresses = geocoder.getFromLocation(lat, lng, 1)

                if (!addresses.isNullOrEmpty()) {
                    val addressObj = addresses[0]

                    // Format the address nicely
                    val subLocality = addressObj.subLocality ?: addressObj.locality ?: ""
                    val city = addressObj.adminArea ?: ""
                    val fullAdd = if (subLocality.isNotEmpty()) "$subLocality, $city" else city

                    // Update UI on Main Thread
                    runOnUiThread {
                        selectedAddress = fullAdd
                        tvAddress.text = fullAdd
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    tvAddress.text = "Fetching location..."
                }
            }
        }.start()
    }

    // Lifecycle methods required by OSM to pause/resume tile loading
    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }
}