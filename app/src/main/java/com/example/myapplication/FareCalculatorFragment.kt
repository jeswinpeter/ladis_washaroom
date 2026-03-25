package com.example.myapplication

import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import java.util.Calendar

class FareCalculatorFragment : Fragment() {

    private data class FareResult(
        val total: Double,
        val minFare: Double,
        val distanceFare: Double,
        val waitingCharge: Int,
        val nightCharge: Double,
        val onewayCharge: Double,
        val isNight: Boolean,
        val distanceKm: Double,
        val waitingMinutes: Int,
        val isOneway: Boolean
    )

    private fun calculate(distanceKm: Double, isOneway: Boolean, waitingMinutes: Int, hourOfDay: Int): FareResult {
        val baseMinKm = 1.5; val minFare = 30.0; val perKmRate = 15.0
        val nightMul = 1.5; val onewayMul = 0.5

        val distanceFare = if (distanceKm > baseMinKm) (distanceKm - baseMinKm) * perKmRate else 0.0
        var fare = minFare + distanceFare
        val waitingCharge = waitingMinutes * 2
        fare += waitingCharge
        val isNight = hourOfDay >= 22 || hourOfDay < 5
        val nightCharge = if (isNight) fare * (nightMul - 1) else 0.0
        if (isNight) fare *= nightMul
        val onewayCharge = if (isOneway) (fare - minFare) * onewayMul else 0.0
        fare += onewayCharge
        val rounded = kotlin.math.round(fare * 100) / 100.0

        return FareResult(rounded, minFare, distanceFare, waitingCharge, nightCharge, onewayCharge, isNight, distanceKm, waitingMinutes, isOneway)
    }

    private fun buildBreakdown(r: FareResult): CharSequence {
        return buildString {
            append("Minimum charge (first 1.5 km)       ₹${"%.2f".format(r.minFare)}\n")
            if (r.distanceKm > 1.5) {
                append("Distance (${String.format("%.1f", r.distanceKm)}−1.5) × ₹15/km     ₹${"%.2f".format(r.distanceFare)}\n")
            }
            if (r.waitingMinutes > 0) {
                append("Waiting ${r.waitingMinutes} min × ₹2/min            ₹${r.waitingCharge}\n")
            }
            if (r.isNight) {
                append("Night surcharge (50%, 10PM–5AM)     ₹${"%.2f".format(r.nightCharge)}\n")
            }
            if (r.isOneway) {
                append("One-way extra (50% of fare−min)     ₹${"%.2f".format(r.onewayCharge)}\n")
            }
        }
    }

    // Helper to create a rounded-rect drawable
    private fun roundedRect(fillColor: Int, radiusDp: Float, strokeColor: Int = 0, strokeWidthDp: Float = 0f): GradientDrawable {
        val d = resources.displayMetrics.density
        return GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = radiusDp * d
            if (strokeColor != 0) setStroke((strokeWidthDp * d).toInt(), strokeColor)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): android.view.View {
        val ctx = requireContext()
        val d = resources.displayMetrics.density
        val pad = (16 * d).toInt()
        val smallPad = (10 * d).toInt()
        val tinyPad = (6 * d).toInt()
        val accentBlue = Color.parseColor("#007AFF")
        val lightGrey = Color.parseColor("#F2F2F7")
        val medGrey = Color.parseColor("#C7C7CC")
        val darkText = Color.parseColor("#1C1C1E")

        val scroll = ScrollView(ctx)
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.WHITE)
        }

        // ── Close button (top-right) ──
        val closeRow = FrameLayout(ctx)
        val btnClose = ImageButton(ctx).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.TRANSPARENT)
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.TOP
            )
            layoutParams = lp
        }
        btnClose.setOnClickListener {
            val sheet = view?.parent?.parent
            if (sheet is android.view.View) {
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
        }
        closeRow.addView(btnClose)
        layout.addView(closeRow)

        // ── Title ──
        val title = TextView(ctx).apply {
            text = "Fare Calculator"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(darkText)
            setPadding(0, 0, 0, tinyPad)
        }
        layout.addView(title)

        // ── Accent divider ──
        val divider = android.view.View(ctx).apply {
            setBackgroundColor(accentBlue)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (2.5f * d).toInt()).apply {
                bottomMargin = smallPad
            }
        }
        layout.addView(divider)

        // ── Styled EditText helper ──
        fun styledEdit(hintText: String, inputFlags: Int): EditText {
            return EditText(ctx).apply {
                hint = hintText
                inputType = inputFlags
                background = roundedRect(lightGrey, 10f, medGrey, 1f)
                setPadding(smallPad, smallPad, smallPad, smallPad)
                textSize = 16f
                setTextColor(darkText)
                setHintTextColor(medGrey)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = smallPad }
            }
        }

        val etDistance = styledEdit("Distance (km)", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val etWaiting = styledEdit("Waiting minutes", InputType.TYPE_CLASS_NUMBER)

        val cbOneway = CheckBox(ctx).apply {
            text = "  One-way trip"
            textSize = 15f
            setTextColor(darkText)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = tinyPad; bottomMargin = 0 }
        }

        val onewayHint = TextView(ctx).apply {
            text = "Driver returns empty — 50% surcharge on fare above minimum"
            textSize = 12f
            setTextColor(medGrey)
            setPadding((28 * d).toInt(), 0, 0, 0) // indent to align with checkbox text
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = smallPad }
        }

        // ── Calculate button ──
        val btn = Button(ctx).apply {
            text = "Calculate Fare"
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            background = roundedRect(accentBlue, 12f)
            setPadding(0, smallPad, 0, smallPad)
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = tinyPad; bottomMargin = smallPad }
        }

        // ── Result card (hidden until calculated) ──
        val resultCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedRect(lightGrey, 12f)
            setPadding(smallPad, smallPad, smallPad, smallPad)
            visibility = android.view.View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = smallPad }
        }
        val resultHeader = TextView(ctx).apply {
            text = "Breakdown"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(darkText)
            setPadding(0, 0, 0, tinyPad)
        }
        val resultBody = TextView(ctx).apply {
            textSize = 13.5f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#3A3A3C"))
            setLineSpacing(4 * d, 1f)
        }
        val totalDivider = android.view.View(ctx).apply {
            setBackgroundColor(medGrey)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * d).toInt()).apply {
                topMargin = tinyPad; bottomMargin = tinyPad
            }
        }
        val totalText = TextView(ctx).apply {
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentBlue)
        }
        resultCard.addView(resultHeader)
        resultCard.addView(resultBody)
        resultCard.addView(totalDivider)
        resultCard.addView(totalText)

        // ── Calculate click ──
        btn.setOnClickListener {
            val dist = etDistance.text.toString().toDoubleOrNull() ?: 0.0
            val wait = etWaiting.text.toString().toIntOrNull() ?: 0
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val result = calculate(dist, cbOneway.isChecked, wait, hour)
            resultBody.text = buildBreakdown(result)
            totalText.text = "Total:  ₹${"%.2f".format(result.total)}"
            resultCard.visibility = android.view.View.VISIBLE
            (ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(btn.windowToken, 0)
        }

        listOf(etDistance, etWaiting, cbOneway, onewayHint, btn, resultCard).forEach { layout.addView(it) }
        scroll.addView(layout)
        return scroll
    }
}
