package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.ImageButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class RouteSearchFragment : Fragment() {

    private val client = OkHttpClient()

    private val accentBlue = Color.parseColor("#007AFF")
    private val lightGrey = Color.parseColor("#F2F2F7")
    private val medGrey = Color.parseColor("#C7C7CC")
    private val darkText = Color.parseColor("#1C1C1E")

    data class RouteRow(
        val id: Int,
        val otpRouteId: String?,
        val shortName: String?,
        val longName: String?,
        val operator: String?,
        val originName: String?,
        val destName: String?,
        val source: String?,
        val stopIds: JSONArray,
    )

    private lateinit var etRouteName: EditText
    private lateinit var etOriginStop: EditText
    private lateinit var etDestStop: EditText
    private lateinit var resultsContainer: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()

        val scroll = ScrollView(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val handleRow = LinearLayout(ctx).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER
    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
}
val handle = View(ctx).apply {
    background = roundedRect(medGrey, 3f)
    layoutParams = LinearLayout.LayoutParams(dp(40), dp(4)).apply {
        topMargin = dp(8)
        bottomMargin = dp(8)
    }
}
handleRow.addView(handle)
root.addView(handleRow) 

        val topBar = LinearLayout(ctx).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.END
}
val closeBtn = ImageButton(ctx).apply {
    setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
    setBackgroundColor(Color.TRANSPARENT)
}
closeBtn.setOnClickListener {
    // Hide the bottom sheet entirely
    val activity = requireActivity() as? androidx.appcompat.app.AppCompatActivity
    val sheet = activity?.findViewById<android.widget.LinearLayout>(R.id.placeDetailsSheet)
    sheet?.let {
        com.google.android.material.bottomsheet.BottomSheetBehavior.from(it).state =
            com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
    }
}
topBar.addView(closeBtn)
root.addView(topBar)

        val title = TextView(ctx).apply {
            text = "Find your bus"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(darkText)
        }
        root.addView(title)

        root.addView(View(ctx).apply {
            setBackgroundColor(accentBlue)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2)).apply {
                topMargin = dp(8)
                bottomMargin = dp(12)
            }
        })

        etRouteName = styledEditText("Route number or name (e.g. 223, Fast Passenger)")
        etOriginStop = styledEditText("From (boarding stop name)")
        etDestStop = styledEditText("To (final destination of bus)")

        val btnNearby = styledButton("Show routes near me")
        val btnSearch = styledButton("Search")
        val btnNewRoute = styledButton("+ add unlisted route" )

        resultsContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(12)
                bottomMargin = dp(12)
            }
        }

        root.addView(etRouteName)
        root.addView(etOriginStop)
        root.addView(etDestStop)
        root.addView(btnNearby)
        root.addView(btnSearch)
        root.addView(btnNewRoute)
        root.addView(resultsContainer)


        btnSearch.setOnClickListener {
            searchByText()
        }

        etRouteName.setOnEditorActionListener { _, _, _ ->
            searchByText()
            true
        }

        btnNearby.setOnClickListener {
            searchNearby()
        }

        btnNewRoute.setOnClickListener {
            openContribute(null)
        }

        scroll.addView(root)
        return scroll
    }

    private fun searchByText() {
        val routeQ = etRouteName.text.toString().trim()
        val origin = etOriginStop.text.toString().trim()
        val dest = etDestStop.text.toString().trim()

        val params = mutableListOf<String>()
        if (routeQ.isNotBlank()) {
            params.add("q=${URLEncoder.encode(routeQ, "UTF-8")}")
        }
        if (origin.isNotBlank()) {
            params.add("originStopId=${URLEncoder.encode(origin, "UTF-8")}")
        }
        if (dest.isNotBlank()) {
            params.add("destStopId=${URLEncoder.encode(dest, "UTF-8")}")
        }

        val url = if (params.isEmpty()) {
            "http://139.59.65.249:3001/api/routes/search"
        } else {
            "http://139.59.65.249:3001/api/routes/search?${params.joinToString("&")}"
        }
        fetchRoutes(url)
    }

    private fun searchNearby() {
        val ctx = requireContext()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 2002)
            Toast.makeText(ctx, "Location permission required", Toast.LENGTH_SHORT).show()
            return
        }

        val fused = LocationServices.getFusedLocationProviderClient(ctx)
        fused.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val url = "http://139.59.65.249:3001/api/routes/search?lat=${location.latitude}&lng=${location.longitude}"
                fetchRoutes(url)
            } else {
                val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 0)
                    .setMaxUpdates(1)
                    .build()
                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        fused.removeLocationUpdates(this)
                        val fresh = result.lastLocation ?: return
                        val url = "http://139.59.65.249:3001/api/routes/search?lat=${fresh.latitude}&lng=${fresh.longitude}"
                        fetchRoutes(url)
                    }
                }
                fused.requestLocationUpdates(request, callback, requireActivity().mainLooper)
            }
        }
    }

    private fun fetchRoutes(url: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: "{}"
                if (!response.isSuccessful) {
                    throw IllegalStateException("Search failed")
                }

                val json = JSONObject(body)
                val rows = json.optJSONArray("routes") ?: JSONArray()
                val parsed = mutableListOf<RouteRow>()
                for (i in 0 until rows.length()) {
                    val r = rows.getJSONObject(i)
                    parsed.add(
                        RouteRow(
                            id = r.optInt("id", 0),
                            otpRouteId = r.optString("otp_route_id", null),
                            shortName = r.optString("operator", null),
                            longName = r.optString("long_name", null),
                            operator = r.optString("operator", null),
                            originName = r.optString("origin_name", null),
                            destName = r.optString("dest_name", null),
                            source = r.optString("source", null),
                            stopIds = r.optJSONArray("stop_ids") ?: JSONArray(),
                        )
                    )
                }

                withContext(Dispatchers.Main) {
                    renderResults(parsed)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Search failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun renderResults(routes: List<RouteRow>) {
        val ctx = requireContext()
        resultsContainer.removeAllViews()

        if (routes.isEmpty()) {
            resultsContainer.addView(TextView(ctx).apply {
                text = "No matching routes found"
                textSize = 14f
                setTextColor(medGrey)
            })
            return
        }

        routes.forEach { route ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                background = roundedRect(lightGrey, 12f, medGrey, 1f)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = dp(8)
                }
            }

            val header = TextView(ctx).apply {
                val sn = route.shortName ?: "Unknown"
                val ln = route.longName ?: "Route"
                text = "$sn  $ln"
                setTextColor(darkText)
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
            }

            val details = TextView(ctx).apply {
                val op = route.operator ?: "Unknown operator"
                val src = route.source ?: "crowdsourced"
                val count = route.stopIds.length()
                val origin = route.originName ?: "-"
                val dest = route.destName ?: "-"
                text = "$op • $src • $count stops\n$origin -> $dest"
                setTextColor(Color.parseColor("#3A3A3C"))
                textSize = 13f
            }

            row.addView(header)
            row.addView(details)
            row.setOnClickListener {
                openContribute(route)
            }
            resultsContainer.addView(row)
        }
    }

    private fun openContribute(route: RouteRow?) {
        val fragment = ContributeFragment()
        if (route != null) {
            val args = Bundle().apply {
                putString("otpRouteId", route.otpRouteId)
                putString("shortName", route.shortName)
                putString("longName", route.longName)
                putString("originName", route.originName)
                putString("destName", route.destName)
                putString("stopIdsJson", route.stopIds.toString())
            }
            fragment.arguments = args
        }

        parentFragmentManager.beginTransaction()
            .replace(R.id.placeDetailsContainer, fragment, "Contribute")
            .addToBackStack(null)
            .commit()
    }

    private fun styledEditText(hint: String): EditText {
        return EditText(requireContext()).apply {
            this.hint = hint
            setTextColor(darkText)
            setHintTextColor(medGrey)
            background = roundedRect(lightGrey, 10f, medGrey, 1f)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(10)
            }
        }
    }

    private fun styledButton(text: String): Button {
        return Button(requireContext()).apply {
            this.text = text
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(accentBlue)
            gravity = Gravity.CENTER
            background = roundedRect(accentBlue, 12f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(8)
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

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
