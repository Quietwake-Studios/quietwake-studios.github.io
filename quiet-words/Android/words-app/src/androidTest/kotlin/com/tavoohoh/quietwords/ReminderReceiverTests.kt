package com.tavoohoh.quietwords

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tavoohoh.quiet.ui.DailyReminderReceiver
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A planned reminder, when its alarm fires, posts the text written when it was scheduled. */
@RunWith(AndroidJUnit4::class)
class ReminderReceiverTests {
    @Test fun theAlarmPostsTheNotification() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val id = "daily.2026-11-13.reminder"
        val intent = Intent(context, DailyReminderReceiver::class.java)
            .putExtra("id", id).putExtra("title", "Daily Challenge").putExtra("body", "Today's challenge is waiting.")
            .putExtra("icon", R.drawable.ic_notification).putExtra("channelName", "Daily challenge")
        // The grant can take a moment to reach the notification service.
        val manager = context.getSystemService(NotificationManager::class.java)
        var posted: android.service.notification.StatusBarNotification? = null
        val deadline = System.currentTimeMillis() + 5_000
        while (posted == null && System.currentTimeMillis() < deadline) {
            DailyReminderReceiver().onReceive(context, intent)
            Thread.sleep(250)
            posted = manager.activeNotifications.firstOrNull { it.id == id.hashCode() }
        }
        assertTrue("not posted", posted != null)
        manager.cancel(id.hashCode())
    }
}
