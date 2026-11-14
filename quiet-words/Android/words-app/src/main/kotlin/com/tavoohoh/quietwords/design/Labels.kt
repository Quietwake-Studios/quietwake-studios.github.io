package com.tavoohoh.quietwords.design

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.tavoohoh.quietwords.R
import com.tavoohoh.quietwords.core.Level
import com.tavoohoh.quietwords.core.WordLanguage
import com.tavoohoh.quietwords.core.WordTheme

/** "Gentle" / "Suave". */
@Composable
@ReadOnlyComposable
fun Level.title(): String = stringResource(
    when (this) {
        Level.GENTLE -> R.string.level_gentle
        Level.MEDIUM -> R.string.level_medium
        Level.HARD -> R.string.level_hard
    },
)

/** "11×11 · about 17 words · 4 directions". */
@Composable
@ReadOnlyComposable
fun Level.detail(): String = stringResource(R.string.level_detail, side, side, typicalWords, directions.size)

/** "English words" / "Palabras en español": always in their own language. */
@Composable
@ReadOnlyComposable
fun WordLanguage.wordsTitle(): String =
    stringResource(if (this == WordLanguage.EN) R.string.words_title_en else R.string.words_title_es)

/** The language's name in the app's language (TalkBack). */
@Composable
@ReadOnlyComposable
fun WordLanguage.displayName(): String =
    stringResource(if (this == WordLanguage.EN) R.string.language_english else R.string.language_spanish)

val WordLanguage.code: String get() = rawValue.uppercase()

/** The app's own language (only en and es exist; anything else falls back to English). */
@Composable
@ReadOnlyComposable
fun appLanguage(): WordLanguage =
    if (LocalConfiguration.current.locales[0].language == "es") WordLanguage.ES else WordLanguage.EN

/** The theme's name in the app's language (the lists keep their own). */
@Composable
@ReadOnlyComposable
fun WordTheme.localizedName(): String = name(appLanguage())

/** The system's language as a suggestion for the words (preselected, never decided for the player). */
fun systemWordLanguage(): WordLanguage =
    if (Resources.getSystem().configuration.locales[0].language == "es") WordLanguage.ES else WordLanguage.EN
