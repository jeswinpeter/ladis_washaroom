package com.example.myapplication

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.example.myapplication.R
import com.google.android.material.card.MaterialCardView

class SettingsActivity : AppCompatActivity() {

    lateinit var switchGPSAlarm: SwitchCompat
    lateinit var switchNotifications: SwitchCompat
    lateinit var btnProfile: MaterialCardView
    lateinit var btnLocationSharing: MaterialCardView
    lateinit var btnAppDisplay: MaterialCardView
    lateinit var btnPrivacyPolicy: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        // Connect UI elements to Kotlin code
        switchGPSAlarm = findViewById(R.id.switchGPSAlarm)
        switchNotifications = findViewById(R.id.switchNotifications)


        btnProfile = findViewById(R.id.btnProfile)
        btnLocationSharing = findViewById(R.id.btnLocationSharing)
        btnAppDisplay = findViewById(R.id.btnAppDisplay)
        btnPrivacyPolicy = findViewById(R.id.btnPrivacyPolicy)

        // Set click listeners for buttons (we'll show a Toast for simplicity)
        btnProfile.setOnClickListener {
            Toast.makeText(this, "Your Profile clicked", Toast.LENGTH_SHORT).show()
        }

        btnLocationSharing.setOnClickListener {
            Toast.makeText(this, "Location Sharing clicked", Toast.LENGTH_SHORT).show()
        }

        btnAppDisplay.setOnClickListener {
            Toast.makeText(this, "App & Display clicked", Toast.LENGTH_SHORT).show()
        }

        btnPrivacyPolicy.setOnClickListener {
            Toast.makeText(this, "Privacy Policy clicked", Toast.LENGTH_SHORT).show()
        }

        // Handle switch changes
        switchGPSAlarm.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Toast.makeText(this, "GPS Alarm Enabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "GPS Alarm Disabled", Toast.LENGTH_SHORT).show()
            }
        }

        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Toast.makeText(this, "Notifications Enabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Notifications Disabled", Toast.LENGTH_SHORT).show()
            }
        }
    }
}