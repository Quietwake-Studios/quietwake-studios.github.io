package com.tavoohoh.quietwords.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tavoohoh.quiet.core.Streaks
import com.tavoohoh.quiet.ui.DailyReminders
import com.tavoohoh.quietwords.AppClock
import com.tavoohoh.quietwords.AppModel
import com.tavoohoh.quietwords.R
import com.tavoohoh.quietwords.core.ReminderText
import com.tavoohoh.quietwords.core.WordsStore
import java.text.NumberFormat

/**
 * Quiet Words's texts for the kit's daily reminders. `state` says whether today is done (either
 * language) and the current streak. UI tests get their own preferences and never schedule.
 */
fun wordsReminders(context: Context, uiTesting: Boolean = false, state: () -> Pair<Boolean, Int>): DailyReminders {
    val app = context.applicationContext
    return DailyReminders(app, R.drawable.ic_notification, if (uiTesting) "dailyReminders-uitesting" else "dailyReminders").apply {
        channelName = app.getString(R.string.reminder_channel)
        clock = AppClock::now
        this.state = state
        texts = { item ->
            val points = { n: Int -> NumberFormat.getIntegerInstance().format(n) }
            when (val text = ReminderText.of(item)) {
                ReminderText.Waiting -> DailyReminders.Content(app.getString(R.string.daily_challenge), app.getString(R.string.reminder_waiting))
                is ReminderText.Streak -> DailyReminders.Content(
                    app.getString(R.string.daily_challenge),
                    app.resources.getQuantityString(R.plurals.reminder_streak, text.days, text.days),
                )
                is ReminderText.OneFriend -> DailyReminders.Content(
                    app.getString(R.string.daily_challenge), app.getString(R.string.reminder_one_friend, text.name, points(text.score)),
                )
                is ReminderText.Friends -> DailyReminders.Content(
                    app.getString(R.string.daily_challenge), app.getString(R.string.reminder_friends, text.count, text.leader, points(text.score)),
                )
                is ReminderText.StreakAtRisk -> DailyReminders.Content(
                    app.getString(R.string.your_streak),
                    app.resources.getQuantityString(R.plurals.reminder_at_risk, text.days, text.days),
                )
            }
        }
    }
}

/** Alarms don't survive a reboot or a clock change: plan again from the saved dailies. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = WordsStore(AppModel.storeDirectory(context))
        wordsReminders(context) {
            val today = AppClock.todayKey
            val done = store.completedDays
            (today in done) to Streaks.current(done, today)
        }.reschedule()
    }
}
