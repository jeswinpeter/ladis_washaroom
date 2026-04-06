package com.example.myapplication

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import kotlin.math.*

class GpsAlarmService : Service() {

    companion object {
        const val ACTION_START = "com.example.myapplication.GPS_ALARM_START"
        const val ACTION_RING  = "com.example.myapplication.GPS_ALARM_RING"
        const val ACTION_STOP  = "com.example.myapplication.GPS_ALARM_STOP"
        const val EXTRA_LAT    = "target_lat"
        const val EXTRA_LON    = "target_lon"
        const val EXTRA_RADIUS = "radius_meters"

        const val TRACKING_CHANNEL_ID = "gps_alarm_tracking"
        const val RING_CHANNEL_ID     = "gps_alarm_ring"
        const val TRACKING_NOTIF_ID   = 1001
        const val RING_NOTIF_ID       = 1002
    }

    private lateinit var fusedClient: FusedLocationProviderClient
    private var targetLat    = 0.0
    private var targetLon    = 0.0
    private var radiusMeters = 200
    private var ringtone: android.media.Ringtone? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val dist = haversineMeters(loc.latitude, loc.longitude, targetLat, targetLon)
            Log.d("GpsAlarmService", "Distance: ${dist.toInt()}m / alarm at ${radiusMeters}m")
            if (dist <= radiusMeters) {
                ring()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {

            ACTION_STOP -> {
                AlarmStateStore.clear(this)
                AlarmReceiver.cancelSnooze(this)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_RING -> {
                // Snooze re-fire: just ring, don't restart GPS tracking
                targetLat    = intent.getDoubleExtra(EXTRA_LAT, 0.0)
                targetLon    = intent.getDoubleExtra(EXTRA_LON, 0.0)
                radiusMeters = intent.getIntExtra(EXTRA_RADIUS, 200)
                startForegroundWithTracking()   // need foreground to play sound
                ring()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                targetLat    = intent.getDoubleExtra(EXTRA_LAT, 0.0)
                targetLon    = intent.getDoubleExtra(EXTRA_LON, 0.0)
                radiusMeters = intent.getIntExtra(EXTRA_RADIUS, 200)
                AlarmStateStore.save(this, targetLat, targetLon, radiusMeters)
                startForegroundWithTracking()
                startLocationUpdates()
                return START_STICKY
            }

            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    // ── Foreground notification (silent, persistent — shown while tracking) ──

    private fun startForegroundWithTracking() {
        val cancelIntent = Intent(this, GpsAlarmService::class.java).apply {
            action = ACTION_STOP
        }
        val cancelPi = PendingIntent.getService(
            this, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, TRACKING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("GPS Alarm Active")
            .setContentText("Will alert within ${radiusMeters}m of your destination")
            .setPriority(NotificationCompat.PRIORITY_LOW)   // silent while tracking
            .setOngoing(true)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel Alarm", cancelPi)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(TRACKING_NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(TRACKING_NOTIF_ID, notification)
        }
    }

    // ── Ring: plays sound + vibration + shows actionable notification ──

    private fun ring() {
        // Play alarm sound
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(this, uri).also { it.play() }
        } catch (e: Exception) {
            Log.e("GpsAlarmService", "Ringtone failed", e)
        }

        // Vibrate
        val pattern = longArrayOf(0, 600, 400, 600, 400, 600)
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }

        // Stop GPS tracking (we've arrived — no need to keep tracking)
        fusedClient.removeLocationUpdates(locationCallback)

        // Post the actionable ring notification
        showRingNotification()
    }

    private fun showRingNotification() {
        val snoozePi = PendingIntent.getBroadcast(
            this, 101,
            Intent(this, AlarmReceiver::class.java).apply { action = AlarmReceiver.ACTION_SNOOZE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissPi = PendingIntent.getBroadcast(
            this, 102,
            Intent(this, AlarmReceiver::class.java).apply { action = AlarmReceiver.ACTION_DISMISS },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, RING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("🔔 GPS Alarm — You're almost there!")
            .setContentText("You are within ${radiusMeters}m of your destination")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)         // can't swipe away — must use button
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_popup_reminder, "Snooze 2 min", snoozePi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPi)
            .setFullScreenIntent(openPi, true)   // shows as heads-up / lock screen
            .build()

        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(RING_NOTIF_ID, notification)
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 15_000L)
            .setMinUpdateDistanceMeters(30f)
            .build()
        try {
            fusedClient.requestLocationUpdates(request, locationCallback, mainLooper)
        } catch (e: SecurityException) {
            Log.e("GpsAlarmService", "Location permission denied", e)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedClient.removeLocationUpdates(locationCallback)
        ringtone?.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Silent tracking channel
        nm.createNotificationChannel(NotificationChannel(
            TRACKING_CHANNEL_ID,
            "GPS Alarm (Tracking)",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Silent persistent notification while GPS alarm is active"
            setSound(null, null)
            enableVibration(false)
        })

        // Ring channel — full volume, bypasses DND if possible
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        nm.createNotificationChannel(NotificationChannel(
            RING_CHANNEL_ID,
            "GPS Alarm (Alert)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alarm when you approach your destination"
            enableVibration(true)
            setBypassDnd(true)
        })
    }
}