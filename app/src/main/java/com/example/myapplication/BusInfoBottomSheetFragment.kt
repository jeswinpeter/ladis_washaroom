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

        // Apply any data that arrived before the view was ready
        arguments?.let { args ->
            applyData(
                args.getString(ARG_SIT_SIDE, "–"),
                args.getString(ARG_SUN_POSITION, "–"),
                args.getString(ARG_RECOMMENDATION, "Awaiting route data...")
            )
        }
    }

    /** Push new data into the visible views (or store in args for later). */
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

    companion object {
        private const val ARG_SIT_SIDE       = "sit_side"
        private const val ARG_SUN_POSITION   = "sun_position"
        private const val ARG_RECOMMENDATION = "recommendation"

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
