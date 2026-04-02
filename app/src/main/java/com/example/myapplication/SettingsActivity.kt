package com.example.myapplication

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.net.Uri
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import com.example.myapplication.R
import com.google.android.material.card.MaterialCardView
import android.content.Intent

class SettingsActivity : AppCompatActivity() {

    private val ringtoneLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uri: Uri? =
                    result.data?.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                if (uri != null) {
                    getSharedPreferences("GPS_ALARM", MODE_PRIVATE)
                        .edit().putString("ringtone_uri", uri.toString()).apply()
                    Toast.makeText(this, "Ringtone Selected!", Toast.LENGTH_SHORT).show()
                }
            }
        }

    lateinit var switchGPSAlarm: SwitchCompat
    lateinit var switchNotifications: SwitchCompat
    lateinit var btnProfile: MaterialCardView
    lateinit var btnLocationSharing: MaterialCardView
    lateinit var btnAppDisplay: MaterialCardView
    lateinit var btnPrivacyPolicy: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        val btnRingtone = findViewById<Button>(R.id.btnSelectRingtone)
        btnRingtone.setOnClickListener {
            val intent = Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER)
            ringtoneLauncher.launch(intent)
        }

        switchGPSAlarm = findViewById(R.id.switchGPSAlarm)
        switchNotifications = findViewById(R.id.switchNotifications)
        btnProfile = findViewById(R.id.btnProfile)
        btnLocationSharing = findViewById(R.id.btnLocationSharing)
        btnAppDisplay = findViewById(R.id.btnAppDisplay)
        btnPrivacyPolicy = findViewById(R.id.btnPrivacyPolicy)

        // ── Restore saved switch state so UI reflects reality on reopen ──
        val prefs = getSharedPreferences("GPS_ALARM", MODE_PRIVATE)
        switchGPSAlarm.isChecked = prefs.getBoolean("alarm_enabled", false)

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

        // ── GPS Alarm: only saves state, never plays audio here ──
        switchGPSAlarm.setOnCheckedChangeListener { _, isChecked ->
            getSharedPreferences("GPS_ALARM", MODE_PRIVATE)
                .edit().putBoolean("alarm_enabled", isChecked).apply()
            val msg = if (isChecked) "GPS Alarm Enabled" else "GPS Alarm Disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            val msg = if (isChecked) "Notifications Enabled" else "Notifications Disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // ── onDestroy cleanup removed: no mediaPlayer exists here anymore ──
}