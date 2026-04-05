package com.example.myapplication

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.os.Build

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SNOOZE         = "com.example.myapplication.ALARM_SNOOZE"
        const val ACTION_DISMISS        = "com.example.myapplication.ALARM_DISMISS"
        const val ACTION_SNOOZE_REFIRE  = "com.example.myapplication.ALARM_SNOOZE_REFIRE"
        const val SNOOZE_INTERVAL_MS    = 2 * 60 * 1000L   // 2 minutes
        const val SNOOZE_REQUEST_CODE   = 7001

        /** Schedule a one-shot AlarmManager wake to re-trigger the ring. */
        fun scheduleSnooze(context: Context) {
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_SNOOZE_REFIRE
            }
            val pi = PendingIntent.getBroadcast(
                context, SNOOZE_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerAt = SystemClock.elapsedRealtime() + SNOOZE_INTERVAL_MS

            when {
                // API 31+: check if exact alarms are permitted before using them
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (am.canScheduleExactAlarms()) {
                        am.setExactAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi
                        )
                    } else {
                        // Graceful fallback: fires within ~10 min window but still Doze-safe
                        // Good enough for a snooze alarm — user already knows it's coming
                        am.setAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi
                        )
                    }
                }
                // API 23-30: setExactAndAllowWhileIdle available, no runtime permission needed
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    am.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi
                    )
                }
                // API 23 minimum — set() is fine, Doze didn't exist yet
                else -> {
                    am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                }
            }
        }

        /** Cancel any pending snooze AlarmManager entry. */
        fun cancelSnooze(context: Context) {
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_SNOOZE_REFIRE
            }
            val pi = PendingIntent.getBroadcast(
                context, SNOOZE_REQUEST_CODE, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pi?.let {
                (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(it)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {

            ACTION_SNOOZE -> {
                // Dismiss the ringing notification
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .cancel(GpsAlarmService.RING_NOTIF_ID)
                // Stop the service so ringtone/vibration stops
                context.stopService(Intent(context, GpsAlarmService::class.java))
                // Schedule re-fire in 2 minutes
                scheduleSnooze(context)
            }

            ACTION_DISMISS -> {
                // Cancel any pending snooze re-fire
                cancelSnooze(context)
                // Dismiss the ringing notification
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .cancel(GpsAlarmService.RING_NOTIF_ID)
                // Stop service
                context.stopService(Intent(context, GpsAlarmService::class.java))
                // Clear persisted alarm state so in-app cancel button hides
                AlarmStateStore.clear(context)
            }

            ACTION_SNOOZE_REFIRE -> {
                // AlarmManager woke us — restart the ringing service
                val prefs = AlarmStateStore.load(context)
                if (prefs != null) {
                    val serviceIntent = Intent(context, GpsAlarmService::class.java).apply {
                        action = GpsAlarmService.ACTION_RING   // ring-only, skip GPS check
                        putExtra(GpsAlarmService.EXTRA_LAT,    prefs.first)
                        putExtra(GpsAlarmService.EXTRA_LON,    prefs.second)
                        putExtra(GpsAlarmService.EXTRA_RADIUS, prefs.third)
                    }
                    context.startServiceCompat(serviceIntent)
                }
            }
        }
    }
}