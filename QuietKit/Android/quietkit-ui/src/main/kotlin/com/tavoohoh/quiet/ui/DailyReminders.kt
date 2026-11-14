package com.tavoohoh.quiet.ui

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
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.tavoohoh.quiet.core.DailyReminderPlan
import java.time.Instant
import java.time.ZoneId

/**
 * Local notifications for the daily challenge (no server, no push; port of QuietUI's
 * `DailyReminders`): the 19:00 reminder and the 21:00 "streak at risk", planned by
 * `DailyReminderPlan`. Each app gives the texts and tells it where the day stands; the kit
 * schedules, cancels and rewrites.
 *
 * Alarms are inexact (`setWindow` of 10 minutes, no exact-alarm permission): the system may
 * deliver them a few minutes late, or later in Doze. The app calls `reschedule()` on launch, when it comes to
 * the front, after a daily is completed, and from a receiver for boot and clock changes (alarms
 * don't survive a reboot). Friends (Play Games) come in a later phase.
 *
 * Permission (Android 13+): asked only from the Settings switch or the offer after a first
 * completed daily, like iOS; the app launches the request and reports `onPermissionResult`.
 */
class DailyReminders(
    context: Context,
    /** Small notification icon (a white glyph). */
    private val smallIcon: Int,
    private val prefsName: String = "dailyReminders",
) {
    enum class Authorization { NOT_DETERMINED, ALLOWED, DENIED }

    data class Content(val title: String, val body: String)

    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    /** Text for a planned item, from the app's strings. */
    var texts: (DailyReminderPlan.Item) -> Content = { Content("", "") }
    /** Where the day stands, asked each time the plan is made: today completed, current streak. */
    var state: () -> Pair<Boolean, Int> = { false to 0 }
    /** The app's clock (a fixed date in debug launches). */
    var clock: () -> Instant = { Instant.now() }
    /** The channel's name in the app's language. */
    var channelName: String = "Daily challenge"

    var authorization by mutableStateOf(readAuthorization())
        private set
    /** The Settings switch. Off: nothing is scheduled. */
    var isEnabled by mutableStateOf(prefs.getBoolean(ENABLED, true))
        private set
    /** The in-context offer was already shown (whatever the answer). */
    var wasOffered by mutableStateOf(prefs.getBoolean(OFFERED, false))
        private set

    /** Notifications are allowed and the switch is on. */
    val isActive: Boolean get() = isEnabled && authorization == Authorization.ALLOWED

    /** Worth offering after a completed daily: never offered, and the system hasn't asked yet. */
    val shouldOffer: Boolean get() = !wasOffered && authorization == Authorization.NOT_DETERMINED

    /** Android 13+ needs the runtime permission; older versions only the app's notification setting. */
    val needsPermissionRequest: Boolean
        get() = Build.VERSION.SDK_INT >= 33 && authorization == Authorization.NOT_DETERMINED

    fun refreshAuthorization() {
        authorization = readAuthorization()
    }

    private fun readAuthorization(): Authorization {
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (Build.VERSION.SDK_INT < 33) return if (enabled) Authorization.ALLOWED else Authorization.DENIED
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return when {
            granted && enabled -> Authorization.ALLOWED
            granted || prefs.getBoolean(ASKED, false) -> Authorization.DENIED
            else -> Authorization.NOT_DETERMINED
        }
    }

    /** The system prompt was answered. Turns the switch on if granted. */
    fun onPermissionResult(granted: Boolean) {
        markOffered()
        prefs.edit { putBoolean(ASKED, true) }
        if (granted) setEnabledFlag(true)
        refreshAuthorization()
        reschedule()
    }

    /** "Not now" on the offer: it won't be shown again (Settings still has the switch). */
    fun markOffered() {
        wasOffered = true
        prefs.edit { putBoolean(OFFERED, true) }
    }

    /** The Settings switch. Returns true when the app must ask for the permission now. */
    fun setEnabled(enabled: Boolean): Boolean {
        setEnabledFlag(enabled)
        refreshAuthorization()
        if (enabled && needsPermissionRequest) return true
        reschedule()
        return false
    }

    private fun setEnabledFlag(enabled: Boolean) {
        isEnabled = enabled
        prefs.edit { putBoolean(ENABLED, enabled) }
    }

    /** The app's notification settings (when they're off for the app). */
    fun settingsIntent(): Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // Scheduling

    /** Replaces every pending daily notification with a fresh plan (or none). */
    fun reschedule(now: Instant = clock(), zone: ZoneId = ZoneId.systemDefault()) {
        refreshAuthorization()
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        for (id in prefs.getStringSet(SCHEDULED, emptySet()).orEmpty()) {
            alarms.cancel(pendingIntent(id, null))
        }
        val items = if (isActive) {
            val (todayCompleted, streak) = state()
            DailyReminderPlan.items(now, todayCompleted, streak, zone)
        } else {
            emptyList()
        }
        if (items.isNotEmpty()) ensureChannel()
        for (item in items) {
            val at = item.time.atZone(zone).toInstant().toEpochMilli()
            // Inexact, within 10 minutes after the hour (the shortest window the system allows
            // without the exact-alarm permission); a plain inexact alarm can slip up to an hour.
            alarms.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MILLIS, pendingIntent(item.identifier, texts(item)))
        }
        prefs.edit { putStringSet(SCHEDULED, items.map { it.identifier }.toSet()) }
    }

    /** The daily notifications waiting, for QA: "id · body". */
    fun pendingDescriptions(): List<String> = prefs.getStringSet(SCHEDULED, emptySet()).orEmpty().sorted()

    private fun ensureChannel() = ensureChannel(context, channelName)

    private fun pendingIntent(identifier: String, content: Content?): PendingIntent {
        val intent = Intent(context, DailyReminderReceiver::class.java)
            .setAction("$ACTION.$identifier")
            .putExtra(EXTRA_ID, identifier)
            .putExtra(EXTRA_ICON, smallIcon)
            .putExtra(EXTRA_CHANNEL_NAME, channelName)
        if (content != null) intent.putExtra(EXTRA_TITLE, content.title).putExtra(EXTRA_BODY, content.body)
        return PendingIntent.getBroadcast(
            context, identifier.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL = "daily"
        internal const val ACTION = "com.tavoohoh.quiet.DAILY_REMINDER"
        internal const val EXTRA_ID = "id"
        internal const val EXTRA_TITLE = "title"
        internal const val EXTRA_BODY = "body"
        internal const val EXTRA_ICON = "icon"
        internal const val EXTRA_CHANNEL_NAME = "channelName"
        private const val WINDOW_MILLIS = 10 * 60 * 1000L

        internal fun ensureChannel(context: Context, name: String) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(NotificationChannel(CHANNEL, name, NotificationManager.IMPORTANCE_DEFAULT))
        }
        private const val ENABLED = "enabled"
        private const val OFFERED = "offered"
        private const val ASKED = "asked"
        private const val SCHEDULED = "scheduled"
    }
}

/** Posts a planned reminder (its text was written when it was scheduled). Tapping it opens the app. */
class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(DailyReminders.EXTRA_ID) ?: return
        val title = intent.getStringExtra(DailyReminders.EXTRA_TITLE) ?: return
        val body = intent.getStringExtra(DailyReminders.EXTRA_BODY) ?: return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        DailyReminders.ensureChannel(context, intent.getStringExtra(DailyReminders.EXTRA_CHANNEL_NAME) ?: title)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val open = launch?.let {
            PendingIntent.getActivity(context, 0, it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(context, DailyReminders.CHANNEL)
            .setSmallIcon(intent.getIntExtra(DailyReminders.EXTRA_ICON, android.R.drawable.ic_dialog_info))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .setGroup("daily")
            .build()
        runCatching { manager.notify(id.hashCode(), notification) }
    }
}
