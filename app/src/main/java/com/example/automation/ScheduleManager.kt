package com.example.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import com.example.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

object ScheduleManager {
    private const val ALARM_REQUEST_CODE_BASE = 9000

    /**
     * Calculate next trigger time for given daily times (e.g. "09:00,13:00,19:00")
     */
    fun getNextTriggerMillis(schedulesCsv: String): Long? {
        val times = parseSchedules(schedulesCsv)
        if (times.isEmpty()) return null

        val now = Calendar.getInstance()
        var earliestFutureTime: Calendar? = null

        // Check for remaining times today
        for ((hour, minute) in times) {
            val candidate = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (candidate.after(now)) {
                if (earliestFutureTime == null || candidate.before(earliestFutureTime)) {
                    earliestFutureTime = candidate
                }
            }
        }

        // If all today's times have passed, take earliest time tomorrow
        if (earliestFutureTime == null) {
            val firstTime = times.minByOrNull { it.first * 60 + it.second } ?: return null
            earliestFutureTime = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, firstTime.first)
                set(Calendar.MINUTE, firstTime.second)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }

        return earliestFutureTime.timeInMillis
    }

    fun parseSchedules(csv: String): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        for (part in csv.split(",")) {
            val trimmed = part.trim()
            if (trimmed.contains(":")) {
                val pieces = trimmed.split(":")
                val h = pieces.getOrNull(0)?.toIntOrNull()
                val m = pieces.getOrNull(1)?.toIntOrNull()
                if (h != null && m != null && h in 0..23 && m in 0..59) {
                    result.add(h to m)
                }
            }
        }
        return result
    }

    suspend fun scheduleAllActiveProfiles(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val automatedProfiles = db.profileDao().getAutomatedProfiles()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        for (profile in automatedProfiles) {
            val nextMillis = getNextTriggerMillis(profile.dailySchedulesCsv) ?: continue
            val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                putExtra("profile_id", profile.id)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                (ALARM_REQUEST_CODE_BASE + profile.id).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            scheduleAlarmSafely(alarmManager, nextMillis, pendingIntent)
        }
    }

    fun cancelSchedule(context: Context, profileId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ScheduleAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (ALARM_REQUEST_CODE_BASE + profileId).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun scheduleAlarmSafely(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    // Graceful fallback to inexact alarm as mandated in requirement 18
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // Fallback for security restriction
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
}
