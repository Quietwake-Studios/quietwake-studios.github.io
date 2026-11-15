package com.tavoohoh.quietnumbers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tavoohoh.quiet.core.Streaks
import com.tavoohoh.quietnumbers.core.NumbersStore

/** Alarms don't survive a reboot or a clock change: plan again from the saved dailies. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = NumbersStore(AppModel.storeDirectory(context))
        numbersReminders(context) {
            val today = AppClock.todayKey
            val done = store.completedDays
            (today in done) to Streaks.current(done, today)
        }.reschedule()
    }
}
