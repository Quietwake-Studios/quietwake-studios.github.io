package com.tavoohoh.quietwords.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quiet.ui.QuietType
import com.tavoohoh.quietwords.LocalUiTesting
import com.tavoohoh.quietwords.R
import com.tavoohoh.quietwords.core.GameSession

/** UI tests (debug `uiTesting`): the hidden word's answer on the hidden word row. */
val HiddenAnswer = SemanticsPropertyKey<String>("HiddenAnswer")
var SemanticsPropertyReceiver.hiddenAnswer by HiddenAnswer

/**
 * The theme's words as written (accents, spaces); found ones struck through in muted.
 * Follows the font size: three columns, two from 130 %, one from 180 %.
 */
@Composable
fun WordList(session: GameSession, columns: Int = 3, modifier: Modifier = Modifier) {
    val colors = QuietTheme.colors
    val scale = LocalConfiguration.current.fontScale
    val count = when {
        scale >= 1.8f -> 1
        scale >= 1.3f -> minOf(columns, 2)
        else -> columns
    }
    val placements = session.board.placements
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (rowStart in placements.indices step count) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (index in rowStart until rowStart + count) {
                    Box(Modifier.weight(1f)) {
                        if (index < placements.size) {
                            val text = placements[index].word.text
                            val found = session.isFound(index)
                            val label = stringResource(if (found) R.string.word_found else R.string.word_to_find, text)
                            Text(
                                text, style = QuietType.subheadline, maxLines = 2,
                                color = if (found) colors.muted else colors.ink,
                                // Not by color alone: found words are struck through.
                                textDecoration = if (found) TextDecoration.LineThrough else null,
                                modifier = Modifier.clearAndSetSemantics { contentDescription = label },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "WORDS            3/14". */
@Composable
fun WordsHeader(session: GameSession, modifier: Modifier = Modifier) {
    val colors = QuietTheme.colors
    val found = session.found.size
    val total = session.board.placements.size
    val label = stringResource(R.string.found_of, found, total)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.words).uppercase(), color = colors.muted,
            style = QuietType.caption.copy(fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.weight(1f))
        Text(
            "$found/$total", color = colors.muted, style = QuietType.caption.copy(fontFeatureSettings = QuietType.TABULAR),
            modifier = Modifier.clearAndSetSemantics { contentDescription = label; testTag = "foundCount" },
        )
    }
}

enum class SlotState { HIDDEN, TYPING, WRONG, GUESSED, REVEALED }

/**
 * One box per letter of the hidden word: empty, typed, wrong, guessed (accent) or revealed
 * at the end (primary).
 */
@Composable
fun HiddenSlots(length: Int, letters: String, state: SlotState, size: Dp = 26.dp, modifier: Modifier = Modifier) {
    val colors = QuietTheme.colors
    val shown = letters.toList()
    val description = when (state) {
        SlotState.GUESSED, SlotState.REVEALED -> stringResource(R.string.hidden_a11y_shown, letters)
        else -> pluralStringResource(R.plurals.hidden_a11y, length, length)
    }
    val foreground = when (state) {
        SlotState.GUESSED -> colors.onAccent
        SlotState.REVEALED -> colors.onPrimary
        SlotState.WRONG -> colors.accent
        else -> colors.ink
    }
    Row(
        modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(size * 0.2f),
    ) {
        for (i in 0 until length) {
            val letter = shown.getOrNull(i)?.toString() ?: ""
            val filled = letter.isNotEmpty()
            val shape = RoundedCornerShape(size * 0.27f)
            val background = when (state) {
                SlotState.GUESSED -> colors.accent
                SlotState.REVEALED -> colors.primary
                SlotState.WRONG -> colors.accent.copy(alpha = 0.1f)
                else -> if (filled) colors.surface else colors.surface2
            }
            val border = when (state) {
                SlotState.WRONG -> colors.accent
                SlotState.TYPING -> if (filled || i == shown.size) colors.primary else Color.Transparent
                else -> Color.Transparent
            }
            Box(
                Modifier
                    .size(size, size * 1.18f)
                    .background(background, shape)
                    .border(2.dp, border, shape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    letter, color = foreground,
                    style = QuietType.body.copy(fontSize = (size.value * 0.6f).sp, fontWeight = FontWeight.Black, lineHeight = (size.value * 0.7f).sp),
                )
            }
        }
    }
}

/** The hidden word under the board: its slots, and its letters once guessed or revealed. */
@Composable
fun HiddenWordRow(session: GameSession, modifier: Modifier = Modifier, size: Dp = 26.dp, stacked: Boolean = false) {
    val colors = QuietTheme.colors
    val uiTesting = LocalUiTesting.current
    val length = session.board.hidden.length
    val caption = when (session.hiddenState) {
        GameSession.HiddenState.UNKNOWN -> pluralStringResource(R.plurals.hidden_caption, length, length)
        GameSession.HiddenState.GUESSED -> stringResource(R.string.hidden_guessed, session.scoring.hiddenGuessed)
        GameSession.HiddenState.REVEALED -> stringResource(R.string.hidden_revealed, session.scoring.hiddenRevealed)
    }
    val state = when (session.hiddenState) {
        GameSession.HiddenState.UNKNOWN -> SlotState.HIDDEN
        GameSession.HiddenState.GUESSED -> SlotState.GUESSED
        GameSession.HiddenState.REVEALED -> SlotState.REVEALED
    }
    val label = @Composable {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.hidden_word), color = colors.ink, style = QuietType.subheadline.copy(fontWeight = FontWeight.SemiBold))
            Text(caption, color = colors.muted, style = QuietType.caption)
        }
    }
    val slots = @Composable { HiddenSlots(length, session.hiddenShown, state, size) }
    // Side by side when it fits; the slots under the label for long words in a narrow column.
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val slotsWidth = size * length + size * 0.2f * (length - 1)
        val stack = stacked || maxWidth - 28.dp - slotsWidth < 150.dp
        val base = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(18.dp))
            .semantics(mergeDescendants = true) { if (uiTesting) hiddenAnswer = session.board.hidden }
            .testTag("hiddenWord")
            .padding(horizontal = 14.dp, vertical = 10.dp)
        if (stack) {
            Column(base, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                label()
                slots()
            }
        } else {
            Row(base, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).padding(end = 8.dp)) { label() }
                slots()
            }
        }
    }
}
