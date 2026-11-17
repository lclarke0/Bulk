package com.bulkplan.tracker.work

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bulkplan.tracker.MainActivity
import com.bulkplan.tracker.R
import com.bulkplan.tracker.data.Repo
import com.bulkplan.tracker.data.ScheduleItem
import com.bulkplan.tracker.logic.Actions
import com.bulkplan.tracker.logic.PlanLogic
import com.bulkplan.tracker.net.Garmin
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object Reminders {
    const val CHANNEL = "schedule"
    private const val NUDGE = "weigh-nudge"
    private const val NUDGE_TIME = "09:00"

    fun createChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Schedule reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Meals, training, skin care, supplements and sleep from your plan"
        })
    }

    private fun alarmIntent(ctx: Context, date: String, time: String): PendingIntent =
        PendingIntent.getBroadcast(ctx, 7001,
            Intent(ctx, AlarmReceiver::class.java).putExtra("date", date).putExtra("time", time),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun canExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    /** Sets one alarm for the next upcoming item; each alarm schedules the one after it. */
    fun schedule(ctx: Context) {
        Repo.init(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val s = Repo.state.value
        if (!s.settings.alerts) { am.cancel(alarmIntent(ctx, "", "")); return }
        val now = LocalDateTime.now()
        val today = PlanLogic.today()
        var best: Pair<LocalDateTime, Pair<String, String>>? = null
        for (i in 0..7L) {
            val d = PlanLogic.addD(today, i)
            val times = PlanLogic.itemsFor(s.plan, d).map { it.time }.toMutableSet()
            if (s.settings.weighNudge) times += NUDGE_TIME
            for (t in times) {
                val m = PlanLogic.mins(t)
                val at = PlanLogic.date(d).atTime(m / 60, m % 60)
                if (at.isAfter(now) && (best == null || at.isBefore(best.first))) best = at to (d to t)
            }
            if (best != null) break
        }
        val b = best ?: return
        val ms = b.first.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = alarmIntent(ctx, b.second.first, b.second.second)
        if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
    }

    fun notify(ctx: Context, date: String, item: ScheduleItem) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val id = (date + item.id).hashCode()
        val open = PendingIntent.getActivity(ctx, id,
            Intent(ctx, MainActivity::class.java).putExtra("tab", if (item.kind == "train") "workout" else if (item.kind == "coach") "coach" else "today")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_bulk)
            .setContentTitle("${PlanLogic.fmtT(item.time)} · ${item.title}")
            .setContentText(item.detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.detail + (item.kcal?.let { " · ${item.protein ?: 0} g protein · $it kcal" } ?: "")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        if (item.id != NUDGE) {
            val done = PendingIntent.getBroadcast(ctx, id,
                Intent(ctx, DoneReceiver::class.java).putExtra("date", date).putExtra("item", item.id).putExtra("nid", id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            n.addAction(0, if (item.kind == "meal") "Ate it" else "Done", done)
        }
        try { NotificationManagerCompat.from(ctx).notify(id, n.build()) } catch (_: SecurityException) {}
    }

    fun fire(ctx: Context, date: String, time: String) {
        Repo.init(ctx)
        val s = Repo.state.value
        val log = Repo.log(date)
        if (s.settings.alerts) {
            PlanLogic.itemsFor(s.plan, date).filter { it.time == time && log.checks[it.id] != true }.forEach { notify(ctx, date, it) }
            if (time == NUDGE_TIME && s.settings.weighNudge && PlanLogic.weightOf(log) == null && date == PlanLogic.today())
                notify(ctx, date, ScheduleItem(NUDGE, NUDGE_TIME, "task", "Log your morning weight", "Open Bulk Plan and enter today's weigh-in. It drives your calorie calls."))
        }
        schedule(ctx)
    }

    fun ensureGarminSync(ctx: Context) {
        if (Repo.state.value.settings.garminKey.isBlank()) { runCatching { WorkManager.getInstance(ctx).cancelUniqueWork("garmin") }; return }
        val wm = WorkManager.getInstance(ctx)
        val req = PeriodicWorkRequestBuilder<GarminWorker>(3, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork("garmin", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.fire(context, intent.getStringExtra("date") ?: return, intent.getStringExtra("time") ?: return)
    }
}

class DoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Repo.init(context)
        val date = intent.getStringExtra("date") ?: return
        val id = intent.getStringExtra("item") ?: return
        PlanLogic.itemsFor(Repo.state.value.plan, date).firstOrNull { it.id == id }?.let { Actions.setChecked(date, it, true) }
        Repo.writeNow()
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra("nid", 0))
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.schedule(context)
        Reminders.ensureGarminSync(context)
    }
}

class GarminWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Repo.init(applicationContext)
        return try {
            Garmin.sync(applicationContext)
            Repo.writeNow()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
