package com.example.myapplication

    import android.content.Context
    import android.content.Intent
    import android.os.Build

    fun Context.startServiceCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }