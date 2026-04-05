package com.example.myapplication

import android.content.Context

object AlarmStateStore {

    private const val PREFS_NAME   = "navez_gps_alarm"
    private const val KEY_ACTIVE   = "active"
    private const val KEY_LAT      = "lat"
    private const val KEY_LON      = "lon"
    private const val KEY_RADIUS   = "radius"

    /** Save alarm params. Call when service starts tracking. */
    fun save(context: Context, lat: Double, lon: Double, radius: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putFloat(KEY_LAT,    lat.toFloat())
            .putFloat(KEY_LON,    lon.toFloat())
            .putInt(KEY_RADIUS,   radius)
            .apply()
    }

    /** Returns Triple(lat, lon, radius) if alarm is active, else null. */
    fun load(context: Context): Triple<Double, Double, Int>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return null
        return Triple(
            prefs.getFloat(KEY_LAT, 0f).toDouble(),
            prefs.getFloat(KEY_LON, 0f).toDouble(),
            prefs.getInt(KEY_RADIUS, 200)
        )
    }

    /** Clear when dismissed or cancelled. */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .clear()
            .apply()
    }

    fun isActive(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ACTIVE, false)
}