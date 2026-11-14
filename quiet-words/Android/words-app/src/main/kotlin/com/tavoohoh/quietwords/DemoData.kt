package com.tavoohoh.quietwords

import com.tavoohoh.quiet.core.DailyKey
import com.tavoohoh.quietwords.core.Album
import com.tavoohoh.quietwords.core.DailyChallenge
import com.tavoohoh.quietwords.core.DailyID
import com.tavoohoh.quietwords.core.FreeGame
import com.tavoohoh.quietwords.core.Level
import com.tavoohoh.quietwords.core.PlayerProgress
import com.tavoohoh.quietwords.core.Profile
import com.tavoohoh.quietwords.core.Slot
import com.tavoohoh.quietwords.core.WordLanguage
import com.tavoohoh.quietwords.core.WordsStore
import java.io.File

/**
 * Debug `--ez demo true` (screenshots, like iOS `-demo`): a 5-day streak with today done in
 * the other language, a best streak of 7, a past day won late, dailies left halfway (today in
 * the words language and a past day) and a free game in progress.
 */
object DemoData {
    fun seed(directory: File, language: WordLanguage) {
        val store = WordsStore(directory)
        val today = AppClock.todayKey
        fun day(offset: Int) = DailyKey.dayKey(today, offset)!!
        val other = if (language == WordLanguage.EN) WordLanguage.ES else WordLanguage.EN
        fun win(offset: Int, lang: WordLanguage, score: Int, seconds: Int) {
            val id = DailyID(day(offset), lang)
            store.startAttempt(id)
            store.recordWin(id, score, seconds)
        }
        win(0, other, 742, 251)
        for (offset in 1..4) win(-offset, if (offset % 2 == 0) WordLanguage.ES else WordLanguage.EN, 600 + offset * 13, 200 + offset * 7)
        for (offset in 14..20) win(-offset, WordLanguage.EN, 580, 260)
        win(-9, WordLanguage.ES, 512, 310)
        // Today in the words language, halfway (Continue on the card).
        DailyChallenge.session(today, language)?.let { s ->
            for (i in 0 until 4) s.board.placements[i].cells(s.board.side).let { s.select(it.first(), it.last()) }
            repeat(97) { s.tick() }
            val id = DailyID(today, language)
            store.startAttempt(id)
            store.saveGame(s, Slot.Daily(id))
        }
        // A past day halfway (a dot in the calendar).
        DailyChallenge.session(day(-6), language)?.let { s ->
            s.board.placements[0].cells(s.board.side).let { s.select(it.first(), it.last()) }
            val id = DailyID(day(-6), language)
            store.startAttempt(id)
            store.saveGame(s, Slot.Daily(id))
        }
        FreeGame.session("ocean", Level.MEDIUM, language, "demo-free")?.let { s ->
            for (i in 0 until 6) s.board.placements[i].cells(s.board.side).let { s.select(it.first(), it.last()) }
            repeat(164) { s.tick() }
            store.saveGame(s, Slot.Free)
        }
        val progress = PlayerProgress(gamesWon = 23, gamesPlayed = 26, wordsFound = 412, dailiesCompleted = 13, bestStreak = 7, lateDays = setOf(day(-9)))
        store.saveProfile(Profile(progress, demoAlbum(language)))
    }

    private fun demoAlbum(language: WordLanguage): Album {
        var album = Album()
        val library = com.tavoohoh.quietwords.core.ThemeLibrary.shared
        for ((id, count) in listOf("ocean" to 87, "botany" to 64, "space" to 120, "coffee_tea" to 41)) {
            val words = library.theme(id)?.words(language)?.all ?: continue
            album = album.add(words.take(count).map { it.letters }, id, language).first
        }
        return album
    }
}
