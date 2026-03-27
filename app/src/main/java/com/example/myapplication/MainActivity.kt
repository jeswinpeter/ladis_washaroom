package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
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
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import android.widget.ImageButton
import android.widget.LinearLayout
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
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import okhttp3.Callback
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.card.MaterialCardView
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.exponential
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.io.OutputStreamWriter
import java.time.Instant

class MainActivity : AppCompatActivity(), GpsAlarmFragment.RadiusListener {
    private fun playAlarmSound() {

        val prefs = getSharedPreferences("GPS_ALARM", MODE_PRIVATE)
        val uriString = prefs.getString("ringtone_uri", null)

        val uri = if (uriString != null)
            android.net.Uri.parse(uriString)
        else
            android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)

        val ringtone = android.media.RingtoneManager.getRingtone(this, uri)
        ringtone.play()
    }
    private var radiusMeters = 100

    // Declare a variable for MapView
    private lateinit var mapView: MapView
    private lateinit var mapLibreMap: MapLibreMap
    private var locationComponent: LocationComponent? = null
    private var isLocationEnabled = false
    private var startPoint: LatLng? = null
    private var destinationPoint: LatLng? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Location permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 3000
        ).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                checkIfReachedDestination(location.latitude, location.longitude)
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            mainLooper
        )
    }

    private fun checkIfReachedDestination(currentLat: Double, currentLon: Double) {
        val dest = destinationPoint ?: return
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            currentLat, currentLon,
            dest.latitude, dest.longitude,
            results
        )
        val distance = results[0]
        if (distance <= radiusMeters && isAlarmActive) {
            playAlarmSound()
            isAlarmActive = false
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Toast.makeText(this, "Destination reached!", Toast.LENGTH_SHORT).show()
        }
    }

    private lateinit var placeDetailsSheetBehavior: BottomSheetBehavior<LinearLayout>
    private var pendingActionAfterGps: (() -> Unit)? = null
    private var routeInfoSheet: RouteInfoBottomSheetFragment? = null
    var isAlarmActive = false




    @SuppressLint("MissingPermission")
    private val locationSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // User clicked "OK" - GPS is now on!
            mapLibreMap.style?.let { enableLocation(it) }
            pendingActionAfterGps?.invoke()
            pendingActionAfterGps = null
        } else {
            // User clicked "No thanks"
            pendingActionAfterGps = null
            Toast.makeText(this, "Location services are required for this feature.", Toast.LENGTH_SHORT).show()
        }
    }


    enum class SearchType {
        MAIN,
        ORIGIN,
        DESTINATION
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Init MapLibre
        MapLibre.getInstance(this)

        // Init layout view
        val inflater = LayoutInflater.from(this)
        val rootView = inflater.inflate(R.layout.activity_main, null)
        setContentView(rootView)
        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)

        // Inside onCreate, after setContentView(rootView)
        val btnMyLocation = findViewById<FloatingActionButton>(R.id.btnMyLocation)
        val btnGetDirections = findViewById<Button>(R.id.btnGetDirections)
        val directionsPanel = findViewById<MaterialCardView>(R.id.directionsPanel)
        val btnSearchOrigin = findViewById<ImageButton>(R.id.btnSearchOrigin)
        val btnSearchDestination = findViewById<ImageButton>(R.id.btnSearchDestination)
        val etOrigin = findViewById<EditText>(R.id.etOrigin)
        val etDestination = findViewById<EditText>(R.id.etDestination)
        val btnCalculateRoute = findViewById<Button>(R.id.btnCalculateRoute)
        val closeDirections = findViewById<LinearLayout>(R.id.btnCloseDirections)
        val btnClose = findViewById<ImageButton>(R.id.btnClose)
        val btnGpsAlarm = findViewById<FloatingActionButton>(R.id.btnGpsAlarm)

        /*btnGetDirections.setOnClickListener {
            val searchBar = findViewById<CardView>(R.id.searchBar)
            searchBar.visibility = View.GONE
            btnGetDirections.visibility = View.GONE
            closeDirections.visibility= View.VISIBLE
            directionsPanel.visibility = View.VISIBLE
            searchEditText.text.clear()
            btnSearch.setImageResource(android.R.drawable.ic_menu_search)
            btnSearch.tag = "search"
        }*/


        // Set up persistent bottom sheet (initially hidden, map stays interactive behind it)
        val placeDetailsSheet = rootView.findViewById<LinearLayout>(R.id.placeDetailsSheet)
        placeDetailsSheetBehavior = BottomSheetBehavior.from(placeDetailsSheet)
        placeDetailsSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        placeDetailsSheetBehavior.isHideable = true

        // Apply window insets to search bar
        val searchBar = rootView.findViewById<MaterialCardView>(R.id.searchBar)
        ViewCompat.setOnApplyWindowInsetsListener(searchBar) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<CoordinatorLayout.LayoutParams> {
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
            btnSettings.setOnClickListener {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
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

            //Change GPS Alarm
            btnGpsAlarm.setOnClickListener {

                if (!isLocationEnabled) {
                    handleMyLocationClick()   // ✅ enable GPS first
                    Toast.makeText(this, "Turn on GPS first", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val fragment = GpsAlarmFragment()
                fragment.show(supportFragmentManager, "GPS_ALARM")
            }

            // Fare Calculator FAB (defined in XML)
            val btnFare = rootView.findViewById<FloatingActionButton>(R.id.btnFareCalc)
            btnFare?.setOnClickListener {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.placeDetailsContainer, FareCalculatorFragment(), "FareCalc")
                    .commit()
                placeDetailsSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }

            val btnContribute = rootView.findViewById<FloatingActionButton>(R.id.btnContribute)
            btnContribute?.setOnClickListener {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.placeDetailsContainer, RouteSearchFragment(), "RouteSearch")
                    .commit()
                placeDetailsSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }

            btnSearch.setImageResource(android.R.drawable.ic_menu_search)
            btnSearch.tag = "search"
            // Search button click
            btnSearch.setOnClickListener {
                val query = searchEditText.text.toString()
                if (btnSearch.tag == "search") {
                    if (query.isNotBlank()) {
                        searchLocation(query, map, searchEditText, btnSearch, SearchType.MAIN)
                    } else {
                        Toast.makeText(this, "Enter a location", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // CLEAR MODE: Just reset text and icon
                    searchEditText.text.clear()
                    btnSearch.setImageResource(android.R.drawable.ic_menu_search)
                    btnSearch.tag = "search"
                    findViewById<Button>(R.id.btnGetDirections).visibility = View.GONE
                    destinationPoint = null
                    removeMarker("end-source", "end-layer")
                    placeDetailsSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

                }
            }


            // Search on keyboard "Search" button
            searchEditText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    val query = searchEditText.text.toString()
                    if (query.isNotBlank()) {
                        searchLocation(query, map, searchEditText, btnSearch, SearchType.MAIN)
                    }
                    true
                } else {
                    false
                }
            }

            // Origin search button
            btnSearchOrigin.setOnClickListener {
                val query = etOrigin.text.toString()
                if (btnSearchOrigin.tag == "search") {
                    if (query.isNotBlank()) {
                        searchLocation(query, map, etOrigin, btnSearchOrigin, SearchType.ORIGIN)
                    } else {
                        Toast.makeText(this, "Enter origin location", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Clear origin
                    etOrigin.text.clear()
                    etOrigin.isEnabled = true
                    btnSearchOrigin.setImageResource(android.R.drawable.ic_menu_search)
                    btnSearchOrigin.tag = "search"
                    startPoint = null
                    removeMarker("start-source", "start-layer")
                    clearRoute()
                    updateCalculateButtonState()
                    //busInfoSheet?.updateData("–", "–", "Awaiting route data...")
                }
            }

            // Origin keyboard search
            etOrigin.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    val query = etOrigin.text.toString()
                    if (query.isNotBlank()) {
                        searchLocation(query, map, etOrigin, btnSearchOrigin, SearchType.ORIGIN)
                    }
                    true
                } else false
            }

            // Destination search button
            btnSearchDestination.setOnClickListener {
                val query = etDestination.text.toString()
                if (btnSearchDestination.tag == "search") {
                    if (query.isNotBlank()) {
                        searchLocation(query, map, etDestination, btnSearchDestination, SearchType.DESTINATION)
                    } else {
                        Toast.makeText(this, "Enter destination", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Clear destination
                    etDestination.text.clear()
                    etDestination.isEnabled = true
                    btnSearchDestination.setImageResource(android.R.drawable.ic_menu_search)
                    btnSearchDestination.tag = "search"
                    destinationPoint = null
                    removeMarker("end-source", "end-layer")
                    updateCalculateButtonState()
                   // busInfoSheet?.updateData("–", "–", "Awaiting route data...")
                }
            }

            // Destination keyboard search
            etDestination.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    val query = etDestination.text.toString()
                    if (query.isNotBlank()) {
                        searchLocation(query, map, etDestination, btnSearchDestination, SearchType.DESTINATION)
                    }
                    true
                } else false
            }

            // Calculate route button
            btnCalculateRoute.setOnClickListener {
                if (startPoint != null && destinationPoint != null) {
                    calculateAndDisplayRoute(startPoint!!, destinationPoint!!)
                }
            }

            // Close directions button
            btnClose.setOnClickListener {
                resetDirectionsPanel(directionsPanel, searchBar, etOrigin, etDestination,
                    btnSearchOrigin, btnSearchDestination, btnCalculateRoute, closeDirections)

                placeDetailsSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
        }
    }

    // Search function using Nominatim
    //Switch to MapTiler geocoding or Photon for release
    private fun searchLocation(query: String,
                               map: org.maplibre.android.maps.MapLibreMap,
                               editText: EditText? = null,
                               searchButton: ImageButton? = null,
                               searchType: SearchType = SearchType.MAIN) {
        val btnSearch = findViewById<ImageButton>(R.id.btnSearch)
        val searchEditText = findViewById<EditText>(R.id.searchEditText)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                // Fetch up to 10 results so we can pick the best city/town match.
                // featuretype and tag are not valid Nominatim /search params and are ignored.
                val url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=10&type=city&addressdetails=1"

                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000 // 5 seconds
                connection.readTimeout = 5000
                connection.requestMethod = "GET"

                connection.setRequestProperty("User-Agent", "NavEz/2.0 (alwinjose.job@gmail.com)")
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

                // Prefer results where class=place and type is city/town/village/municipality

                val cityTypes = setOf("city", "town", "village", "municipality")
                var best: JSONObject = jsonArray.getJSONObject(0) // fallback to first

                for (i in 0 until jsonArray.length()) {
                    val candidate = jsonArray.getJSONObject(i)
                    val placeClass = candidate.optString("class", "")
                    val placeType = candidate.optString("type", "")
                    if (placeClass == "place" && placeType in cityTypes) {
                        best = candidate
                        break
                    }
                }

                val json = best
                val lat = json.getString("lat").toDouble()
                val lon = json.getString("lon").toDouble()
                val displayName = json.getString("display_name")


                // Update UI on main thread
                withContext(Dispatchers.Main) {

                    val latLng = LatLng(lat, lon)
                    // Move camera to location
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 15.0),
                        1000  // 1 second animation
                    )

                    // Determine which button and text field to update
                    val buttonToUpdate = searchButton ?: btnSearch
                    val textToUpdate = editText ?: searchEditText

                    // Change Icon to X (Clear)
                    buttonToUpdate.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    buttonToUpdate.tag = "clear"

                    // Hide Keyboard
                    val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                            as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(textToUpdate.windowToken, 0)
                    // Handle based on search type
                    when (searchType) {
                        SearchType.MAIN -> {
                            destinationPoint = latLng
                            addOrUpdateCircleMarker(lat, lon, "end-source", "end-layer", "#E63946")

                            // Show the Directions button
                            //val btnGetDirections = findViewById<Button>(R.id.btnGetDirections)
                            //btnGetDirections.visibility = View.VISIBLE

                            // Pre-fill the destination in the hidden panel
                            findViewById<EditText>(R.id.etDestination).setText(displayName)
                            val btnSearchDest = findViewById<ImageButton>(R.id.btnSearchDestination)
                            btnSearchDest.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                            btnSearchDest.tag = "clear"

                            fetchWikipediaSummary(displayName, lat, lon) { wikiSummary ->
                                val fragment = PlaceDetailsFragment()
                                val bundle = Bundle().apply {
                                    putString("name", displayName)
                                    putString("address", displayName)
                                    putDouble("lat", lat)
                                    putDouble("lon", lon)
                                    putString("wiki", wikiSummary)
                                }
                                fragment.arguments = bundle
                                supportFragmentManager.beginTransaction()
                                    .replace(R.id.placeDetailsContainer, fragment, "PlaceDetails")
                                    .commit()
                                placeDetailsSheetBehavior.state =
                                    BottomSheetBehavior.STATE_COLLAPSED

                                Toast.makeText(
                                    this@MainActivity,
                                    "Found: $displayName",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }

                            SearchType.ORIGIN -> {
                                startPoint = latLng

                                // Enable destination field
                                val etDest = findViewById<EditText>(R.id.etDestination)
                                val btnSearchDest =
                                    findViewById<ImageButton>(R.id.btnSearchDestination)
                                etDest.isEnabled = true
                                btnSearchDest.isEnabled = true
                                etDest.requestFocus()

                                updateCalculateButtonState()
                                Toast.makeText(this@MainActivity, "Origin set", Toast.LENGTH_SHORT)
                                    .show()
                            }

                            SearchType.DESTINATION -> {
                                destinationPoint = latLng
                                addOrUpdateCircleMarker(lat, lon, "end-source", "end-layer", "#E63946")

                                updateCalculateButtonState()
                                Toast.makeText(
                                    this@MainActivity,
                                    "Destination set",
                                    Toast.LENGTH_SHORT
                                )
                                    .show()
                            }

                    }

                    /*// 3. Hide Keyboard
                    val imm =
                        getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager

                    imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)

                    Toast.makeText(this@MainActivity, "Found: $displayName", Toast.LENGTH_LONG)
                        .show()
                    */

                }
            }
            catch(e: Exception) {
                Log.e("SearchError", "Search failed", e)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Search failed: ${e.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
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
                pendingActionAfterGps?.invoke()
                pendingActionAfterGps = null
            } else {
                checkLocationSettings()
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

    // Wikipedia summary call function
    // Uses coordinate-based geosearch so it reliably finds the right article
    private fun fetchWikipediaSummary(
        placeName: String,
        lat: Double,
        lon: Double,
        callback: (String?) -> Unit
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Step 1: find the nearest Wikipedia article to the coordinates
                val geoUrl = "https://en.wikipedia.org/w/api.php" +
                    "?action=query&list=geosearch" +
                    "&gscoord=${lat}%7C${lon}" +
                    "&gsradius=1000&gslimit=1&format=json"

                val geoConn = URL(geoUrl).openConnection() as HttpURLConnection
                geoConn.requestMethod = "GET"
                geoConn.connectTimeout = 5000
                geoConn.readTimeout = 5000
                geoConn.setRequestProperty("User-Agent", "NavEz/1.0 (alwinjose.job@gmail.com)")
                geoConn.connect()

                if (geoConn.responseCode != 200) {
                    withContext(Dispatchers.Main) { callback(null) }
                    return@launch
                }

                val geoJson = JSONObject(geoConn.inputStream.bufferedReader().use { it.readText() })
                val geoResults = geoJson.getJSONObject("query").getJSONArray("geosearch")

                if (geoResults.length() == 0) {
                    withContext(Dispatchers.Main) { callback(null) }
                    return@launch
                }

                val pageTitle = geoResults.getJSONObject(0).getString("title")
                val encodedTitle = URLEncoder.encode(pageTitle, "UTF-8").replace("+", "%20")

                // Step 2: fetch the summary for that article
                val summaryUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/$encodedTitle"

                val summaryConn = URL(summaryUrl).openConnection() as HttpURLConnection
                summaryConn.requestMethod = "GET"
                summaryConn.connectTimeout = 5000
                summaryConn.readTimeout = 5000
                summaryConn.connect()

                if (summaryConn.responseCode != 200) {
                    withContext(Dispatchers.Main) { callback(null) }
                    return@launch
                }

                val summaryJson = JSONObject(summaryConn.inputStream.bufferedReader().use { it.readText() })
                val summary = summaryJson.optString("extract", null)

                withContext(Dispatchers.Main) {
                    callback(summary)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(null)
                }
            }
        }
    }


// This portion was used to connect with backend that vasu did. It shows routes in the terminal
// calculateAndDisplayRoute() should call this funciton for this to work
//    private fun callRoutesBackend(origin: LatLng, destination: LatLng) {
//        val client = OkHttpClient()
//
//        val url =
//            "http://10.0.2.2:3001/api/routes" +
//                    "?originLat=${origin.latitude}" +
//                    "&originLon=${origin.longitude}" +
//                    "&destLat=${destination.latitude}" +
//                    "&destLon=${destination.longitude}"
//
//        val request = Request.Builder()
//            .url(url)
//            .get()
//            .build()
//
//        client.newCall(request).enqueue(object : Callback {
//            override fun onFailure(call: Call, e: IOException) {
//                android.util.Log.e("ROUTES_API", "Request failed", e)
//            }
//
//            override fun onResponse(call: Call, response: Response) {
//                val body = response.body?.string()
//                android.util.Log.d("ROUTES_API", "Response: $body")
//            }
//        })
//    }

    private fun checkSunSide(origin: LatLng, destination: LatLng) {
        val client = OkHttpClient()

        val url = "http://10.0.2.2:3001/api/sun-side" +
                "?originLat=${origin.latitude}" +
                "&originLon=${origin.longitude}" +
                "&destLat=${destination.latitude}" +
                "&destLon=${destination.longitude}"

        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("SUN_SIDE", "Request failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                Log.d("SUN_SIDE", "Response: $body")
                try {
                    val json = JSONObject(body)
                    val sunSide = json.optString("sun_side", "")
                    val advice  = json.optString("advice", "")
                    val sitSide = when (sunSide) {
                        "LEFT"  -> "R"
                        "RIGHT" -> "L"
                        "NIGHT" -> "N"
                        else    -> "–"   // FRONT / BACK
                    }
                    val sunPosition = when (sunSide) {
                        "LEFT"  -> "Sun on Left"
                        "RIGHT" -> "Sun on Right"
                        "FRONT" -> "Sun Ahead"
                        "BACK"  -> "Sun Behind"
                        "NIGHT" -> "Nighttime"
                        else    -> "–"
                    }
                    runOnUiThread {
                        routeInfoSheet?.let {
                            if (it.isAdded) it.updateSunSide(sitSide, sunPosition, advice)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SUN_SIDE", "Parse error: ${e.message}")
                }
            }
        })
    }

    private fun updateCalculateButtonState() {
        val btnCalculateRoute = findViewById<Button>(R.id.btnCalculateRoute)
        val bothReady = startPoint != null && destinationPoint != null
        btnCalculateRoute.isEnabled = bothReady

        if (bothReady && startPoint != null && destinationPoint != null) {
            calculateAndDisplayRoute(startPoint!!, destinationPoint!!)
        }
    }
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun autoFillOriginWithUserLocation(etOrigin: EditText, btnSearchOrigin: ImageButton) {
        val fusedClient = LocationServices.getFusedLocationProviderClient(this)

        val fillFromLocation = { lat: Double, lon: Double ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json"
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("User-Agent", "NavEz/1.0 (alwinjose.job@gmail.com)")
                    connection.connect()

                    if (connection.responseCode != 200) return@launch

                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val displayName = json.optString("display_name", "$lat, $lon")

                    withContext(Dispatchers.Main) {
                        startPoint = LatLng(lat, lon)
                        etOrigin.setText(displayName)
                        btnSearchOrigin.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                        btnSearchOrigin.tag = "clear"

                        val etDest = findViewById<EditText>(R.id.etDestination)
                        val btnSearchDest = findViewById<ImageButton>(R.id.btnSearchDestination)
                        etDest.isEnabled = true
                        btnSearchDest.isEnabled = true

                        updateCalculateButtonState()
                    }
                } catch (e: Exception) {
                    // Silently fail — origin stays empty for manual entry
                }
            }
            Unit
        }

        fusedClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                fillFromLocation(location.latitude, location.longitude)
            } else {
                // GPS just enabled — lastLocation not ready yet, request one fresh fix
                val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 0)
                    .setMaxUpdates(1)
                    .build()
                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        fusedClient.removeLocationUpdates(this)
                        val freshLoc = result.lastLocation ?: return
                        fillFromLocation(freshLoc.latitude, freshLoc.longitude)
                    }
                }
                fusedClient.requestLocationUpdates(request, callback, mainLooper)
            }
        }
    }
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun checkLocationSettings() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)

        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            // Settings are satisfied, we can enable location
            mapLibreMap.style?.let { enableLocation(it) }
            pendingActionAfterGps?.invoke()
            pendingActionAfterGps = null
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    // This triggers the "one-tap" system dialog
                    val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution).build()
                    locationSettingsLauncher.launch(intentSenderRequest)
                } catch (sendEx: Exception) {
                    // Ignore the error.
                }
            }
        }
    }

    private fun addOrUpdateCircleMarker(lat: Double, lon: Double, sourceId: String, layerId: String, colorHex: String) {
        val style = mapLibreMap.style ?: return
        val point = Point.fromLngLat(lon, lat)
        val feature = Feature.fromGeometry(point)
        if (style.getSource(sourceId) == null) {
            style.addSource(GeoJsonSource(sourceId, feature))
            val circleLayer = CircleLayer(layerId, sourceId).withProperties(
                circleColor(colorHex),
                circleRadius(12f),
                circleStrokeWidth(3f),
                circleStrokeColor("#FFFFFF")
            )
            style.addLayer(circleLayer)
        } else {
            style.getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(feature)
        }
    }

    private fun removeMarker(sourceId: String, layerId: String) {
        val style = mapLibreMap.style ?: return
        if (style.getLayer(layerId) != null) style.removeLayer(layerId)
        if (style.getSource(sourceId) != null) style.removeSource(sourceId)
    }

    private fun clearRoute() {
        val style = mapLibreMap.style ?: return
        if (style.getLayer("route-layer") != null) style.removeLayer("route-layer")
        if (style.getSource("route-source") != null) style.removeSource("route-source")
        clearBusRoute()
    }

    private fun clearBusRoute() {
        val style = mapLibreMap.style ?: return
        if (style.getLayer("bus-route-layer") != null) style.removeLayer("bus-route-layer")
        if (style.getSource("bus-route-source") != null) style.removeSource("bus-route-source")
    }

    /** Draw the OTP bus route on the map as an orange-coloured polyline. */
    private fun drawBusRoute(points: List<LatLng>) {
        val style = mapLibreMap.style ?: return
        val coordinates = points.map { Point.fromLngLat(it.longitude, it.latitude) }
        val feature = Feature.fromGeometry(LineString.fromLngLats(coordinates))
        if (style.getSource("bus-route-source") == null) {
            style.addSource(GeoJsonSource("bus-route-source", feature))
            style.addLayer(LineLayer("bus-route-layer", "bus-route-source").withProperties(
                lineColor("#FF6B35"),
                lineWidth(5f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND)
            ))
        } else {
            style.getSourceAs<GeoJsonSource>("bus-route-source")?.setGeoJson(feature)
        }
    }

    /** Decode a Google-encoded polyline string into a list of LatLng points. */
    private fun decodePolyline(encoded: String): List<LatLng> {
        val result = mutableListOf<LatLng>()
        var index = 0
        var lat = 0
        var lng = 0
        while (index < encoded.length) {
            var b: Int; var shift = 0; var value = 0
            do {
                b = encoded[index++].code - 63
                value = value or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lat += if (value and 1 != 0) (value shr 1).inv() else value shr 1
            shift = 0; value = 0
            do {
                b = encoded[index++].code - 63
                value = value or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lng += if (value and 1 != 0) (value shr 1).inv() else value shr 1
            result.add(LatLng(lat / 1e5, lng / 1e5))
        }
        return result
    }

    /**
     * Fetch a TRANSIT+WALK itinerary from OTP (running on 10.0.2.2:8080) and
     * draw the bus route polyline + update the BusInfoBottomSheet with transit details.
     */
    private fun fetchBusRouteFromOTP(origin: LatLng, destination: LatLng) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val query = """
                    {
                      plan(
                        from: {lat: ${origin.latitude}, lon: ${origin.longitude}}
                        to: {lat: ${destination.latitude}, lon: ${destination.longitude}}
                        numItineraries: 1
                        transportModes: [
                          {mode: BUS}, {mode: WALK}, {mode: TRAM},
                          {mode: SUBWAY}, {mode: FERRY}
                        ]
                      ) {
                        itineraries {
                          duration
                          legs {
                            mode
                            distance
                            legGeometry { points }
                            from { name }
                            to { name }
                            route {
                              shortName
                              longName
                              agency {
                                name
                              }
                            }
                          }
                        }
                      }
                    }
                """.trimIndent()

                val requestBody = JSONObject().put("query", query).toString()

                val conn = URL("http://10.0.2.2:8080/otp/gtfs/v1").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 100000
                conn.readTimeout = 100000
                conn.doOutput = true

                OutputStreamWriter(conn.outputStream).use { it.write(requestBody) }

                if (conn.responseCode != 200) {
                    throw Exception("OTP returned HTTP ${conn.responseCode}")
                }

                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                // GraphQL wraps the result in a "data" object
                val plan = json.optJSONObject("data")?.optJSONObject("plan")
                val itineraries = plan?.optJSONArray("itineraries")

                if (itineraries == null || itineraries.length() == 0) {
                    withContext(Dispatchers.Main) {
                        routeInfoSheet?.updateTransitData("No route", "–", "No transit route found")
                    }
                    return@launch
                }

                val itinerary = itineraries.getJSONObject(0)
                val durationSec = itinerary.optInt("duration", 0)
                val durationMin = durationSec / 60

                val legs = itinerary.optJSONArray("legs") ?: JSONArray()

                val allPoints = mutableListOf<LatLng>()
                val legParts = mutableListOf<String>()
                var primaryRoute = "Transit"
                var firstTransit = true

                for (i in 0 until legs.length()) {
                    val leg = legs.getJSONObject(i)
                    val mode = leg.optString("mode", "WALK").uppercase()
                    val encoded = leg.optJSONObject("legGeometry")?.optString("points", "") ?: ""
                    val distanceM = leg.optDouble("distance", 0.0).toInt()

                    if (encoded.isNotBlank()) {
                        allPoints.addAll(decodePolyline(encoded))
                    }

                    val fromName = leg.optJSONObject("from")?.optString("name", "")?.takeIf { it.isNotBlank() && it != "Origin" }
                    val toName   = leg.optJSONObject("to")?.optString("name", "")?.takeIf   { it.isNotBlank() && it != "Destination" }

                    when (mode) {
                        "WALK" -> {
                            val distStr = if (distanceM > 0) " ~${distanceM}m" else ""
                            legParts.add("🚶 Walk$distStr")
                        }
                        "BUS", "TRAM", "SUBWAY", "FERRY" -> {
                            val routeObj   = leg.optJSONObject("route")
                            val agencyName = routeObj?.optJSONObject("agency")?.optString("name", "")?.takeIf { it.isNotBlank() }
                            val longName   = routeObj?.optString("longName", "")?.takeIf { it.isNotBlank() }

                            val routeLabel = listOfNotNull(longName, agencyName).joinToString(", ")
                                .ifBlank { mode.lowercase().replaceFirstChar { it.uppercase() } }

                            val emoji = when (mode) {
                                "TRAM"   -> "🚋"
                                "SUBWAY" -> "🚇"
                                "FERRY"  -> "⛴"
                                else     -> "🚌"
                            }

                            val stopDetail = when {
                                fromName != null && toName != null -> " ($fromName → $toName)"
                                toName   != null                  -> " (to $toName)"
                                else                              -> ""
                            }
                            legParts.add("$emoji $routeLabel$stopDetail")

                            if (firstTransit) {
                                primaryRoute = longName ?: agencyName ?: "Transit"
                                firstTransit = false
                            }
                        }
                        else -> legParts.add(mode.lowercase().replaceFirstChar { it.uppercase() })
                    }
                }

                val legsDesc   = legParts.joinToString("  →  ")
                val durationStr = if (durationMin > 0) "$durationMin min" else "–"

                withContext(Dispatchers.Main) {
                    if (allPoints.isNotEmpty()) drawBusRoute(allPoints)
                    routeInfoSheet?.updateTransitData(primaryRoute, durationStr, legsDesc)
                }

            } catch (e: Exception) {
                Log.e("OTP", "Bus route fetch failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    routeInfoSheet?.updateTransitData(
                        "Unavailable", "–", "Bus route unavailable: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    private fun drawRoute(map: MapLibreMap, routePoints: List<LatLng>) {

        val coordinates = routePoints.map {
            Point.fromLngLat(it.longitude, it.latitude)
        }

        val lineString = LineString.fromLngLats(coordinates)
        val feature = Feature.fromGeometry(lineString)

        val geoJsonSource = GeoJsonSource("route-source", feature)

        val style = map.style ?: return

        if (style.getSource("route-source") == null) {

            style.addSource(geoJsonSource)

            val lineLayer = LineLayer("route-layer", "route-source").withProperties(
                lineColor("#007AFF"),
                lineWidth(6f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND)
            )

            style.addLayer(lineLayer)

        } else {

            val source = style.getSourceAs<GeoJsonSource>("route-source")
            source?.setGeoJson(feature)

        }
    }

    private fun fetchRouteFromOSRM(
        origin: LatLng,
        destination: LatLng,
        onResult: (String) -> Unit
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // In fetchRouteFromOSRM — update the URL
                val url = "https://router.project-osrm.org/route/v1/driving/" +
                        "${origin.longitude},${origin.latitude};" +
                        "${destination.longitude},${destination.latitude}" +
                        "?overview=full&geometries=geojson&steps=true"   // ← add steps=true

                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.setRequestProperty(
                    "User-Agent",
                    "NavEz/1.0 (your-email@example.com)"
                )

                val responseCode = connection.responseCode
                if (responseCode != 200) {
                    throw Exception("OSRM error: $responseCode")
                }

                val response = connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

                withContext(Dispatchers.Main) {
                    onResult(response)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Routing failed: ${e.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }



    private fun updateAlarmRadius(radius: Int) {
        val dest = destinationPoint
        if (dest == null) {
            Toast.makeText(this, "Set a destination first", Toast.LENGTH_SHORT).show()
            return
        }

        val style = mapLibreMap.style ?: return
        val point = Point.fromLngLat(dest.longitude, dest.latitude)
        val feature = Feature.fromGeometry(point)

        // Converts real-world meters → pixels at each zoom level for the destination's latitude
        fun metersToPixelsExpression(meters: Int): Expression {
            val lat = dest.latitude
            // Earth's circumference in meters at the given latitude
            val metersPerPixelAtZoom0 = 78271.484 * Math.cos(Math.toRadians(lat))
            return interpolate(
                exponential(2),
                zoom(),
                *Array(22) { z ->
                    val pixelsAtZoom = meters / (metersPerPixelAtZoom0 / Math.pow(2.0, z.toDouble()))
                    stop(z, pixelsAtZoom.toFloat())
                }
            )
        }

        if (style.getSource("radius-source") == null) {
            val source = GeoJsonSource("radius-source", feature)
            style.addSource(source)

            val circleLayer = CircleLayer("radius-layer", "radius-source")
                .withProperties(
                    circleRadius(metersToPixelsExpression(radius)),
                    circleColor("rgba(0, 0, 0, 0)"),
                    circleStrokeColor("#3366FF"),
                    circleStrokeWidth(2f),
                    circleOpacity(0.15f)
                )
            style.addLayer(circleLayer)
        } else {
            val source = style.getSourceAs<GeoJsonSource>("radius-source")
            source?.setGeoJson(feature)

            val layer = style.getLayer("radius-layer") as? CircleLayer
            layer?.setProperties(
                circleRadius(metersToPixelsExpression(radius)),
                circleColor("rgba(0, 0, 0, 0)"),
                circleStrokeColor("#3366FF"),
                circleStrokeWidth(2f),
                circleOpacity(0.15f)
            )
        }
    }

    override fun onRadiusChanged(radiusMeters: Int) {

        this.radiusMeters = radiusMeters

        updateAlarmRadius(radiusMeters)

        adjustMapZoom(radiusMeters)
    }
    override fun onCancelAlarm() {
        mapLibreMap.style?.let { style ->
            style.removeLayer("radius-layer")
            style.removeSource("radius-source")
        }
        isAlarmActive = false
        Toast.makeText(this, "Alarm Cancelled", Toast.LENGTH_SHORT).show()
    }
    private fun adjustMapZoom(radius: Int) {
        val dest = destinationPoint ?: return   // ✅ zoom to destination

        val zoom = when {
            radius <= 200  -> 16.0
            radius <= 500  -> 15.0
            radius <= 1000 -> 14.0
            radius <= 2000 -> 13.0
            else           -> 12.0
        }

        mapLibreMap.animateCamera(
            CameraUpdateFactory.newLatLngZoom(dest, zoom), 500
        )
    }
    private fun calculateAndDisplayRoute(origin: LatLng, destination: LatLng) {
        fetchRouteFromOSRM(origin, destination) { response ->
            val json = JSONObject(response)
            val route = json.getJSONArray("routes").getJSONObject(0)
            val geometry = route.getJSONObject("geometry")
            val coordinates = geometry.getJSONArray("coordinates")

            val routePoints = mutableListOf<LatLng>()
            for (i in 0 until coordinates.length()) {
                val point = coordinates.getJSONArray(i)
                routePoints.add(LatLng(point.getDouble(1), point.getDouble(0)))
            }
            drawRoute(mapLibreMap, routePoints)
            addOrUpdateCircleMarker(origin.latitude, origin.longitude, "start-source", "start-layer", "#007AFF")

            // Parse turn-by-turn steps
            val steps = mutableListOf<RouteStep>()
            val legs = route.optJSONArray("legs")
            if (legs != null) {
                for (i in 0 until legs.length()) {
                    val legSteps = legs.getJSONObject(i).optJSONArray("steps") ?: continue
                    for (j in 0 until legSteps.length()) {
                        val s = legSteps.getJSONObject(j)
                        val maneuver = s.optJSONObject("maneuver")
                        val name = s.optString("name", "").ifBlank { "Continue" }
                        val modifier = maneuver?.optString("modifier", "") ?: ""
                        val type = maneuver?.optString("type", "") ?: ""
                        val distanceM = s.optDouble("distance", 0.0).toInt()
                        steps.add(RouteStep(name, type, modifier, distanceM))
                    }
                }
            }

            val distanceKm = String.format("%.1f", route.optDouble("distance", 0.0) / 1000)
            val durationMin = (route.optDouble("duration", 0.0) / 60).toInt()

            val existing = routeInfoSheet
            if (existing == null || !existing.isAdded) {
                val sheet = RouteInfoBottomSheetFragment.newInstance()
                sheet.show(supportFragmentManager, "RouteInfo")
                routeInfoSheet = sheet
            }
            routeInfoSheet?.setDrivingData(steps, durationMin, distanceKm)

            fetchBusRouteFromOTP(origin, destination)
            checkSunSide(origin, destination)
        }
    }

    // Simple data class
    data class RouteStep(
        val streetName: String,
        val type: String,       // "turn", "depart", "arrive", etc.
        val modifier: String,   // "left", "right", "straight", etc.
        val distanceMeters: Int
    ) {
        val icon: String get() = when {
            type == "arrive"                    -> "⊙"
            type == "depart"                    -> "↑"
            modifier == "left"                  -> "↰"
            modifier == "right"                 -> "↱"
            modifier == "sharp left"            -> "↩"
            modifier == "sharp right"           -> "↪"
            modifier == "slight left"           -> "↖"
            modifier == "slight right"          -> "↗"
            modifier.contains("uturn")         -> "↺"
            else                               -> "↑"
        }

        val label: String get() = when (type) {
            "depart" -> "Head ${modifier.ifBlank { "forward" }} on $streetName"
            "arrive" -> "Arrive at destination"
            "turn"   -> "Turn $modifier onto $streetName"
            "roundabout", "rotary" -> "Take the roundabout onto $streetName"
            "merge"  -> "Merge onto $streetName"
            "fork"   -> "Keep $modifier at the fork"
            else     -> if (streetName.isNotBlank()) "Continue on $streetName" else "Continue"
        }

        val distanceLabel: String get() = when {
            distanceMeters == 0      -> ""
            distanceMeters < 1000   -> "${distanceMeters}m"
            else                    -> String.format("%.1f km", distanceMeters / 1000.0)
        }
    }

    // INSIDE MainActivity class, alongside drawRoute / drawBusRoute / clearRoute
    fun onMapModeChanged(mode: RouteInfoBottomSheetFragment.Mode) {
        val style = mapLibreMap.style ?: return
        val showDriving = mode == RouteInfoBottomSheetFragment.Mode.DRIVING
        style.getLayer("route-layer")?.setProperties(
            visibility(if (showDriving) Property.VISIBLE else Property.NONE)
        )
        style.getLayer("bus-route-layer")?.setProperties(
            visibility(if (showDriving) Property.NONE else Property.VISIBLE)
        )
    }

    /*private fun fetchSunSide(origin: LatLng, destination: LatLng) {
        val url = "http://10.0.2.2:3001/api/sun-side" +
            "?originLat=${origin.latitude}" +
            "&originLon=${origin.longitude}" +
            "&destLat=${destination.latitude}" +
            "&destLon=${destination.longitude}"

        val client = OkHttpClient()
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    busInfoSheet?.updateData("–", "–", "Could not fetch seating advice")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                try {
                    val json = JSONObject(body)
                    val advice  = json.optString("advice", "No advice available")
                    val sitSide = json.optString("sit_side", "–")
                    runOnUiThread {
                        busInfoSheet?.updateData(sitSide, "–", advice)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        busInfoSheet?.updateData("–", "–", "Could not parse seating advice")
                    }
                }
            }
        })
    }*/

    private fun resetDirectionsPanel(
        directionsPanel: MaterialCardView,
        searchBar: MaterialCardView,
        etOrigin: EditText,
        etDestination: EditText,
        btnSearchOrigin: ImageButton,
        btnSearchDestination: ImageButton,
        btnCalculateRoute: Button,
        closeDirections: LinearLayout


    ) {

        directionsPanel.visibility = View.GONE
        searchBar.visibility = View.VISIBLE
        closeDirections.visibility = View.GONE

        etOrigin.text.clear()
        etDestination.text.clear()
        etOrigin.isEnabled = true
        etDestination.isEnabled = false

        btnSearchOrigin.setImageResource(android.R.drawable.ic_menu_search)
        btnSearchOrigin.tag = "search"
        btnSearchDestination.setImageResource(android.R.drawable.ic_menu_search)
        btnSearchDestination.tag = "search"
        btnSearchDestination.isEnabled = false

        btnCalculateRoute.isEnabled = false

        startPoint = null
        destinationPoint = null

        removeMarker("start-source", "start-layer")
        removeMarker("end-source", "end-layer")
        clearRoute()

        routeInfoSheet?.dismiss()
        routeInfoSheet = null
    }

// Map style changing
    private val styles = listOf(// Array for style url
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
            pendingActionAfterGps?.invoke()
            pendingActionAfterGps = null
        }

    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
fun onGetDirectionsClicked() {
        val searchBar = findViewById<MaterialCardView>(R.id.searchBar)
        val btnGetDirections = findViewById<Button>(R.id.btnGetDirections)
        val closeDirections = findViewById<View>(R.id.btnCloseDirections)
        val directionsPanel = findViewById<View>(R.id.directionsPanel)
        val searchEditText = findViewById<EditText>(R.id.searchEditText)
        val btnSearch = findViewById<ImageButton>(R.id.btnSearch)

        searchBar.visibility = View.GONE
        btnGetDirections.visibility = View.GONE
        closeDirections.visibility = View.VISIBLE
        directionsPanel.visibility = View.VISIBLE
        searchEditText.text.clear()
        btnSearch.setImageResource(android.R.drawable.ic_menu_search)
        btnSearch.tag = "search"

        placeDetailsSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        // Set pending action: once permission + GPS are confirmed, auto-fill origin
        val etOrigin = findViewById<EditText>(R.id.etOrigin)
        val btnSearchOrigin = findViewById<ImageButton>(R.id.btnSearchOrigin)
        pendingActionAfterGps = { autoFillOriginWithUserLocation(etOrigin, btnSearchOrigin) }

        // Reuse existing function — handles permission request + GPS dialog
        handleMyLocationClick()
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop()
            super.onStop()
            locationComponent?.onStop()
            mapView.onStop()

    }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
}