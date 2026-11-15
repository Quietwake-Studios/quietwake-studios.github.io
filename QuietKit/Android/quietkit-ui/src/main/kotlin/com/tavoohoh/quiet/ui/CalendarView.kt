package com.tavoohoh.quiet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields

/** How a day looks on the calendar. */
data class CalendarDay(
    /** Counts for the streak: filled with `primary`, joined to its completed neighbours. */
    val isCompleted: Boolean = false,
    /** Completed after its date (Words) or cleared later (Numbers): softly tinted. */
    val isLate: Boolean = false,
    /** Played to the end without completing it (Numbers): outlined. */
    val isPlayed: Boolean = false,
    /** An attempt left halfway: a dot. */
    val isInProgress: Boolean = false,
)

/**
 * Month grid of daily challenges, shared by the family (iOS `CalendarView` of each game): the
 * month's real days, completed days filled and joined by a band when consecutive (within a
 * week), days completed late softly tinted, played ones outlined, a dot on a day left halfway,
 * today ringed. Days in the replay window (the last 30) can be tapped; older ones only shown.
 */
@Composable
fun CalendarView(
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    todayKey: String,
    day: (String) -> CalendarDay,
    isPlayable: (String) -> Boolean,
    regular: Boolean,
    onSelect: (String) -> Unit,
) {
    val colors = QuietTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val isCurrentMonth = month == YearMonth.parse(todayKey.substring(0, 7))
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val weekdays = (0 until 7).map { firstDay.plus(it.toLong()) }
    val leading = Math.floorMod(month.atDay(1).dayOfWeek.value - firstDay.value, 7)
    val days = month.lengthOfMonth()
    fun key(day: Int) = "%04d-%02d-%02d".format(month.year, month.monthValue, day)
    fun isDone(d: Int) = d in 1..days && day(key(d)).isCompleted

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Arrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.quiet_previous_month), true, Modifier.testTag("previousMonth")) {
                onMonth(month.minusMonths(1))
            }
            Text(
                DayFormat.month(month, locale), style = QuietType.headline, color = colors.ink,
                modifier = Modifier.weight(1f).testTag("monthTitle"), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Arrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.quiet_next_month), !isCurrentMonth, Modifier.testTag("nextMonth")) {
                onMonth(month.plusMonths(1))
            }
        }
        Row {
            for (d in weekdays) {
                Text(
                    d.getDisplayName(TextStyle.NARROW_STANDALONE, locale), style = QuietType.footnote.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.muted, modifier = Modifier.weight(1f).padding(bottom = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        val cells = leading + days
        for (week in 0 until (cells + 6) / 7) {
            Row {
                for (column in 0 until 7) {
                    val n = week * 7 + column - leading + 1
                    Box(Modifier.weight(1f)) {
                        if (n in 1..days) {
                            val k = key(n)
                            val mark = day(k)
                            DayCell(
                                day = n, dayKey = k, isToday = k == todayKey, isCompleted = mark.isCompleted, isLate = mark.isLate,
                                isPlayed = mark.isPlayed, isInProgress = mark.isInProgress, isPlayable = isPlayable(k),
                                joinsPrevious = column != 0 && isDone(n) && isDone(n - 1),
                                joinsNext = column != 6 && isDone(n) && isDone(n + 1),
                                regular = regular, onSelect = onSelect,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Arrow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = QuietTheme.colors
    Box(modifier.size(44.dp).quietClickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = if (enabled) colors.primary else colors.ghost, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun DayCell(
    day: Int, dayKey: String, isToday: Boolean, isCompleted: Boolean, isLate: Boolean, isPlayed: Boolean, isInProgress: Boolean,
    isPlayable: Boolean, joinsPrevious: Boolean, joinsNext: Boolean, regular: Boolean, onSelect: (String) -> Unit,
) {
    val colors = QuietTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val height = if (regular) 56.dp else 42.dp
    val circle = height - 6.dp
    val foreground = when {
        isCompleted -> colors.onPrimary
        isToday -> colors.accent
        isLate -> colors.primary
        isPlayable -> colors.ink
        else -> colors.muted.copy(alpha = 0.6f)
    }
    val label = DayFormat.long(dayKey, locale)
    val status = when {
        isInProgress -> stringResource(R.string.quiet_day_in_progress)
        isCompleted -> stringResource(R.string.quiet_day_completed)
        isLate -> stringResource(R.string.quiet_day_won_later)
        isPlayed -> stringResource(R.string.quiet_day_played)
        else -> ""
    }
    val band = colors.primary.copy(alpha = 0.2f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .then(
                if (joinsPrevious || joinsNext) {
                    Modifier.drawBehindBand(band, circle, joinsPrevious, joinsNext)
                } else {
                    Modifier
                },
            )
            .quietClickable(enabled = isPlayable, role = Role.Button) { onSelect(dayKey) }
            .semantics {
                contentDescription = label
                if (status.isNotEmpty()) stateDescription = status
            }
            .testTag("day-$dayKey"),
        contentAlignment = Alignment.Center,
    ) {
        if (isToday) {
            Box(Modifier.size(circle + 8.dp).border(2.dp, colors.accent, CircleShape))
        }
        Box(
            Modifier.size(circle).background(
                when {
                    isCompleted -> colors.primary
                    isLate -> colors.primary.copy(alpha = 0.22f)
                    else -> Color.Transparent
                },
                CircleShape,
            ).then(if (isPlayed && !isCompleted && !isLate) Modifier.border(1.5.dp, colors.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$day",
                style = (if (regular) QuietType.title3 else QuietType.body).copy(
                    fontWeight = if (isCompleted || isLate || isToday) FontWeight.Bold else FontWeight.Normal,
                    fontFeatureSettings = QuietType.TABULAR,
                ),
                color = foreground,
            )
            if (isInProgress) {
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = if (regular) 4.dp else 2.dp).size(5.dp)
                        .background(if (isCompleted) colors.onPrimary else colors.accent, CircleShape),
                )
            }
        }
    }
}

/** 20 % `primary` behind the circles, from this day's center to the neighbour it joins. */
private fun Modifier.drawBehindBand(color: Color, height: androidx.compose.ui.unit.Dp, previous: Boolean, next: Boolean): Modifier =
    drawBehind {
        val h = height.toPx()
        val mid = size.width / 2
        val start = if (previous) 0f else mid
        val end = if (next) size.width else mid
        drawRect(color, Offset(start, (size.height - h) / 2), Size(end - start, h))
    }
