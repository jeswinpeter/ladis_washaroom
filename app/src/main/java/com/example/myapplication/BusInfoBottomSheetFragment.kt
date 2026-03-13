package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class BusInfoBottomSheetFragment : BottomSheetDialogFragment() {

    private var tvSitSideBadge: TextView? = null
    private var tvSitLabel: TextView? = null
    private var tvSunPosition: TextView? = null
    private var tvRecommendation: TextView? = null
    private var tvRouteInfo: TextView? = null
    private var tvDuration: TextView? = null
    private var tvLegsInfo: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_bus_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvSitSideBadge    = view.findViewById(R.id.tvSitSideBadge)
        tvSitLabel        = view.findViewById(R.id.tvSitLabel)
        tvSunPosition     = view.findViewById(R.id.tvSunPosition)
        tvRecommendation  = view.findViewById(R.id.tvRecommendation)
        tvRouteInfo       = view.findViewById(R.id.tvRouteInfo)
        tvDuration        = view.findViewById(R.id.tvDuration)
        tvLegsInfo        = view.findViewById(R.id.tvLegsInfo)

        // Apply any data that arrived before the view was ready
        arguments?.let { args ->
            applyData(
                args.getString(ARG_SIT_SIDE, "–"),
                args.getString(ARG_SUN_POSITION, "–"),
                args.getString(ARG_RECOMMENDATION, "Awaiting route data...")
            )
            applyTransitData(
                args.getString(ARG_ROUTE_INFO, "Searching..."),
                args.getString(ARG_DURATION, "–"),
                args.getString(ARG_LEGS, "–")
            )
        }
    }

    /** Push transit route data (from OTP) into the visible views. */
    fun updateTransitData(routeInfo: String, duration: String, legs: String) {
        val args = arguments ?: Bundle().also { arguments = it }
        args.putString(ARG_ROUTE_INFO, routeInfo)
        args.putString(ARG_DURATION, duration)
        args.putString(ARG_LEGS, legs)
        if (view != null) applyTransitData(routeInfo, duration, legs)
    }

    /** Push sun-side seating data into the visible views. */
    fun updateData(sitSide: String, sunPosition: String, recommendation: String) {
        // Always keep args in sync so data survives re-creation
        val args = arguments ?: Bundle().also { arguments = it }
        args.putString(ARG_SIT_SIDE, sitSide)
        args.putString(ARG_SUN_POSITION, sunPosition)
        args.putString(ARG_RECOMMENDATION, recommendation)

        // If view is already created, update immediately
        if (view != null) {
            applyData(sitSide, sunPosition, recommendation)
        }
    }

    private fun applyData(sitSide: String, sunPosition: String, recommendation: String) {
        tvSitSideBadge?.text   = sitSide
        tvSitLabel?.text       = when (sitSide) {
            "R"   -> "Sit on the Right side"
            "L"   -> "Sit on the Left side"
            "L/R" -> "Either side is fine"
            else  -> "Calculating..."
        }
        tvSunPosition?.text    = sunPosition
        tvRecommendation?.text = recommendation
    }

    private fun applyTransitData(routeInfo: String, duration: String, legs: String) {
        tvRouteInfo?.text = routeInfo
        tvDuration?.text  = duration
        tvLegsInfo?.text  = legs
    }

    companion object {
        private const val ARG_SIT_SIDE       = "sit_side"
        private const val ARG_SUN_POSITION   = "sun_position"
        private const val ARG_RECOMMENDATION = "recommendation"
        private const val ARG_ROUTE_INFO     = "route_info"
        private const val ARG_DURATION       = "duration"
        private const val ARG_LEGS           = "legs"

        fun newInstance(
            sitSide: String = "–",
            sunPosition: String = "–",
            recommendation: String = "Awaiting route data..."
        ): BusInfoBottomSheetFragment {
            return BusInfoBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SIT_SIDE, sitSide)
                    putString(ARG_SUN_POSITION, sunPosition)
                    putString(ARG_RECOMMENDATION, recommendation)
                }
            }
        }
    }
}
