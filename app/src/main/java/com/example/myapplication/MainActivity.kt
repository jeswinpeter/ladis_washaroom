package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import androidx.cardview.widget.CardView
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
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
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    // Declare a variable for MapView
    private lateinit var mapView: MapView
    private lateinit var mapLibreMap: MapLibreMap
    private var locationComponent: LocationComponent? = null
    private var isLocationEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Init MapLibre
        MapLibre.getInstance(this)

        // Init layout view
        val inflater = LayoutInflater.from(this)
        val rootView = inflater.inflate(R.layout.activity_main, null)
        setContentView(rootView)
        val btnMyLocation = findViewById<FloatingActionButton>(R.id.btnMyLocation)


        // Apply window insets to search bar
        val searchBar = rootView.findViewById<CardView>(R.id.searchBar)
        ViewCompat.setOnApplyWindowInsetsListener(searchBar) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<FrameLayout.LayoutParams> {
                topMargin = systemBars.top + 16  // Status bar + 16dp
            }
            insets
        }

        // Init the MapView
        mapView = rootView.findViewById(R.id.mapView)
        mapView.getMapAsync { map ->
            this.mapLibreMap = map

            // Setting default map style
            map.setStyle("https://tiles.openfreemap.org/styles/bright")

            map.cameraPosition = CameraPosition.Builder().target(LatLng(8.5,76.9)).zoom(5.0).build()
            val uiSettings = map.uiSettings
            uiSettings.isZoomGesturesEnabled = true        // pinch zoom
            uiSettings.isDoubleTapGesturesEnabled = true   // double tap to zoom in
            uiSettings.isQuickZoomGesturesEnabled = true   // double tap + hold + drag

            btnMyLocation.setOnClickListener {
                handleMyLocationClick()

            }


            val btnZoomIn = findViewById<ImageButton>(R.id.btnZoomIn)
            val btnZoomOut = findViewById<ImageButton>(R.id.btnZoomOut)
            val btnSearch = findViewById<ImageButton>(R.id.btnSearch)
            val searchEditText = findViewById<EditText>(R.id.searchEditText)
            val btnChangeStyle = rootView.findViewById<FloatingActionButton>(R.id.btnChangeStyle)



            // Zoom in button
            btnZoomIn.setOnClickListener {
                map.animateCamera(CameraUpdateFactory.zoomIn())
            }

            // Zoom out button
            btnZoomOut.setOnClickListener {
                map.animateCamera(CameraUpdateFactory.zoomOut())
            }

            // Change style button
            btnChangeStyle.setOnClickListener {
                toggleMapStyle()
            }

            btnSearch.setImageResource(android.R.drawable.ic_menu_search)
            btnSearch.tag = "search"
            // Search button click
            btnSearch.setOnClickListener {
                val query = searchEditText.text.toString()
                if (btnSearch.tag == "search") {
                    if (query.isNotBlank()) {
                        searchLocation(query, map)
                    } else {
                        Toast.makeText(this, "Enter a location", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // CLEAR MODE: Just reset text and icon
                    searchEditText.text.clear()
                    btnSearch.setImageResource(android.R.drawable.ic_menu_search)
                    btnSearch.tag = "search"
                }
            }



            // Search on keyboard "Search" button
            searchEditText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    val query = searchEditText.text.toString()
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

    // Search function using Nominatim
    //Switch to MapTiler geocoding or Photon for release
    private fun searchLocation(query: String, map: org.maplibre.android.maps.MapLibreMap) {
        val btnSearch = findViewById<ImageButton>(R.id.btnSearch)
        val searchEditText = findViewById<EditText>(R.id.searchEditText)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=1"

                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000 // 5 seconds
                connection.readTimeout = 5000
                connection.requestMethod = "GET"

                connection.setRequestProperty("User-Agent", "NavEz/1.0 (alwinjose.job@gmail.com)")
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode != 200) {
                    throw Exception("Server returned code $responseCode")
                }

                val response = connection.inputStream.bufferedReader().use { it.readText() }


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

                    // 2. Change Icon to X (Clear)
                    btnSearch.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    btnSearch.tag = "clear"

                    // 3. Hide Keyboard
                    val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)

                    Toast.makeText(this@MainActivity, "Found: $displayName", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Search failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }



    private fun handleMyLocationClick() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1001
            )
            return
        }

            if (!isLocationEnabled) {
                mapLibreMap.style?.let { enableLocation(it) }
            } else {
                // SAFE CHECK: Ensure we actually have a location before forcing an update
                val lastLoc = mapLibreMap.locationComponent.lastKnownLocation
                if (lastLoc != null) {
                    mapLibreMap.locationComponent.forceLocationUpdate(lastLoc)
                    // Optional: Move camera to user if they are off-screen
                    mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                        LatLng(lastLoc.latitude, lastLoc.longitude), 15.0))
                } else {
                    Toast.makeText(this, "Searching for GPS signal...", Toast.LENGTH_SHORT).show()
                }
            }
        }

// Map style changing
    private val styles = listOf(
        "https://tiles.openfreemap.org/styles/bright",
        "https://tiles.openfreemap.org/styles/liberty",
        "https://demotiles.maplibre.org/style.json"
    )

    private var styleIndex = 0

    private fun toggleMapStyle() {
        styleIndex = (styleIndex + 1) % styles.size
        mapLibreMap.setStyle(styles[styleIndex])
    }

// Location activation
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun enableLocation(style: Style) {
        val locationComponent = mapLibreMap.locationComponent

        val options = LocationComponentActivationOptions.builder(this, style)
            .useDefaultLocationEngine(true)
            .build()

        locationComponent.activateLocationComponent(options)
        locationComponent.isLocationComponentEnabled = true
        locationComponent.cameraMode = CameraMode.TRACKING
        locationComponent.renderMode = RenderMode.COMPASS

        this.locationComponent = locationComponent
        isLocationEnabled = true
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1001 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            mapLibreMap.style?.let { enableLocation(it) }
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
}