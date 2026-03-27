package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class RouteInfoBottomSheetFragment : BottomSheetDialogFragment() {

    private var rootView: View? = null
    private var currentMode = Mode.DRIVING

    private var pendingSteps: List<MainActivity.RouteStep>? = null
    private var pendingDrivingDuration: Int = 0
    private var pendingDrivingDistance: String = "–"
    private var pendingTransitRoute: String? = null
    private var pendingTransitDuration: String? = null
    private var pendingTransitLegs: String? = null

    enum class Mode { DRIVING, TRANSIT }

    companion object {
        fun newInstance() = RouteInfoBottomSheetFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_route_info, container, false)
        rootView = view
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.btnDriving).setOnClickListener { switchMode(Mode.DRIVING) }
        view.findViewById<Button>(R.id.btnTransit).setOnClickListener { switchMode(Mode.TRANSIT) }

        pendingSteps?.let { renderDrivingView(it, pendingDrivingDuration, pendingDrivingDistance) }
        pendingTransitRoute?.let {
            renderTransitView(it, pendingTransitDuration ?: "–", pendingTransitLegs ?: "–")
        }
        switchMode(currentMode)
    }

    fun setDrivingData(steps: List<MainActivity.RouteStep>, durationMin: Int, distanceKm: String) {
        pendingSteps = steps
        pendingDrivingDuration = durationMin
        pendingDrivingDistance = distanceKm
        rootView?.let { renderDrivingView(steps, durationMin, distanceKm) }
    }

    fun updateTransitData(route: String, duration: String, legs: String) {
        pendingTransitRoute = route
        pendingTransitDuration = duration
        pendingTransitLegs = legs
        rootView?.let { renderTransitView(route, duration, legs) }
    }

    fun updateSunSide(side: String, sunPosition: String, recommendation: String) {
        val v = rootView ?: return
        v.findViewById<TextView>(R.id.tvSitSideBadge).text = side
        v.findViewById<TextView>(R.id.tvSitLabel).text = "Sit on the ${if (side == "L") "Left" else "Right"} side"
        v.findViewById<TextView>(R.id.tvSunPosition).text = sunPosition
        v.findViewById<TextView>(R.id.tvRecommendation).text = recommendation
    }

    private fun renderDrivingView(steps: List<MainActivity.RouteStep>, durationMin: Int, distanceKm: String) {
        val v = rootView ?: return
        v.findViewById<TextView>(R.id.tvRouteNameDriving).text = steps
            .firstOrNull { it.type == "turn" || it.type == "merge" }
            ?.streetName?.let { "via $it" } ?: "Driving route"
        v.findViewById<TextView>(R.id.tvDurationDriving).text = "$durationMin min · $distanceKm km"

        val container = v.findViewById<LinearLayout>(R.id.stepsContainer)
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        for (step in steps) {
            if (step.distanceMeters == 0 && step.type != "arrive") continue
            val row = inflater.inflate(R.layout.item_route_step, container, false)
            row.findViewById<TextView>(R.id.tvStepIcon).text = step.icon
            row.findViewById<TextView>(R.id.tvStepName).text = step.label
            row.findViewById<TextView>(R.id.tvStepDist).text = step.distanceLabel
            container.addView(row)
        }
    }

    private fun renderTransitView(route: String, duration: String, legsRaw: String) {
        val v = rootView ?: return
        v.findViewById<TextView>(R.id.tvRouteNameTransit).text = route
        v.findViewById<TextView>(R.id.tvDurationTransit).text = duration

        val container = v.findViewById<LinearLayout>(R.id.legsContainer)
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        val legs = legsRaw.split("  →  ")
        legs.forEachIndexed { index, leg ->
            if (index > 0) {
                val divider = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(4.dpToPx(), 14.dpToPx()).also {
                        it.marginStart = 20.dpToPx()
                        it.topMargin = 2.dpToPx()
                        it.bottomMargin = 2.dpToPx()
                    }
                    setBackgroundColor(
                        requireContext().getColor(android.R.color.darker_gray)
                    )
                }
                container.addView(divider)
            }
            val card = inflater.inflate(R.layout.item_transit_leg, container, false)
            card.findViewById<TextView>(R.id.tvLegText).text = leg.trim()
            container.addView(card)
        }
    }

    private fun switchMode(mode: Mode) {
        currentMode = mode
        val v = rootView ?: return
        val isDriving = mode == Mode.DRIVING
        v.findViewById<Button>(R.id.btnDriving).isSelected = isDriving
        v.findViewById<Button>(R.id.btnTransit).isSelected = !isDriving
        v.findViewById<View>(R.id.drivingView).visibility = if (isDriving) View.VISIBLE else View.GONE
        v.findViewById<View>(R.id.transitView).visibility = if (isDriving) View.GONE else View.VISIBLE
        (activity as? MainActivity)?.onMapModeChanged(mode)
    }

    private fun Int.dpToPx() = (this * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        rootView = null
    }
}