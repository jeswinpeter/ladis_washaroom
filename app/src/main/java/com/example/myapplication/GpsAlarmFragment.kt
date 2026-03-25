package com.example.myapplication

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Button
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class GpsAlarmFragment : BottomSheetDialogFragment() {

    interface RadiusListener {
        fun onRadiusChanged(radiusMeters: Int)
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
            dismiss()
        }

        btnSet.setOnClickListener {
            Toast.makeText(requireContext(), "Alarm Set!", Toast.LENGTH_SHORT).show()
        }

        return view
    }
}