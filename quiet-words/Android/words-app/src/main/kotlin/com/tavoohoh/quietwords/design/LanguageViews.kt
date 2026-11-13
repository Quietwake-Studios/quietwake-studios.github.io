package com.tavoohoh.quietwords.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quiet.ui.QuietType
import com.tavoohoh.quiet.ui.quietClickable
import com.tavoohoh.quiet.ui.quietSurface
import com.tavoohoh.quietwords.R
import com.tavoohoh.quietwords.core.WordLanguage

/**
 * The compact language picker (EN · ES) where play starts: the daily card and New Game.
 * One tap switches and it's remembered; nobody is asked before each game. A check marks a
 * language whose daily is done today.
 */
@Composable
fun LanguagePill(
    language: WordLanguage,
    onChange: (WordLanguage) -> Unit,
    modifier: Modifier = Modifier,
    done: Set<WordLanguage> = emptySet(),
    onPrimary: Boolean = false,
    onTick: () -> Unit = {},
) {
    val colors = QuietTheme.colors
    val track = if (onPrimary) colors.onPrimary.copy(alpha = 0.18f) else colors.surface2
    val fill = if (onPrimary) colors.onPrimary else colors.surface
    val selectedText = if (onPrimary) colors.primary else colors.ink
    val idleText = if (onPrimary) colors.onPrimary else colors.muted
    Row(modifier.background(track, CircleShape).padding(3.dp)) {
        for (option in WordLanguage.entries) {
            val selected = option == language
            val label = stringResource(R.string.words_in, option.displayName())
            Row(
                Modifier
                    .defaultMinSize(minWidth = 44.dp, minHeight = 30.dp)
                    .background(if (selected) fill else Color.Transparent, CircleShape)
                    .quietClickable {
                        if (!selected) {
                            onChange(option)
                            onTick()
                        }
                    }
                    .padding(horizontal = 12.dp)
                    .semantics {
                        contentDescription = label
                        this.selected = selected
                    }
                    .testTag("words-${option.rawValue}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    option.code, color = if (selected) selectedText else idleText,
                    style = QuietType.footnote.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                )
                if (option in done) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(12.dp), tint = if (selected) selectedText else idleText)
                }
            }
        }
    }
}

/** One of the two languages, big, with a sample of its words: the tutorial's first step. */
@Composable
fun LanguageChoice(
    language: WordLanguage,
    sample: String,
    note: String,
    selected: WordLanguage,
    onSelect: (WordLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QuietTheme.colors
    val isSelected = language == selected
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .quietSurface(shape, if (isSelected) colors.primary.copy(alpha = 0.08f).compositeOver(colors.surface) else null)
            .border(2.5.dp, if (isSelected) colors.primary else Color.Transparent, shape)
            .quietClickable(role = Role.RadioButton) { onSelect(language) }
            .semantics { this.selected = isSelected }
            .testTag("language-${language.rawValue}")
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(language.wordsTitle(), style = QuietType.title3, color = colors.ink)
            Spacer(Modifier.weight(1f))
            Icon(
                if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle, null,
                tint = if (isSelected) colors.primary else colors.ghost, modifier = Modifier.size(26.dp),
            )
        }
        Text(sample, style = QuietType.headline.copy(letterSpacing = 1.5.sp), color = colors.primary)
        Text(note, style = QuietType.footnote, color = colors.muted)
    }
}

private fun Color.compositeOver(background: Color): Color {
    val a = alpha
    return Color(red * a + background.red * (1 - a), green * a + background.green * (1 - a), blue * a + background.blue * (1 - a))
}
