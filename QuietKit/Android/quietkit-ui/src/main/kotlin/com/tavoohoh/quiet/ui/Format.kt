package com.tavoohoh.quiet.ui

/** "2:14": minutes and seconds of a game's clock (iOS `GameClock` / `.time(pattern: .minuteSecond)`). */
fun clock(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
