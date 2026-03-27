package com.example.myapplication

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.media.RingtoneManager
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

                val uri: android.net.Uri? =
                    result.data?.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)

                if (uri != null) {
                    val prefs = getSharedPreferences("GPS_ALARM", MODE_PRIVATE)
                    prefs.edit().putString("ringtone_uri", uri.toString()).apply()

                    Toast.makeText(this, "Ringtone Selected!", Toast.LENGTH_SHORT).show()
                }
            }
        }

    lateinit var switchGPSAlarm: SwitchCompat
    lateinit var switchNotifications: SwitchCompat
    lateinit var btnProfile: MaterialCardView
    lateinit var btnLocationSharing: MaterialCardView

    private var ringtone: android.media.Ringtone? = null

    private var mediaPlayer: android.media.MediaPlayer? = null
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
                val prefs = getSharedPreferences("GPS_ALARM", MODE_PRIVATE)
                val uriString = prefs.getString("ringtone_uri", null)
                if (uriString != null) {
                    mediaPlayer?.release()
                    mediaPlayer = android.media.MediaPlayer().apply {
                        setDataSource(this@SettingsActivity, Uri.parse(uriString))
                        setAudioStreamType(android.media.AudioManager.STREAM_ALARM)
                        isLooping = true
                        prepare()
                        start()
                    }
                } else {
                    Toast.makeText(this, "Please select a ringtone first!", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "GPS Alarm Disabled", Toast.LENGTH_SHORT).show()
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
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
override fun onDestroy() {
    super.onDestroy()
    mediaPlayer?.stop()
    mediaPlayer?.release()
    mediaPlayer = null
        }
    }