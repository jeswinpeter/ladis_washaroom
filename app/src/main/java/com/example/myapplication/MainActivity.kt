package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import java.net.URL
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    // Declare a variable for MapView
    private lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Init MapLibre
        MapLibre.getInstance(this)

        // Init layout view
        val inflater = LayoutInflater.from(this)
        val rootView = inflater.inflate(R.layout.activity_main, null)
        setContentView(rootView)

        // Init the MapView
        mapView = rootView.findViewById(R.id.mapView)
        mapView.getMapAsync { map ->
            map.setStyle("https://demotiles.maplibre.org/style.json")
            map.cameraPosition = CameraPosition.Builder().target(LatLng(8.5,76.9)).zoom(5.0).build()
            val uiSettings = map.uiSettings
            uiSettings.isZoomGesturesEnabled = true        // pinch zoom
            uiSettings.isDoubleTapGesturesEnabled = true   // double tap to zoom in
            uiSettings.isQuickZoomGesturesEnabled = true   // double tap + hold + drag
            // Zoom in button
            findViewById<ImageButton>(R.id.btnZoomIn).setOnClickListener {
                map.animateCamera(CameraUpdateFactory.zoomIn())
            }

            // Zoom out button
            findViewById<ImageButton>(R.id.btnZoomOut).setOnClickListener {
                map.animateCamera(CameraUpdateFactory.zoomOut())
            }
            // Search button click
            findViewById<ImageButton>(R.id.btnSearch).setOnClickListener {
                val query = findViewById<EditText>(R.id.searchEditText).text.toString()
                if (query.isNotBlank()) {
                    searchLocation(query, map)
                } else {
                    Toast.makeText(this, "Enter a location", Toast.LENGTH_SHORT).show()
                }
            }

            // Search on keyboard "Search" button
            findViewById<EditText>(R.id.searchEditText).setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    val query = findViewById<EditText>(R.id.searchEditText).text.toString()
                    if (query.isNotBlank()) {
                        searchLocation(query, map)
                    }
                    true
                } else {
                    false
                }
            }
        }
    }

    // Search function using Nominatim (OpenStreetMap geocoding)
    private fun searchLocation(query: String, map: org.maplibre.android.maps.MapLibreMap) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=1"
                val response = URL(url).readText()

                // Check if empty results
                if (response == "[]") {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "No results found", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Parse JSON
                val jsonArray = JSONArray(response)
                val json = jsonArray.getJSONObject(0)

                val lat = json.getString("lat").toDouble()
                val lon = json.getString("lon").toDouble()
                val displayName = json.getString("display_name")

                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    // Move camera to location
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 15.0),
                        1000  // 1 second animation
                    )
                    Toast.makeText(this@MainActivity, "Found: $displayName", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Search failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }



    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}
