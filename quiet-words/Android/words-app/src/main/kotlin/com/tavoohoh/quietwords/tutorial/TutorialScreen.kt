package com.tavoohoh.quietwords.tutorial

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.tavoohoh.quiet.ui.CapsuleButton
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quiet.ui.QuietType
import com.tavoohoh.quiet.ui.quietClickable
import com.tavoohoh.quiet.ui.rememberHaptics
import com.tavoohoh.quietwords.AppModel
import com.tavoohoh.quietwords.R
import com.tavoohoh.quietwords.core.Board
import com.tavoohoh.quietwords.core.Direction
import com.tavoohoh.quietwords.core.GameSession
import com.tavoohoh.quietwords.core.Level
import com.tavoohoh.quietwords.core.Placement
import com.tavoohoh.quietwords.core.Word
import com.tavoohoh.quietwords.core.WordGroup
import com.tavoohoh.quietwords.core.WordLanguage
import com.tavoohoh.quietwords.design.LanguageChoice
import com.tavoohoh.quietwords.design.play
import com.tavoohoh.quietwords.game.BoardView
import com.tavoohoh.quietwords.game.GameModel
import com.tavoohoh.quietwords.game.HiddenWordRow

private const val STEPS = 4

/**
 * How to play. Step 1 is the language of the words (both choices in view, the system's
 * preselected, never deduced). Then a hand-made board: find a word by dragging, one by
 * tapping first and last letter, and the hidden word. "Skip" is always there.
 */
@Composable
fun TutorialScreen(app: AppModel, onFinish: () -> Unit) {
    val colors = QuietTheme.colors
    var step by rememberSaveable { mutableIntStateOf(0) }
    var language by rememberSaveable { mutableStateOf(app.wordLanguage) }
    var model by remember { mutableStateOf<GameModel?>(null) }
    if (step > 0 && model == null) model = tutorialModel(language)

    fun finish() {
        if (step == 0) app.chooseLanguage(language)
        onFinish()
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            val column = if (maxWidth >= 600.dp) 680.dp else 560.dp
            Column(Modifier.widthIn(max = column).fillMaxSize().padding(horizontal = 24.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val label = stringResource(R.string.step_of, step + 1, STEPS)
                    Row(Modifier.clearAndSetSemantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (i in 0 until STEPS) {
                            val width by animateDpAsState(if (i == step) 18.dp else 7.dp, label = "step")
                            Box(Modifier.width(width).height(7.dp).background(if (i == step) colors.primary else colors.ghost, CircleShape))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(R.string.skip), style = QuietType.headline, color = colors.primary,
                        modifier = Modifier.quietClickable { finish() }.padding(8.dp).testTag("skip"),
                    )
                }
                val current = model
                if (step == 0 || current == null) {
                    LanguageStep(language, { language = it }) {
                        app.chooseLanguage(language)
                        model = tutorialModel(language).also { it.allowedWord = 0 }
                        step = 1
                    }
                } else {
                    PlayStep(current, step, language, min(330.dp, column - 48.dp), onAdvance = { step = it }, onFinish = ::finish)
                }
            }
        }
    }
}

@Composable
private fun LanguageStep(language: WordLanguage, onSelect: (WordLanguage) -> Unit, onContinue: () -> Unit) {
    val colors = QuietTheme.colors
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.which_words), style = QuietType.largeTitle, color = colors.ink, modifier = Modifier.padding(top = 36.dp))
            Text(stringResource(R.string.app_stays), style = QuietType.body, color = colors.muted)
            Spacer(Modifier.height(0.dp))
            LanguageChoice(WordLanguage.EN, "TIDE · CORAL · ABYSSAL", stringResource(R.string.themed_en), language, onSelect)
            LanguageChoice(WordLanguage.ES, "MAREA · CORAL · ÑANDÚ", stringResource(R.string.themed_es), language, onSelect)
        }
        CapsuleButton(stringResource(R.string.continue_), onContinue, Modifier.padding(top = 12.dp, bottom = 16.dp).testTag("tutorialContinue"))
    }
}

@Composable
private fun PlayStep(model: GameModel, step: Int, language: WordLanguage, boardSize: androidx.compose.ui.unit.Dp, onAdvance: (Int) -> Unit, onFinish: () -> Unit) {
    val colors = QuietTheme.colors
    val haptics = rememberHaptics()
    SideEffect { model.onFeedback = { haptics.play(it) } }
    val words = if (language == WordLanguage.EN) listOf("CORAL", "WAVES") else listOf("CORAL", "MEDUSA")
    val found = model.state.found.size
    LaunchedEffect(found) {
        if (found == 1 && step == 1) { onAdvance(2); model.allowedWord = 1 }
        if (found == 2 && step == 2) { onAdvance(3); model.allowedWord = null }
    }
    val title = when (step) {
        1 -> stringResource(R.string.find_word, words[0])
        2 -> stringResource(R.string.now_word, words[1])
        else -> stringResource(R.string.tutorial_hidden_title)
    }
    val message = when (step) {
        1 -> stringResource(R.string.tutorial_drag)
        2 -> stringResource(R.string.tutorial_tap)
        else -> stringResource(R.string.tutorial_hidden)
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = QuietType.title, color = colors.ink, modifier = Modifier.padding(top = 28.dp))
        Text(message, style = QuietType.body, color = colors.muted)
        Box(Modifier.fillMaxWidth().padding(top = 18.dp), contentAlignment = Alignment.Center) { BoardView(model, boardSize) }
        if (step == 3) HiddenWordRow(model.state, Modifier.padding(top = 8.dp))
        Spacer(Modifier.weight(1f))
        if (step == 3) {
            CapsuleButton(stringResource(R.string.start_playing), onFinish, Modifier.padding(bottom = 16.dp).testTag("startPlaying"))
        }
    }
}

/** A 7×7 board made by hand: CORAL across and a second word down (Waves / Medusa). Same as iOS. */
private fun tutorialModel(language: WordLanguage): GameModel {
    val rows: List<String>
    val second: Triple<String, Int, Int>
    if (language == WordLanguage.EN) {
        rows = listOf("SHELLTA", "WTDIVER", "ACORALE", "VKDLFIC", "EAEPINS", "SLPOOLL", "WAVELLT")
        second = Triple("Waves", 1, 0)
    } else {
        rows = listOf("OLASALT", "PERLASM", "ACORALE", "RDUNASD", "EUBOYAU", "NSARESS", "AAMEJAA")
        second = Triple("Medusa", 1, 6)
    }
    val letters = rows.flatMap { row -> row.map { it.toString() } }
    val coral = Placement(Word("Coral", language, WordGroup.COMMON), 2, 1, Direction.EAST)
    val down = Placement(Word(second.first, language, WordGroup.COMMON), second.second, second.third, Direction.SOUTH)
    val board = Board(7, language, letters, listOf(coral, down), if (language == WordLanguage.EN) "SWELL" else "MAREA", emptyList())
    return GameModel(GameSession(GameSession.Mode.Free, "ocean", Level.GENTLE, board), isTutorial = true)
}
