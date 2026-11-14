package com.tavoohoh.quietwords.design

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.text.format.DateFormat
import com.tavoohoh.quiet.core.DailyKey
import java.time.Duration
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Day keys (`yyyy-MM-dd`) for display, in the app's locale (iOS `DayKeyFormat`). */
object DayFormat {
    private fun pattern(skeleton: String, locale: Locale) = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

    /** "Friday, November 13": weekday and date, no year. */
    fun weekday(dayKey: String, locale: Locale): String =
        DailyKey.date(dayKey)?.format(pattern("EEEEMMMMd", locale))?.replaceFirstChar { it.titlecase(locale) } ?: dayKey

    /** "Nov 12". */
    fun short(dayKey: String, locale: Locale): String = DailyKey.date(dayKey)?.format(pattern("MMMd", locale)) ?: dayKey

    /** "November 12, 2026" (TalkBack). */
    fun long(dayKey: String, locale: Locale): String = DailyKey.date(dayKey)?.format(pattern("yMMMMd", locale)) ?: dayKey

    /** "November 2026". */
    fun month(month: YearMonth, locale: Locale): String =
        month.atDay(1).format(pattern("yMMMM", locale)).replaceFirstChar { it.titlecase(locale) }

    /** An hour in the device's style ("7 PM", "19"). */
    fun hour(hour: Int, locale: Locale): String = LocalTime.of(hour, 0).format(pattern("j", locale))

    /** "6 hr, 12 min" until the next challenge. */
    fun countdown(duration: Duration, locale: Locale): String {
        val minutes = maxOf(1, duration.toMinutes())
        val h = (minutes / 60).toInt()
        val m = (minutes % 60).toInt()
        val format = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT)
        val parts = buildList {
            if (h > 0) add(Measure(h, MeasureUnit.HOUR))
            if (m > 0 || h == 0) add(Measure(m, MeasureUnit.MINUTE))
        }
        return format.formatMeasures(*parts.toTypedArray())
    }
}
