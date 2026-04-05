package com.example.myapplication

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Button
import android.widget.Toast
import android.content.Intent
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class GpsAlarmFragment : BottomSheetDialogFragment() {

    interface RadiusListener {
        fun onRadiusChanged(radiusMeters: Int)
        fun onCancelAlarm()   // ✅ ADD HERE
    }

    private var listener: RadiusListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)

        if (context is RadiusListener) {
            listener = context
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_gps_alarm, container, false)

        val seekBar = view.findViewById<SeekBar>(R.id.seekRadius)
        val txtRadius = view.findViewById<TextView>(R.id.txtRadius)  // ✅ ADD THIS

        txtRadius.text = "${seekBar.progress} m"


        val btnCancel = view.findViewById<Button>(R.id.btnCancelAlarm)
        val btnSet = view.findViewById<Button>(R.id.btnSetAlarm)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {

                txtRadius.text = "$progress m"

                listener?.onRadiusChanged(progress)

            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnCancel.setOnClickListener {
            listener?.onCancelAlarm()   // ✅ ADD HERE
            dismiss()
        }

            btnSet.setOnClickListener {
                val targetLat = arguments?.getDouble("target_lat") ?: 0.0
                val targetLon = arguments?.getDouble("target_lon") ?: 0.0
                val radius    = seekBar.progress.takeIf { it > 0 } ?: 200

                if (targetLat == 0.0 && targetLon == 0.0) {
                    Toast.makeText(requireContext(), "Set a destination on the map first", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val intent = Intent(requireContext(), GpsAlarmService::class.java).apply {
                    action = GpsAlarmService.ACTION_START
                    putExtra(GpsAlarmService.EXTRA_LAT, targetLat)
                    putExtra(GpsAlarmService.EXTRA_LON, targetLon)
                    putExtra(GpsAlarmService.EXTRA_RADIUS, radius)
                }
                requireContext().startServiceCompat(intent)

                Toast.makeText(requireContext(), "Alarm set — ${radius}m from destination", Toast.LENGTH_SHORT).show()
                dismiss()
        }
        return view
    }
}