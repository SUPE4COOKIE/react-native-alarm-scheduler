package com.rnalarmmodule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Build
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private const val PREFS = "rn_alarm_module_alarms"
        private const val DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                Log.d(TAG, "Rescheduling alarms after: ${intent.action}")
                rescheduleAlarms(context)
            }
        }
    }

    private fun rescheduleAlarms(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val all = prefs.all
        if (all.isEmpty()) {
            Log.d(TAG, "No alarms to reschedule.")
            return
        }

        val sdf = SimpleDateFormat(DATE_PATTERN, Locale.getDefault())
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()

        var rescheduledCount = 0
        var skippedCount = 0

        for ((id, rawJson) in all) {
            val jsonStr = rawJson as? String ?: continue
            val obj = try {
                JSONObject(jsonStr)
            } catch (_: Exception) {
                continue
            }
            val datetimeISO = obj.optString("datetimeISO", "")
            val title = obj.optString("title", "Alarm")
            val body = obj.optString("body", "")

            val date = try {
                sdf.parse(datetimeISO)
            } catch (_: Exception) {
                null
            } ?: continue
            
            val triggerAt = date.time
            if (triggerAt <= now) {
                // Past alarms: skip (could optionally fire immediately if desired)
                skippedCount++
                continue
            }

            val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("id", id)
                putExtra("title", title)
                putExtra("body", body)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                id.hashCode(),
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }

            rescheduledCount++
            Log.d(TAG, "Rescheduled alarm id=$id at=$datetimeISO")
        }

        Log.d(TAG, "Rescheduling complete: $rescheduledCount rescheduled, $skippedCount skipped (past)")
    }
}