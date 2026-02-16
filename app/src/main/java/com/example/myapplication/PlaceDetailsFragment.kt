package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat.startActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class PlaceDetailsFragment : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_place_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val placeName = arguments?.getString("name") ?: "Unknown Place"
        val address = arguments?.getString("address") ?: ""
        val lat = arguments?.getDouble("lat") ?: 0.0
        val lon = arguments?.getDouble("lon") ?: 0.0
        val wikiSummary = arguments?.getString("wiki")

        val tvPlaceName = view.findViewById<TextView>(R.id.tvPlaceName)
        val tvAddress = view.findViewById<TextView>(R.id.tvAddress)
        val tvWikiSummary = view.findViewById<TextView>(R.id.tvWikiSummary)
        val tvCoordinates = view.findViewById<TextView>(R.id.tvCoordinates)
        val btnOpenExternal = view.findViewById<Button>(R.id.btnOpenExternal)

        tvPlaceName.text = placeName
        tvAddress.text = address

        if (!wikiSummary.isNullOrEmpty()) {
            tvWikiSummary.text = wikiSummary
        } else {
            tvWikiSummary.text = "No Wikipedia information available."
        }

        tvCoordinates.text = "Lat: $lat, Lon: $lon"

        btnOpenExternal.setOnClickListener {
            val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon($placeName)")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }
    }
}
