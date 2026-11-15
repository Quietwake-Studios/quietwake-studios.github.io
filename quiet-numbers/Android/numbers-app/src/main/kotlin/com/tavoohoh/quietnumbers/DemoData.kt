package com.tavoohoh.quietnumbers

import com.tavoohoh.quiet.core.DailyKey
import com.tavoohoh.quietnumbers.core.DailyChallenge
import com.tavoohoh.quietnumbers.core.GameSession
import com.tavoohoh.quietnumbers.core.MatchRules
import com.tavoohoh.quietnumbers.core.NumbersStore
import com.tavoohoh.quietnumbers.core.PlayerProgress
import java.io.File

/**
 * Debug `--ez demo true` (screenshots, like iOS `-demo`): a streak of 8 ending yesterday, a past
 * day filled later and one played without reaching the goal, today halfway, and a classic game
 * halfway.
 */
object DemoData {
    fun seed(directory: File) {
        val store = NumbersStore(directory)
        val today = AppClock.todayKey
        fun day(offset: Int) = DailyKey.dayKey(today, offset)!!
        for (offset in 1..8) store.finishDaily(day(-offset), 520 + offset * 15, true, 240 + offset * 9)
        store.finishDaily(day(-12), 610, true, 300)
        store.finishDaily(day(-14), 380, false, 410)
        for (offset in 17..21) store.finishDaily(day(-offset), 560, true, 280)
        // Today halfway (Continue on the Daily card).
        DailyChallenge.session(today).let { s ->
            repeat(5) { MatchRules.validPairs(s.board).firstOrNull()?.let { p -> s.match(p.first, p.second) } }
            store.saveGame(s, NumbersStore.day(today))
        }
        // A classic game halfway (12 pairs into stage 1).
        GameSession(seed = 2026uL).let { s ->
            repeat(12) { MatchRules.validPairs(s.board).firstOrNull()?.let { p -> s.match(p.first, p.second) } }
            store.saveGame(s, NumbersStore.CLASSIC)
        }
        store.store.saveProfile(
            PlayerProgress(
                gamesFinished = 31, gamesPlayed = 34, boardsCleared = 9, rowsCleared = 64, highestStage = 4,
                dailiesCompleted = 14, bestStreak = 8, bestScore = 2480, practicedDays = setOf(day(-12)),
            ),
        )
    }
}
