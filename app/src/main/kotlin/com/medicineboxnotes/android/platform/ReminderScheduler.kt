package com.medicineboxnotes.android.platform

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.medicineboxnotes.android.MainActivity
import com.medicineboxnotes.android.R
import com.medicineboxnotes.database.MedicineBoxDatabase
import com.medicineboxnotes.database.model
import com.medicineboxnotes.model.MedicalRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ReminderScheduler(private val context: Context) {
    private val alarm = context.getSystemService(AlarmManager::class.java)

    fun update(record: MedicalRecord) {
        cancel(record.id)
        val followUp = record.followUpDateEpochDay?.let(LocalDate::ofEpochDay) ?: return
        schedule(record, followUp.minusDays(1), false)
        schedule(record, followUp, true)
    }

    fun cancel(recordId: String) {
        listOf(false, true).forEach { sameDay -> alarm.cancel(pending(recordId, sameDay, null)) }
    }

    suspend fun rescheduleAll(db: MedicineBoxDatabase) {
        db.medicalRecords().getAll().map { it.model() }.forEach(::update)
    }

    private fun schedule(record: MedicalRecord, day: LocalDate, sameDay: Boolean) {
        val at = day.atTime(LocalTime.of(9, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (at <= System.currentTimeMillis()) return
        val intent = Intent(context, ReminderReceiver::class.java).putExtra("recordId", record.id)
            .putExtra("title", if (sameDay) "今天需要复诊" else "明天需要复诊")
            .putExtra("hospital", record.hospitalName)
        val operation = pending(record.id, sameDay, intent)
        if (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
    }

    private fun pending(recordId: String, sameDay: Boolean, intent: Intent?): PendingIntent = PendingIntent.getBroadcast(
        context, recordId.hashCode() * 2 + if (sameDay) 1 else 0,
        intent ?: Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val recordId = intent.getStringExtra("recordId") ?: return
        val channelId = "follow_up"
        if (Build.VERSION.SDK_INT >= 26) context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channelId, "复诊提醒", NotificationManager.IMPORTANCE_DEFAULT),
        )
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val deepLink = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("medicinebox://record?id=$recordId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val content = PendingIntent.getActivity(context, recordId.hashCode(), deepLink, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(intent.getStringExtra("title") ?: "复诊提醒")
            .setContentText(intent.getStringExtra("hospital").orEmpty().ifBlank { "点击查看病历详情" })
            .setContentIntent(content).setAutoCancel(true).build()
        NotificationManagerCompat.from(context).notify(recordId.hashCode(), notification)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { ReminderScheduler(context).rescheduleAll(MedicineBoxDatabase.get(context)) } finally { pending.finish() }
        }
    }
}
