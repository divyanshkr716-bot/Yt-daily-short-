package com.example.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScheduleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val profileId = intent.getLongExtra("profile_id", -1L)

        // Acquire a temporary partial wake lock to prevent immediate CPU sleep
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "YTAutoShorts:ScheduledWakeLock"
        )
        wakeLock?.acquire(3 * 60 * 1000L) // 3 minutes timeout safety

        val inputData = Data.Builder()
            .putLong("profile_id", profileId)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<AutomationWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .build()

        val workName = if (profileId > 0) "auto_short_work_$profileId" else "auto_short_work_default"
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        // Reschedule next daily run
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ScheduleManager.scheduleAllActiveProfiles(context)
            } finally {
                if (wakeLock?.isHeld == true) {
                    try {
                        wakeLock.release()
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
