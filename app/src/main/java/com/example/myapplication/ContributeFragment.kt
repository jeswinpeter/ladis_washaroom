package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Instant
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ContributeFragment : Fragment() {

    data class StopEntry(
        val stopId: String?,
        val name: String,
        val lat: Double,
        val lng: Double,
        val timeIso: String,
        val confirmed: Boolean,
        val mayStop: Boolean,
        val sampleCount: Int = 1,
    )

    private val recordedStops = mutableListOf<StopEntry>()
    private val gpsTrace = mutableListOf<Map<String, Double>>()
    private val mayStopCandidates = mutableListOf<StopEntry>()
    private var isSessionActive = false
    private var sessionMode: String = "new_route"
    private var otpRouteId: String? = null
    private var declaredFinalStopId: String? = null
    private var declaredFinalStopName: String? = null
    private var declaredFinalLat: Double? = null
    private var declaredFinalLon: Double? = null
    private lateinit var deviceId: String

    private val client = OkHttpClient()
    private lateinit var fused: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    private lateinit var statusText: TextView
    private lateinit var etBusName: EditText
    private lateinit var etFinalStop: EditText
    private lateinit var btnStartSession: Button
    private lateinit var btnAddStop: Button
    private lateinit var btnSubmit: Button
    private lateinit var stopCountText: TextView
    private lateinit var stopsContainer: LinearLayout
    private lateinit var mayStopSection: LinearLayout
    private lateinit var mayStopContainer: LinearLayout
    private lateinit var modeSelector: LinearLayout
    private lateinit var btnModeTimeOnly: Button
    private lateinit var btnModeFullConfirm: Button

    private val accentBlue = Color.parseColor("#007AFF")
    private val lightGrey = Color.parseColor("#F2F2F7")
    private val medGrey = Color.parseColor("#C7C7CC")
    private val darkText = Color.parseColor("#1C1C1E")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fused = LocationServices.getFusedLocationProviderClient(requireContext())
        computeDeviceId()

        otpRouteId = arguments?.getString("otpRouteId")
        if (!otpRouteId.isNullOrBlank()) {
            sessionMode = "time_only"
        }

        val scroll = ScrollView(requireContext())
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val topBar = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val closeBtn = ImageButton(requireContext()).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.TRANSPARENT)
        }
        closeBtn.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        topBar.addView(closeBtn)
        root.addView(topBar)

        val title = TextView(requireContext()).apply {
            text = "Contribute Route Data"
            textSize = 22f
            setTextColor(darkText)
            setTypeface(null, Typeface.BOLD)
        }
        root.addView(title)

        root.addView(View(requireContext()).apply {
            setBackgroundColor(accentBlue)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2)).apply {
                topMargin = dp(8)
                bottomMargin = dp(12)
            }
        })

        statusText = TextView(requireContext()).apply {
            text = "Ready to start contribution session"
            textSize = 14f
            setTextColor(Color.parseColor("#3A3A3C"))
        }
        root.addView(statusText)

        etBusName = styledEditText("Bus name or number")
        etBusName.setText(arguments?.getString("shortName") ?: "")
        root.addView(etBusName)

        etFinalStop = styledEditText("Final destination of this bus")
        etFinalStop.setText(arguments?.getString("destName") ?: "")
        root.addView(etFinalStop)

        modeSelector = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = if (otpRouteId.isNullOrBlank()) View.GONE else View.VISIBLE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            }
        }
        btnModeTimeOnly = modeButton("Update times only")
        btnModeFullConfirm = modeButton("Verify stops + times")
        modeSelector.addView(btnModeTimeOnly, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        modeSelector.addView(btnModeFullConfirm, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(modeSelector)

        btnModeTimeOnly.setOnClickListener {
            sessionMode = "time_only"
            etFinalStop.visibility = View.GONE
            refreshModeStyles()
            renderKnownStopsForTimeOnlyMode()
        }
        btnModeFullConfirm.setOnClickListener {
            sessionMode = "full_confirm"
            etFinalStop.visibility = View.VISIBLE
            if (etFinalStop.text.isNullOrBlank()) {
                etFinalStop.setText(arguments?.getString("destName") ?: "")
            }
            refreshModeStyles()
            stopsContainer.removeAllViews()
            rerenderRecordedStops()
        }

        btnStartSession = styledPrimaryButton("Start Session")
        btnAddStop = styledPrimaryButton("Add Stop").apply { isEnabled = false }
        btnSubmit = styledPrimaryButton("Submit").apply { isEnabled = false }

        stopCountText = TextView(requireContext()).apply {
            text = "Stops recorded: 0"
            textSize = 14f
            setTextColor(Color.parseColor("#3A3A3C"))
            setPadding(0, dp(6), 0, dp(6))
        }

        stopsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        mayStopSection = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = roundedRect(lightGrey, 12f, medGrey, 1f)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            }
        }

        val mayTitle = TextView(requireContext()).apply {
            text = "May stop"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(darkText)
        }
        mayStopContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        mayStopSection.addView(mayTitle)
        mayStopSection.addView(mayStopContainer)

        root.addView(btnStartSession)
        root.addView(btnAddStop)
        root.addView(stopCountText)
        root.addView(stopsContainer)
        root.addView(mayStopSection)
        root.addView(btnSubmit)

        btnStartSession.setOnClickListener {
            if (isSessionActive) {
                pauseSession()
            } else {
                startSession()
            }
        }

        btnAddStop.setOnClickListener {
            onAddStopClicked()
        }

        btnSubmit.setOnClickListener {
            submitContribution()
        }

        refreshModeStyles()
        if (sessionMode == "time_only") {
            etFinalStop.visibility = View.GONE
            renderKnownStopsForTimeOnlyMode()
        }

        scroll.addView(root)
        return scroll
    }

    private fun nowIso(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun computeDeviceId() {
        val rawId = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
        deviceId = MessageDigest.getInstance("SHA-256")
            .digest(rawId.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)
    }

    private fun refreshModeStyles() {
        btnModeTimeOnly.background = roundedRect(if (sessionMode == "time_only") accentBlue else medGrey, 10f)
        btnModeFullConfirm.background = roundedRect(if (sessionMode == "full_confirm") accentBlue else medGrey, 10f)
    }

    private fun startSession() {
        val busName = etBusName.text.toString().trim()
        if (busName.isBlank()) {
            Toast.makeText(requireContext(), "Bus name is required", Toast.LENGTH_SHORT).show()
            return
        }

        if (sessionMode == "new_route" || sessionMode == "full_confirm") {
            val finalName = etFinalStop.text.toString().trim()
            if (finalName.isBlank()) {
                Toast.makeText(requireContext(), "Final destination is required", Toast.LENGTH_SHORT).show()
                return
            }
            geocodeAndSnapFinalStop(finalName) {
                activateSession()
            }
        } else {
            activateSession()
        }
    }

    private fun activateSession() {
        isSessionActive = true
        btnStartSession.text = "Pause Session"
        btnAddStop.isEnabled = true
        statusText.text = "Session active - recording trace"
        startGpsTraceRecording()
    }

    private fun pauseSession() {
        isSessionActive = false
        btnStartSession.text = "Start Session"
        btnAddStop.isEnabled = false
        statusText.text = "Session paused"
        stopGpsTraceRecording()
    }

    private fun startGpsTraceRecording() {
        val ctx = requireContext()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L).build()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                gpsTrace.add(mapOf("lat" to loc.latitude, "lon" to loc.longitude))
            }
        }
        fused.requestLocationUpdates(req, locationCallback!!, requireActivity().mainLooper)
    }

    private fun stopGpsTraceRecording() {
        locationCallback?.let { fused.removeLocationUpdates(it) }
        locationCallback = null
    }

    private fun onAddStopClicked() {
        val ctx = requireContext()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 3001)
            Toast.makeText(ctx, "Location permission required", Toast.LENGTH_SHORT).show()
            return
        }

        fused.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                snapOrPrompt(location.latitude, location.longitude)
            } else {
                val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 0)
                    .setMaxUpdates(1)
                    .build()
                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        fused.removeLocationUpdates(this)
                        val fresh = result.lastLocation ?: return
                        snapOrPrompt(fresh.latitude, fresh.longitude)
                    }
                }
                fused.requestLocationUpdates(req, callback, requireActivity().mainLooper)
            }
        }
    }

    private fun snapOrPrompt(lat: Double, lng: Double) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("http://139.59.65.249:3001/api/stops/nearby?lat=$lat&lng=$lng&radiusM=100")
                    .get()
                    .build()
                val rsp = client.newCall(req).execute()
                val body = rsp.body?.string() ?: "{}"
                val json = JSONObject(body)
                val stops = json.optJSONArray("stops") ?: JSONArray()

                if (stops.length() > 0) {
                    val stop = stops.getJSONObject(0)
                    withContext(Dispatchers.Main) {
                        showFoundNearbyDialog(stop, lat, lng)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        promptNewStop(lat, lng)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Stop lookup failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showFoundNearbyDialog(stop: JSONObject, lat: Double, lng: Double) {
        val name = stop.optString("stop_name", "Nearby Stop")
        val id = stop.optString("stop_id", null)
        val dist = stop.optDouble("distance_m", 0.0)

        AlertDialog.Builder(requireContext())
            .setTitle("Stop found nearby")
            .setMessage("$name (${String.format("%.1f", dist)}m)")
            .setPositiveButton("Add $name") { _, _ ->
                addConfirmedStop(
                    StopEntry(
                        stopId = id,
                        name = name,
                        lat = stop.optDouble("lat", lat),
                        lng = stop.optDouble("lng", lng),
                        timeIso = nowIso(),
                        confirmed = true,
                        mayStop = false,
                    )
                )
            }
            .setNegativeButton("Not this stop") { _, _ ->
                promptNewStop(lat, lng)
            }
            .show()
    }

    private fun promptNewStop(lat: Double, lng: Double) {
        val input = EditText(requireContext()).apply {
            hint = "Stop name"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Create new stop")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                createStopOnServer(name, lat, lng)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createStopOnServer(name: String, lat: Double, lng: Double) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("name", name)
                    put("lat", lat)
                    put("lng", lng)
                }
                val req = Request.Builder()
                    .url("http://139.59.65.249:3001/api/stops")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                val rsp = client.newCall(req).execute()
                val body = rsp.body?.string() ?: "{}"
                val json = JSONObject(body)
                val stop = json.optJSONObject("stop") ?: JSONObject()

                val entry = StopEntry(
                    stopId = stop.optString("stop_id", null),
                    name = stop.optString("stop_name", "Unnamed Stop"),
                    lat = stop.optDouble("lat", lat),
                    lng = stop.optDouble("lng", lng),
                    timeIso = nowIso(),
                    confirmed = true,
                    mayStop = false,
                )

                withContext(Dispatchers.Main) {
                    addConfirmedStop(entry)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to create stop", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun addConfirmedStop(entry: StopEntry) {
        recordedStops.add(entry)
        rerenderRecordedStops()
        btnSubmit.isEnabled = recordedStops.any { it.confirmed && !it.mayStop }
        fetchMayStopCandidates()
    }

    private fun rerenderRecordedStops() {
        stopsContainer.removeAllViews()
        recordedStops.filter { !it.mayStop }.forEachIndexed { idx, stop ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                background = roundedRect(lightGrey, 12f, medGrey, 1f)
                setPadding(dp(10), dp(8), dp(10), dp(8))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                }
            }
            row.addView(TextView(requireContext()).apply {
                text = "${idx + 1}. ${stop.name}"
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(darkText)
            })
            row.addView(TextView(requireContext()).apply {
                text = stop.timeIso
                textSize = 12f
                setTextColor(Color.parseColor("#3A3A3C"))
            })
            stopsContainer.addView(row)
        }
        stopCountText.text = "Stops recorded: ${recordedStops.count { !it.mayStop }}"
    }

    private fun renderKnownStopsForTimeOnlyMode() {
        val raw = arguments?.getString("stopIdsJson") ?: "[]"
        val arr = try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }

        stopsContainer.removeAllViews()
        for (i in 0 until arr.length()) {
            val stopId = arr.optString(i)
            val btn = Button(requireContext()).apply {
                text = "Tap when at stop: $stopId"
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = roundedRect(accentBlue, 10f)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                }
            }
            btn.setOnClickListener {
                addConfirmedStop(
                    StopEntry(
                        stopId = stopId,
                        name = stopId,
                        lat = 0.0,
                        lng = 0.0,
                        timeIso = nowIso(),
                        confirmed = true,
                        mayStop = false,
                    )
                )
            }
            stopsContainer.addView(btn)
        }
    }

    private fun fetchMayStopCandidates() {
        if (recordedStops.none { it.confirmed && !it.mayStop }) return
        val finalLat = declaredFinalLat
        val finalLon = declaredFinalLon
        val lastConfirmed = recordedStops.lastOrNull { !it.mayStop } ?: return
        if (finalLat == null || finalLon == null) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val nearbyReq = Request.Builder()
                    .url("http://139.59.65.249:3001/api/stops/nearby?lat=$finalLat&lng=$finalLon&radiusM=1000")
                    .get()
                    .build()
                val nearbyRes = client.newCall(nearbyReq).execute()
                val nearbyJson = JSONObject(nearbyRes.body?.string() ?: "{}")
                val nearStops = nearbyJson.optJSONArray("stops") ?: JSONArray()

                val corridorReq = Request.Builder()
                    .url("http://139.59.65.249:3001/api/stops/corridor?lat1=${lastConfirmed.lat}&lon1=${lastConfirmed.lng}&lat2=$finalLat&lon2=$finalLon")
                    .get()
                    .build()
                val corridorRes = client.newCall(corridorReq).execute()
                val corridorJson = JSONObject(corridorRes.body?.string() ?: "{}")
                val corridorStops = corridorJson.optJSONArray("stops") ?: JSONArray()

                val picked = linkedMapOf<String, StopEntry>()
                fun pull(arr: JSONArray) {
                    for (i in 0 until arr.length()) {
                        val s = arr.getJSONObject(i)
                        val id = s.optString("stop_id", null) ?: continue
                        if (!picked.containsKey(id) && recordedStops.none { it.stopId == id }) {
                            picked[id] = StopEntry(
                                stopId = id,
                                name = s.optString("stop_name", id),
                                lat = s.optDouble("stop_lat", s.optDouble("lat", 0.0)),
                                lng = s.optDouble("stop_lon", s.optDouble("lng", 0.0)),
                                timeIso = nowIso(),
                                confirmed = false,
                                mayStop = true,
                            )
                        }
                    }
                }

                pull(nearStops)
                pull(corridorStops)

                withContext(Dispatchers.Main) {
                    mayStopCandidates.clear()
                    mayStopCandidates.addAll(picked.values.take(10))
                    renderMayStops()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun renderMayStops() {
        mayStopContainer.removeAllViews()
        if (mayStopCandidates.isEmpty()) {
            mayStopSection.visibility = View.GONE
            return
        }

        mayStopSection.visibility = View.VISIBLE
        mayStopCandidates.forEach { stop ->
            val chip = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = roundedRect(Color.WHITE, 10f, medGrey, 1f)
                setPadding(dp(8), dp(6), dp(8), dp(6))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(6)
                }
            }
            val stopNameView = TextView(requireContext()).apply {
                text = stop.name
                setTextColor(darkText)
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val remove = TextView(requireContext()).apply {
                text = "Remove"
                setTextColor(accentBlue)
                textSize = 12f
            }
            remove.setOnClickListener {
                mayStopCandidates.remove(stop)
                renderMayStops()
            }
            chip.addView(stopNameView)
            chip.addView(remove)
            mayStopContainer.addView(chip)
        }
    }

    private fun geocodeAndSnapFinalStop(finalStopName: String, onDone: () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(finalStopName, "UTF-8")
                val url = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1"
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("User-Agent", "NavEz/2.0 (android)")
                conn.connect()

                if (conn.responseCode != 200) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to resolve final stop", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val raw = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(raw)
                if (arr.length() == 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Final stop not found", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val first = arr.getJSONObject(0)
                val lat = first.getString("lat").toDouble()
                val lon = first.getString("lon").toDouble()

                val req = Request.Builder()
                    .url("http://139.59.65.249:3001/api/stops/nearby?lat=$lat&lng=$lon&radiusM=200")
                    .get()
                    .build()
                val res = client.newCall(req).execute()
                val json = JSONObject(res.body?.string() ?: "{}")
                val stops = json.optJSONArray("stops") ?: JSONArray()

                if (stops.length() > 0) {
                    val stop = stops.getJSONObject(0)
                    declaredFinalStopId = stop.optString("stop_id", null)
                    declaredFinalStopName = stop.optString("stop_name", finalStopName)
                    declaredFinalLat = stop.optDouble("lat", lat)
                    declaredFinalLon = stop.optDouble("lng", lon)
                } else {
                    declaredFinalStopId = null
                    declaredFinalStopName = finalStopName
                    declaredFinalLat = lat
                    declaredFinalLon = lon
                }

                withContext(Dispatchers.Main) {
                    onDone()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to prepare final stop", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun submitContribution() {
        stopGpsTraceRecording()

        val allStops = mutableListOf<StopEntry>()
        allStops.addAll(recordedStops.filter { !it.mayStop })
        allStops.addAll(mayStopCandidates)

        if (allStops.none { it.confirmed && !it.mayStop }) {
            Toast.makeText(requireContext(), "Add at least one confirmed stop", Toast.LENGTH_SHORT).show()
            return
        }

        val payload = JSONObject().apply {
            put("deviceId", deviceId)
            put("busName", etBusName.text.toString().trim())
            put("mode", sessionMode)
            put("otpRouteId", otpRouteId)
            put("declaredFinalStopId", declaredFinalStopId)
            put("declaredFinalStopName", declaredFinalStopName ?: etFinalStop.text.toString().trim())

            val stopsJson = JSONArray()
            allStops.forEach { s ->
                stopsJson.put(JSONObject().apply {
                    put("stop_id", s.stopId)
                    put("name", s.name)
                    put("lat", s.lat)
                    put("lng", s.lng)
                    put("time", s.timeIso)
                    put("confirmed", s.confirmed)
                    put("may_stop", s.mayStop)
                    put("sample_count", s.sampleCount)
                })
            }
            put("stops", stopsJson)

            val shapeJson = JSONArray()
            gpsTrace.forEach { p ->
                shapeJson.put(JSONObject().apply {
                    put("lat", p["lat"])
                    put("lng", p["lon"])
                })
            }
            put("shapePoints", shapeJson)
        }

        val req = Request.Builder()
            .url("http://139.59.65.249:3001/api/trips")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Submit failed", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val body = response.body?.string() ?: "{}"
                if (!response.isSuccessful) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Submit failed", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                val json = JSONObject(body)
                val newId = json.optInt("id", 0)
                val dupGap = if (json.isNull("duplicate_gap_minutes")) null else json.optDouble("duplicate_gap_minutes")
                val existingId = if (json.isNull("duplicate_existing_id")) null else json.optInt("duplicate_existing_id")

                requireActivity().runOnUiThread {
                    handleDuplicateFlow(newId, allStops, dupGap, existingId)
                }
            }
        })
    }

    private fun handleDuplicateFlow(newId: Int, allStops: List<StopEntry>, duplicateGapMinutes: Double?, existingId: Int?) {
        if (duplicateGapMinutes == null || existingId == null || newId == 0) {
            Toast.makeText(requireContext(), "Thank you! Your contribution is under review.", Toast.LENGTH_LONG).show()
            resetSession()
            return
        }

        if (duplicateGapMinutes < 5.0) {
            Toast.makeText(requireContext(), "Averaged with existing submission for this bus.", Toast.LENGTH_LONG).show()
            mergeWithExisting(newId, existingId, allStops)
            return
        }

        if (duplicateGapMinutes in 5.0..15.0) {
            AlertDialog.Builder(requireContext())
                .setTitle("Possible duplicate")
                .setMessage("Another recording for this bus was made ~${duplicateGapMinutes.toInt()} minutes ago. Same bus running late, or a different bus?")
                .setPositiveButton("Same bus") { _, _ ->
                    mergeWithExisting(newId, existingId, allStops)
                }
                .setNegativeButton("Different bus") { _, _ ->
                    Toast.makeText(requireContext(), "Thank you! Your contribution is under review.", Toast.LENGTH_LONG).show()
                    resetSession()
                }
                .show()
            return
        }

        Toast.makeText(requireContext(), "Thank you! Your contribution is under review.", Toast.LENGTH_LONG).show()
        resetSession()
    }

    private fun mergeWithExisting(newId: Int, existingId: Int, allStops: List<StopEntry>) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val stopsJson = JSONArray()
                allStops.forEach { s ->
                    stopsJson.put(JSONObject().apply {
                        put("stop_id", s.stopId)
                        put("name", s.name)
                        put("lat", s.lat)
                        put("lng", s.lng)
                        put("time", s.timeIso)
                        put("confirmed", s.confirmed)
                        put("may_stop", s.mayStop)
                    })
                }

                val payload = JSONObject().apply {
                    put("existingId", existingId)
                    put("newStops", stopsJson)
                }

                val req = Request.Builder()
                    .url("http://139.59.65.249:3001/api/trips/$newId/merge")
                    .patch(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(req).execute()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Thank you! Your contribution is under review.", Toast.LENGTH_LONG).show()
                    resetSession()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Merge failed", Toast.LENGTH_SHORT).show()
                    resetSession()
                }
            }
        }
    }

    private fun resetSession() {
        recordedStops.clear()
        gpsTrace.clear()
        mayStopCandidates.clear()
        isSessionActive = false
        stopGpsTraceRecording()

        etBusName.text?.clear()
        etFinalStop.text?.clear()
        statusText.text = "Ready to start contribution session"
        btnStartSession.text = "Start Session"
        btnAddStop.isEnabled = false
        btnSubmit.isEnabled = false
        mayStopSection.visibility = View.GONE
        stopsContainer.removeAllViews()
        stopCountText.text = "Stops recorded: 0"

        otpRouteId = arguments?.getString("otpRouteId")
        if (!otpRouteId.isNullOrBlank()) {
            etBusName.setText(arguments?.getString("shortName") ?: "")
            if (sessionMode == "time_only") {
                renderKnownStopsForTimeOnlyMode()
            }
        }
    }

    private fun styledEditText(hintText: String): EditText {
        return EditText(requireContext()).apply {
            hint = hintText
            setTextColor(darkText)
            setHintTextColor(medGrey)
            background = roundedRect(lightGrey, 10f, medGrey, 1f)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            }
        }
    }

    private fun styledPrimaryButton(text: String): Button {
        return Button(requireContext()).apply {
            this.text = text
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = roundedRect(accentBlue, 12f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }
    }

    private fun modeButton(text: String): Button {
        return Button(requireContext()).apply {
            this.text = text
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = 12f
            background = roundedRect(medGrey, 10f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        }
    }

    private fun roundedRect(fillColor: Int, radiusDp: Float, strokeColor: Int = 0, strokeWidthDp: Float = 0f): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = radiusDp * resources.displayMetrics.density
            if (strokeColor != 0) {
                setStroke((strokeWidthDp * resources.displayMetrics.density).toInt(), strokeColor)
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
