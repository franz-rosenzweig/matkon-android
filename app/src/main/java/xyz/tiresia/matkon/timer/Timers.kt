package xyz.tiresia.matkon.timer

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import xyz.tiresia.matkon.MainActivity
import xyz.tiresia.matkon.R
import xyz.tiresia.matkon.data.Amounts
import xyz.tiresia.matkon.data.Net

data class RunningTimer(
    val id: Int,
    val code: String,
    val title: String,
    val step: Int,
    val minutes: Int,
    val endAt: Long,
)

/**
 * Step timers. Each one is an alarm-clock alarm (it rings on time even when the phone sleeps), with a
 * counting-down notification while it runs and a ringing one when it's done.
 */
object Timers {
    private const val RUNNING = "running"
    private const val DONE = "done"
    const val ACTION_FIRE = "xyz.tiresia.matkon.TIMER_FIRE"
    const val ACTION_STOP = "xyz.tiresia.matkon.TIMER_STOP"

    private lateinit var prefs: SharedPreferences
    val running = MutableStateFlow<List<RunningTimer>>(emptyList())
    val ringing = MutableStateFlow<List<RunningTimer>>(emptyList())

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("timers", Context.MODE_PRIVATE)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(RUNNING, "Running timers", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
        )
        nm.createNotificationChannel(
            NotificationChannel(DONE, "Timer alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
            },
        )
        load()
        // a timer that ended while the app was closed has rung already
        val now = System.currentTimeMillis()
        save(running.value.filter { it.endAt > now })
        // and the ones still running get their alarm back (a restart of the phone clears alarms)
        running.value.forEach {
            arm(ctx, it)
            showRunning(ctx, it)
        }
    }

    private fun load() {
        try {
            val type = object : TypeToken<List<RunningTimer>>() {}.type
            running.value = Net.gson.fromJson<List<RunningTimer>>(prefs.getString("list", "[]"), type).orEmpty()
        } catch (_: Exception) {
            running.value = emptyList()
        }
    }

    private fun save(list: List<RunningTimer>) {
        running.value = list.sortedBy { it.endAt }
        prefs.edit().putString("list", Net.gson.toJson(running.value)).apply()
    }

    private fun broadcast(
        ctx: Context,
        action: String,
        id: Int,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            id * 2 + (if (action == ACTION_STOP) 1 else 0),
            Intent(ctx, TimerReceiver::class.java).setAction(action).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun opener(
        ctx: Context,
        t: RunningTimer,
    ): PendingIntent =
        PendingIntent.getActivity(
            ctx,
            t.id,
            Intent(ctx, MainActivity::class.java).putExtra("cook", t.code).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun start(
        ctx: Context,
        code: String,
        title: String,
        step: Int,
        minutes: Int,
    ) {
        val id = ((prefs.getInt("next", 1)) % 100000).also { prefs.edit().putInt("next", it + 1).apply() }
        val t = RunningTimer(id, code, title, step, minutes, System.currentTimeMillis() + minutes * 60_000L)
        arm(ctx, t)
        save(running.value + t)
        showRunning(ctx, t)
    }

    private fun arm(
        ctx: Context,
        t: RunningTimer,
    ) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val fire = broadcast(ctx, ACTION_FIRE, t.id)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(t.endAt, opener(ctx, t)), fire)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.endAt, fire)
        }
    }

    private fun showRunning(
        ctx: Context,
        t: RunningTimer,
    ) {
        val n =
            Notification
                .Builder(ctx, RUNNING)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle(t.title)
                .setContentText("Step ${t.step} · " + Amounts.minutes(t.minutes))
                .setWhen(t.endAt)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_STOPWATCH)
                .setContentIntent(opener(ctx, t))
                .addAction(Notification.Action.Builder(null, "Stop", broadcast(ctx, ACTION_STOP, t.id)).build())
                .build()
        ctx.getSystemService(NotificationManager::class.java).notify(t.id, n)
    }

    /** The countdowns again (once notifications are allowed, the first ones were dropped). */
    fun repost(ctx: Context) = running.value.forEach { showRunning(ctx, it) }

    /** The alarm went off. */
    fun fire(
        ctx: Context,
        id: Int,
    ) {
        val t = running.value.firstOrNull { it.id == id } ?: run { load(); running.value.firstOrNull { it.id == id } } ?: return
        save(running.value.filter { it.id != id })
        ringing.value = ringing.value + t
        val n =
            Notification
                .Builder(ctx, DONE)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle(t.title)
                .setContentText("Time's up · " + Amounts.minutes(t.minutes))
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setTimeoutAfter(3 * 60_000L)
                .setContentIntent(opener(ctx, t))
                .setDeleteIntent(broadcast(ctx, ACTION_STOP, t.id))
                .addAction(Notification.Action.Builder(null, "Stop", broadcast(ctx, ACTION_STOP, t.id)).build())
                .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT
        ctx.getSystemService(NotificationManager::class.java).notify(t.id, n)
    }

    fun stop(
        ctx: Context,
        id: Int,
    ) {
        ctx.getSystemService(AlarmManager::class.java).cancel(broadcast(ctx, ACTION_FIRE, id))
        ctx.getSystemService(NotificationManager::class.java).cancel(id)
        save(running.value.filter { it.id != id })
        ringing.value = ringing.value.filter { it.id != id }
    }
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val id = intent.getIntExtra("id", -1)
        if (id < 0) return
        when (intent.action) {
            Timers.ACTION_FIRE -> Timers.fire(context, id)
            Timers.ACTION_STOP -> Timers.stop(context, id)
        }
    }
}

/** After the phone restarts: starting the app process is enough, Timers.init puts the alarms back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) = Unit
}
