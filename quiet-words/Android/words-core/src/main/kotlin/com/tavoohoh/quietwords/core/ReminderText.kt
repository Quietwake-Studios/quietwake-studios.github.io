package com.tavoohoh.quietwords.core

import com.tavoohoh.quiet.core.DailyReminderPlan
import com.tavoohoh.quiet.core.FriendsToday

/**
 * Which text a daily notification gets (port of iOS `DailyReminders.content`); the app turns
 * it into its strings. Friends come with Play Games (a later phase): until then `friends` is null.
 */
sealed interface ReminderText {
    data object Waiting : ReminderText
    data class Streak(val days: Int) : ReminderText
    data class OneFriend(val name: String, val score: Int) : ReminderText
    data class Friends(val count: Int, val leader: String, val score: Int) : ReminderText
    data class StreakAtRisk(val days: Int) : ReminderText

    companion object {
        fun of(item: DailyReminderPlan.Item, friends: FriendsToday? = null): ReminderText = when (item.kind) {
            DailyReminderPlan.Kind.STREAK_AT_RISK -> StreakAtRisk(item.streak)
            DailyReminderPlan.Kind.REMINDER -> {
                val name = friends?.leaderName
                val score = friends?.leaderScore
                when {
                    friends != null && friends.count > 0 && name != null && score != null ->
                        if (friends.count == 1) OneFriend(name, score) else Friends(friends.count, name, score)
                    item.streak > 0 -> Streak(item.streak)
                    else -> Waiting
                }
            }
        }
    }
}
